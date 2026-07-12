package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ─────────────────────────────────────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────────────────────────────────────
enum class PatchLogic(val label: String, val desc: String) {
    SHARK_PATCH_ONLY(
        "Shark Patch Only",
        "NXT_SHARK537 Method: Rebuild tabel MP4 + inject fake samples. Tanpa re-encode."
    ),
    ENCODE_THEN_SHARK(
        "Encode + Shark Patch",
        "Re-encode dulu, lalu terapkan NXT_SHARK537 Method. Kualitas optimal."
    ),
    ENCODE_ONLY(
        "Encode Only",
        "Hanya re-encode tanpa patch. Untuk perbaikan video rusak."
    ),
    KYTHERA_60FPS(
        "Kythera 60fps Stamp",
        "Sematkan metadata stamp eksklusif Kythera 60fps."
    )
}

enum class EncodeQuality(val label: String, val desc: String, val cmdParams: String) {
    CPU_CRF20(
        "Jernih & Stabil (CRF 20)",
        "CPU libx264. Ukuran file aman, ketajaman dipertahankan.",
        "-c:v libx264 -preset fast -crf 20 -bf 0"
    ),
    CPU_HIGH(
        "Sultan High Bitrate (25M)",
        "CPU libx264. Kualitas maksimal, file lebih besar.",
        "-c:v libx264 -preset fast -b:v 25M -maxrate 25M -bufsize 50M -bf 0"
    ),
    GPU_FAST(
        "Super Cepat (GPU)",
        "Akselerasi GPU Hardware (15M). Proses kilat.",
        "-c:v h264_mediacodec -b:v 15M -bf 0"
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// NXT_SHARK537 METHOD — Port dari popup.js
// ─────────────────────────────────────────────────────────────────────────────
object SharkPatcher {

    private val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)
    private const val FAKE_SAMPLE_SIZE = 8
    private const val VIDEO_TIMESCALE = 90000L
    private const val VIDEO_DURATION = 2269500L
    private const val VIDEO_EDIT_MEDIA_TIME = 0L
    private const val VIDEO_SAMPLE_DELTA = 1500L

    private val CONTAINER_BOXES = setOf("moov","trak","mdia","minf","stbl","edts","dinf","udta","meta","ilst")

    data class Box(
        val type: String,
        val offset: Int,
        val size: Int,
        val headerSize: Int,
        val data: ByteArray,
        val children: MutableList<Box> = mutableListOf(),
        var prefixStart: Int = 0,
        var prefixEnd: Int = 0
    ) {
        val contentStart get() = offset + headerSize
        val end get() = offset + size
    }

    private fun getBoxType(data: ByteArray, offset: Int): String {
        return String(byteArrayOf(data[offset], data[offset+1], data[offset+2], data[offset+3]))
    }

    private fun readUint32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
               ((data[offset+1].toLong() and 0xFF) shl 16) or
               ((data[offset+2].toLong() and 0xFF) shl 8) or
               (data[offset+3].toLong() and 0xFF)
    }

    private fun writeUint32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset]   = ((value shr 24) and 0xFF).toByte()
        buf[offset+1] = ((value shr 16) and 0xFF).toByte()
        buf[offset+2] = ((value shr 8)  and 0xFF).toByte()
        buf[offset+3] = (value and 0xFF).toByte()
    }

    private fun parseBoxes(data: ByteArray, start: Int = 0, end: Int = data.size): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset + 8 <= end) {
            val smallSize = readUint32(data, offset)
            val type = getBoxType(data, offset + 4)
            val size = when {
                smallSize == 1L -> {
                    val high = readUint32(data, offset + 8)
                    val low  = readUint32(data, offset + 12)
                    high * 4294967296L + low
                }
                smallSize == 0L -> (end - offset).toLong()
                else -> smallSize
            }
            val headerSize = if (smallSize == 1L) 16 else 8
            val box = Box(
                type       = type,
                offset     = offset,
                size       = size.toInt(),
                headerSize = headerSize,
                data       = data,
                prefixStart = offset + headerSize,
                prefixEnd   = offset + headerSize
            )
            if (CONTAINER_BOXES.contains(type)) {
                val childStart = if (type == "meta") box.contentStart + 4 else box.contentStart
                box.prefixEnd = childStart
                box.children.addAll(parseBoxes(data, childStart, box.end))
            }
            boxes.add(box)
            offset += size.toInt()
        }
        return boxes
    }

    private fun findChild(box: Box, type: String): Box? =
        box.children.find { it.type == type }

    private fun findDescendant(box: Box, path: List<String>): Box? {
        var current: Box? = box
        for (t in path) { current = current?.let { findChild(it, t) } ?: return null }
        return current
    }

    private fun findTopLevel(boxes: List<Box>, type: String): Box? =
        boxes.find { it.type == type }

    private fun handlerTypeForTrak(trak: Box): String? {
        val hdlr = findDescendant(trak, listOf("mdia", "hdlr")) ?: return null
        return if (hdlr.offset + 20 <= hdlr.end) getBoxType(hdlr.data, hdlr.offset + 16) else null
    }

    private fun parseStsz(stsz: Box): List<Long> {
        val sampleSize = readUint32(stsz.data, stsz.offset + 12)
        val count      = readUint32(stsz.data, stsz.offset + 16).toInt()
        if (sampleSize != 0L) return List(count) { sampleSize }
        val tableStart = stsz.offset + 20
        return List(count) { i -> readUint32(stsz.data, tableStart + i * 4) }
    }

    private fun parseStco(stco: Box): List<Long> {
        val count      = readUint32(stco.data, stco.offset + 12).toInt()
        val tableStart = stco.offset + 16
        return List(count) { i -> readUint32(stco.data, tableStart + i * 4) }
    }

    private fun parseStsc(stsc: Box): List<Triple<Long,Long,Long>> {
        val count      = readUint32(stsc.data, stsc.offset + 12).toInt()
        val tableStart = stsc.offset + 16
        return List(count) { i ->
            val base = tableStart + i * 12
            Triple(
                readUint32(stsc.data, base),
                readUint32(stsc.data, base + 4),
                readUint32(stsc.data, base + 8)
            )
        }
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val buf  = ByteArray(size)
        writeUint32(buf, 0, size.toLong())
        type.forEachIndexed { i, c -> buf[4 + i] = c.code.toByte() }
        System.arraycopy(payload, 0, buf, 8, payload.size)
        return buf
    }

    private fun boxBytes(box: Box): ByteArray =
        box.data.copyOfRange(box.offset, box.end)

    private fun concatBytes(parts: List<ByteArray>): ByteArray {
        val total  = parts.sumOf { it.size }
        val result = ByteArray(total)
        var pos    = 0
        parts.forEach { System.arraycopy(it, 0, result, pos, it.size); pos += it.size }
        return result
    }

    // ── Build mdhd ───────────────────────────────────────────────────────────
    private fun buildMdhd(mdhd: Box): ByteArray {
        val payload = mdhd.data.copyOfRange(mdhd.contentStart, mdhd.end).copyOf()
        val version = payload[0].toInt()
        if (version == 1) {
            writeUint32(payload, 24, (VIDEO_TIMESCALE shr 32) and 0xFFFFFFFFL)
            writeUint32(payload, 28, VIDEO_TIMESCALE and 0xFFFFFFFFL)
            writeUint32(payload, 32, (VIDEO_DURATION shr 32) and 0xFFFFFFFFL)
            writeUint32(payload, 36, VIDEO_DURATION and 0xFFFFFFFFL)
        } else {
            writeUint32(payload, 12, VIDEO_TIMESCALE)
            writeUint32(payload, 16, VIDEO_DURATION)
        }
        return makeBox("mdhd", payload)
    }

    // ── Build elst ───────────────────────────────────────────────────────────
    private fun buildElst(elst: Box): ByteArray {
        val payload = elst.data.copyOfRange(elst.contentStart, elst.end).copyOf()
        val version = payload[0].toInt()
        val count   = readUint32(payload, 4).toInt()
        if (count >= 1) {
            if (version == 1) {
                writeUint32(payload, 8,  (VIDEO_DURATION shr 32) and 0xFFFFFFFFL)
                writeUint32(payload, 12, VIDEO_DURATION and 0xFFFFFFFFL)
                writeUint32(payload, 16, (VIDEO_EDIT_MEDIA_TIME shr 32) and 0xFFFFFFFFL)
                writeUint32(payload, 20, VIDEO_EDIT_MEDIA_TIME and 0xFFFFFFFFL)
            } else {
                writeUint32(payload, 8,  VIDEO_DURATION)
                writeUint32(payload, 12, VIDEO_EDIT_MEDIA_TIME)
            }
        }
        return makeBox("elst", payload)
    }

    // ── Build stts ───────────────────────────────────────────────────────────
    private fun buildStts(realSampleCount: Int, fakeSampleCount: Int): ByteArray {
        val payload = ByteArray(4 + 4 + 2 * 8)
        writeUint32(payload, 4, 2L)
        writeUint32(payload, 8,  realSampleCount.toLong())
        writeUint32(payload, 12, VIDEO_SAMPLE_DELTA)
        writeUint32(payload, 16, fakeSampleCount.toLong())
        writeUint32(payload, 20, VIDEO_SAMPLE_DELTA)
        return makeBox("stts", payload)
    }

    // ── Build stsz ───────────────────────────────────────────────────────────
    private fun buildStsz(originalSizes: List<Long>, fakeSampleCount: Int): ByteArray {
        val total   = originalSizes.size + fakeSampleCount
        val payload = ByteArray(4 + 4 + total * 4)
        writeUint32(payload, 4, total.toLong())
        var offset  = 8
        originalSizes.forEach { sz -> writeUint32(payload, offset, sz); offset += 4 }
        repeat(fakeSampleCount) { writeUint32(payload, offset, FAKE_SAMPLE_SIZE.toLong()); offset += 4 }
        return makeBox("stsz", payload)
    }

    // ── Build stsc ───────────────────────────────────────────────────────────
    private fun buildStsc(originalRows: List<Triple<Long,Long,Long>>, originalChunkCount: Int): ByteArray {
        val rows    = originalRows.toMutableList()
        val lastRow = rows.lastOrNull()
        if (lastRow == null || lastRow.second != 1L) {
            rows.add(Triple((originalChunkCount + 1).toLong(), 1L, 1L))
        }
        val payload = ByteArray(4 + 4 + rows.size * 12)
        writeUint32(payload, 4, rows.size.toLong())
        var offset = 8
        rows.forEach { (first, spc, sdi) ->
            writeUint32(payload, offset,     first)
            writeUint32(payload, offset + 4, spc)
            writeUint32(payload, offset + 8, sdi)
            offset += 12
        }
        return makeBox("stsc", payload)
    }

    // ── Build stco ───────────────────────────────────────────────────────────
    private fun buildStco(
        originalOffsets: List<Long>,
        delta: Long,
        fakeOffset: Long? = null,
        fakeSampleCount: Int = 0
    ): ByteArray {
        val count   = originalOffsets.size + (if (fakeOffset != null) fakeSampleCount else 0)
        val payload = ByteArray(4 + 4 + count * 4)
        writeUint32(payload, 4, count.toLong())
        var tableOffset = 8
        originalOffsets.forEach { off ->
            writeUint32(payload, tableOffset, off + delta)
            tableOffset += 4
        }
        if (fakeOffset != null) {
            repeat(fakeSampleCount) {
                writeUint32(payload, tableOffset, fakeOffset)
                tableOffset += 4
            }
        }
        return makeBox("stco", payload)
    }

    // ── Rebuild box recursively ───────────────────────────────────────────────
    private fun rebuildBox(box: Box, replacements: Map<Box, ByteArray>): ByteArray {
        replacements[box]?.let { return it }
        if (box.children.isEmpty()) return boxBytes(box)
        val parts = mutableListOf<ByteArray>()
        parts.add(box.data.copyOfRange(box.prefixStart, box.prefixEnd))
        box.children.forEach { parts.add(rebuildBox(it, replacements)) }
        return makeBox(box.type, concatBytes(parts))
    }

    // ── Collect all stco boxes ────────────────────────────────────────────────
    private fun collectTrackStcoBoxes(moov: Box): List<Box> {
        val result = mutableListOf<Box>()
        moov.children.filter { it.type == "trak" }.forEach { trak ->
            val stbl = findDescendant(trak, listOf("mdia", "minf", "stbl")) ?: return@forEach
            findChild(stbl, "stco")?.let { result.add(it) }
        }
        return result
    }

    private fun buildStcoReplacements(
        stcoBoxes: List<Box>,
        videoStco: Box,
        delta: Long,
        fakeOffset: Long,
        fakeSampleCount: Int
    ): Map<Box, ByteArray> {
        return stcoBoxes.associate { stco ->
            stco to buildStco(
                parseStco(stco), delta,
                if (stco === videoStco) fakeOffset else null,
                fakeSampleCount
            )
        }
    }

    // ── MAIN PATCH FUNCTION ───────────────────────────────────────────────────
    fun patch(inputFile: File, outputFile: File) {
        val data     = inputFile.readBytes()
        val topLevel = parseBoxes(data)

        val ftyp = findTopLevel(topLevel, "ftyp")
            ?: throw Exception("Box 'ftyp' tidak ditemukan. File bukan MP4 valid.")
        val moov = findTopLevel(topLevel, "moov")
            ?: throw Exception("Box 'moov' tidak ditemukan.")
        val mdat = findTopLevel(topLevel, "mdat")
            ?: throw Exception("Box 'mdat' tidak ditemukan.")

        val videoTrak = moov.children.find { it.type == "trak" && handlerTypeForTrak(it) == "vide" }
            ?: throw Exception("Track video tidak ditemukan.")

        val stbl = findDescendant(videoTrak, listOf("mdia", "minf", "stbl"))
            ?: throw Exception("Box stbl tidak ditemukan.")
        val mdhd = findDescendant(videoTrak, listOf("mdia", "mdhd"))
            ?: throw Exception("Box mdhd tidak ditemukan.")
        val elst = findDescendant(videoTrak, listOf("edts", "elst"))
            ?: throw Exception("Box elst tidak ditemukan.")
        val stts = findChild(stbl, "stts") ?: throw Exception("Box stts tidak ditemukan.")
        val stsc = findChild(stbl, "stsc") ?: throw Exception("Box stsc tidak ditemukan.")
        val stsz = findChild(stbl, "stsz") ?: throw Exception("Box stsz tidak ditemukan.")
        val stco = findChild(stbl, "stco") ?: throw Exception("Box stco tidak ditemukan.")

        val originalSizes    = parseStsz(stsz)
        val realSampleCount  = originalSizes.size
        val fakeSampleCount  = realSampleCount * 9

        val originalStscRows     = parseStsc(stsc)
        val originalChunkOffsets = parseStco(stco)
        val stcoBoxes            = collectTrackStcoBoxes(moov)

        val preservedTopLevel = topLevel
            .filter { it.type !in listOf("ftyp", "moov", "mdat") }
            .map { boxBytes(it) }

        val fixedReplacements = mutableMapOf<Box, ByteArray>(
            mdhd to buildMdhd(mdhd),
            elst to buildElst(elst),
            stts to buildStts(realSampleCount, fakeSampleCount),
            stsc to buildStsc(originalStscRows, originalChunkOffsets.size),
            stsz to buildStsz(originalSizes, fakeSampleCount)
        )

        // Pass 1 — placeholder buat hitung delta
        val placeholderReplacements = HashMap(fixedReplacements)
        buildStcoReplacements(stcoBoxes, stco, 0L, 0L, fakeSampleCount).forEach { (k,v) ->
            placeholderReplacements[k] = v
        }
        val moovPlaceholder  = rebuildBox(moov, placeholderReplacements)
        val preservedBytes   = concatBytes(preservedTopLevel)
        val oldMdatPayload   = data.copyOfRange(mdat.contentStart, mdat.end)
        val newMdatPayloadStart = ftyp.size + moovPlaceholder.size + preservedBytes.size + 8L
        var delta      = newMdatPayloadStart - mdat.contentStart
        var fakeOffset = newMdatPayloadStart + oldMdatPayload.size

        // Pass 2 — hitung ulang setelah tahu delta real
        var finalReplacements = HashMap(fixedReplacements)
        buildStcoReplacements(stcoBoxes, stco, delta, fakeOffset, fakeSampleCount).forEach { (k,v) ->
            finalReplacements[k] = v
        }
        var moovNew = rebuildBox(moov, finalReplacements)
        val recalcStart = ftyp.size + moovNew.size + preservedBytes.size + 8L
        delta      = recalcStart - mdat.contentStart
        fakeOffset = recalcStart + oldMdatPayload.size

        // Pass 3 — final
        finalReplacements = HashMap(fixedReplacements)
        buildStcoReplacements(stcoBoxes, stco, delta, fakeOffset, fakeSampleCount).forEach { (k,v) ->
            finalReplacements[k] = v
        }
        moovNew = rebuildBox(moov, finalReplacements)

        val mdatPayloadNew = concatBytes(listOf(oldMdatPayload, FAKE_SAMPLE_BYTES))
        val mdatNew        = makeBox("mdat", mdatPayloadNew)
        val output         = concatBytes(listOf(boxBytes(ftyp), moovNew, preservedBytes, mdatNew))

        outputFile.writeBytes(output)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PatchScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var inputUriString  by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing    by rememberSaveable { mutableStateOf(false) }
    var statusText      by rememberSaveable { mutableStateOf("") }
    var errorLog        by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess       by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri   by remember { mutableStateOf<Uri?>(null) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var selectedLogic   by remember { mutableStateOf(PatchLogic.SHARK_PATCH_ONLY) }
    var selectedQuality by remember { mutableStateOf(EncodeQuality.CPU_CRF20) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString  = it.toString()
            errorLog        = null
            isSuccess       = false
            savedVideoUri   = null
            progressPercent = 0f
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, it)
                videoDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                statusText = "✅ Siap: ${it.lastPathSegment}"
            } catch (e: Exception) {
                videoDurationMs = 0L
                statusText = "✅ Siap: ${it.lastPathSegment}"
            }
        }
    }

    fun saveToGallery(file: File, fileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { dest ->
            context.contentResolver.openOutputStream(dest)?.use { out ->
                FileInputStream(file).use { it.copyTo(out) }
            }
            file.delete()
        }
        return uri
    }

    fun executePatch() {
        if (inputUri == null) return
        scope.launch {
            isProcessing    = true
            isSuccess       = false
            errorLog        = null
            savedVideoUri   = null
            progressPercent = 0f

            val fileName = "Kythera_Patched_${System.currentTimeMillis()}.mp4"
            val tmpPath  = File(context.getExternalFilesDir(null), fileName)
            val safUrl   = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
            val logic    = selectedLogic
            val quality  = selectedQuality

            FFmpegKitConfig.enableStatisticsCallback { stats ->
                if (videoDurationMs > 0) {
                    val pct = (stats.time.toFloat() / videoDurationMs).coerceIn(0f, 1f)
                    progressPercent = pct
                    statusText = "⚙️ Encoding: ${(pct * 100).toInt()}%"
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    when (logic) {

                        PatchLogic.SHARK_PATCH_ONLY -> {
                            // Step 1: remux + faststart
                            statusText = "⚙️ Remux MP4 (+faststart)..."
                            val tmpRemux = File(context.cacheDir, "remux_${System.currentTimeMillis()}.mp4")
                            val cmd = "-hide_banner -i \"$safUrl\" -c copy -movflags +faststart \"${tmpRemux.absolutePath}\""
                            val session = FFmpegKit.execute(cmd)
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)

                            // Step 2: Shark patch
                            progressPercent = 0.85f
                            statusText = "⚙️ Menerapkan NXT_SHARK537 Method..."
                            SharkPatcher.patch(tmpRemux, tmpPath)
                            tmpRemux.delete()
                        }

                        PatchLogic.ENCODE_THEN_SHARK -> {
                            // Step 1: Encode
                            statusText = "⚙️ Encoding video..."
                            val tmpEncoded = File(context.cacheDir, "enc_${System.currentTimeMillis()}.mp4")
                            val cmd = "-hide_banner -i \"$safUrl\" ${quality.cmdParams} -c:a aac -b:a 192k -movflags +faststart \"${tmpEncoded.absolutePath}\""
                            val session = FFmpegKit.execute(cmd)
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)

                            // Step 2: Shark patch
                            progressPercent = 0.9f
                            statusText = "⚙️ Menerapkan NXT_SHARK537 Method..."
                            SharkPatcher.patch(tmpEncoded, tmpPath)
                            tmpEncoded.delete()
                        }

                        PatchLogic.ENCODE_ONLY -> {
                            statusText = "⚙️ Encoding video..."
                            val cmd = "-hide_banner -i \"$safUrl\" ${quality.cmdParams} -c:a aac -b:a 192k -movflags +faststart \"${tmpPath.absolutePath}\""
                            val session = FFmpegKit.execute(cmd)
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)
                        }

                        PatchLogic.KYTHERA_60FPS -> {
                            statusText = "⚙️ Remux + 60fps stamp..."
                            val tmpRemux = File(context.cacheDir, "remux_${System.currentTimeMillis()}.mp4")
                            val cmd = "-hide_banner -i \"$safUrl\" -c copy -movflags +faststart \"${tmpRemux.absolutePath}\""
                            val session = FFmpegKit.execute(cmd)
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)

                            progressPercent = 0.9f
                            statusText = "⚙️ Menyematkan Kythera Stamp..."
                            val bytes = tmpRemux.readBytes()
                            val stamp = "KYTHERA_60FPS".toByteArray()
                            tmpPath.writeBytes(bytes + stamp)
                            tmpRemux.delete()
                        }
                    }

                    FFmpegKitConfig.enableStatisticsCallback(null)
                    progressPercent = 0.98f
                    statusText = "⚙️ Menyimpan ke galeri..."
                    savedVideoUri = saveToGallery(tmpPath, fileName)
                    isSuccess  = true
                    statusText = "✅ Berhasil! Tersimpan di Galeri/Kythera"

                } catch (e: Exception) {
                    FFmpegKitConfig.enableStatisticsCallback(null)
                    errorLog   = e.message ?: "Unknown error"
                    statusText = "❌ Gagal!"
                    tmpPath.delete()
                }
            }
            isProcessing = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Patch Video", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
        Text("NXT_SHARK537 Method — MP4 Sample Table Rebuild", color = KColor.Orange, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Input
        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle = "MP4 — Patch, Encode, atau Keduanya",
                icon = Icons.Rounded.CloudUpload,
                accentColor = KColor.Orange
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = if (isSuccess) KColor.Orange else KColor.Text2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        // Metode
        GlassCard {
            Text("Metode Patch", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PatchLogic.entries.forEach { logic ->
                val isActive = selectedLogic == logic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) KColor.Orange.copy(0.12f) else Color.Transparent)
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) KColor.Orange.copy(0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedLogic = logic }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(logic.label,
                            color = if (isActive) KColor.Orange else KColor.Text,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp)
                        Text(logic.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    RadioButton(
                        selected = isActive,
                        onClick  = { selectedLogic = logic },
                        colors   = RadioButtonDefaults.colors(selectedColor = KColor.Orange)
                    )
                }
            }
        }

        // Kualitas encode (muncul kalau pilih mode encode)
        AnimatedVisibility(
            visible = selectedLogic == PatchLogic.ENCODE_THEN_SHARK || selectedLogic == PatchLogic.ENCODE_ONLY,
            enter   = expandVertically(tween(300)),
            exit    = shrinkVertically(tween(300))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Kualitas Encoding", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    EncodeQuality.entries.forEach { quality ->
                        val isActive = selectedQuality == quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) KColor.Orange.copy(0.12f) else Color.Transparent)
                                .border(
                                    width = if (isActive) 1.dp else 0.dp,
                                    color = if (isActive) KColor.Orange.copy(0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedQuality = quality }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(quality.label,
                                    color = if (isActive) KColor.Orange else KColor.Text,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp)
                                Text(quality.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            RadioButton(
                                selected = isActive,
                                onClick  = { selectedQuality = quality },
                                colors   = RadioButtonDefaults.colors(selectedColor = KColor.Orange)
                            )
                        }
                    }
                }
            }
        }

        // Error
        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("⚠️ ERROR", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // Progress
        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KColor.Surface)
                    .padding(16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⏳ $statusText", color = KColor.Orange, fontSize = 13.sp)
                        if (progressPercent > 0f)
                            Text("${(progressPercent * 100).toInt()}%", color = KColor.Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (progressPercent > 0f && progressPercent < 1f) {
                        LinearProgressIndicator(
                            progress = { progressPercent },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color    = KColor.Orange
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = KColor.Orange)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (isSuccess && savedVideoUri != null) {
            KPrimaryButton(
                label      = "Reset",
                icon       = Icons.Rounded.Refresh,
                startColor = KColor.Orange,
                endColor   = Color(0xFFD97706),
                onClick    = {
                    inputUriString  = null
                    isSuccess       = false
                    statusText      = ""
                    savedVideoUri   = null
                    progressPercent = 0f
                }
            )
        } else {
            KPrimaryButton(
                label      = "Patch & Download",
                icon       = Icons.Rounded.AutoFixHigh,
                enabled    = inputUri != null && !isProcessing,
                startColor = KColor.Orange,
                endColor   = Color(0xFFD97706),
                onClick    = ::executePatch
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
