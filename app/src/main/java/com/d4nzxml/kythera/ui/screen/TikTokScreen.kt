package com.d4nzxml.kythera.ui.screen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════
// MODE
// ═══════════════════════════════════════════════════════════════════════════
enum class PipelineMode(val label: String, val icon: String, val desc: String) {
    PATCH_ONLY("Patch Only", "🩹", "Shark HD patch langsung ke video asli"),
    ENCODE_PATCH("Encode + Patch", "🎬", "Re-encode dulu lalu Shark patch"),
    KYTHERA_60("Kythera 60fps", "⚡", "Z-Payload + encoder tag")
}

// ═══════════════════════════════════════════════════════════════════════════
// NOTIFICATION
// ═══════════════════════════════════════════════════════════════════════════
private const val NOTIF_CHANNEL_ID = "kythera_pipeline"
private const val NOTIF_ID = 7777

private fun ensureNotifChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            NOTIF_CHANNEL_ID, "Kythera Pipeline",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Encode & Patch progress" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

private fun postNotif(context: Context, title: String, text: String, progress: Int = -1) {
    ensureNotifChannel(context)
    val b = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(progress in 0..99)
        .setSilent(true)
    if (progress in 0..100) b.setProgress(100, progress, false)
    try { NotificationManagerCompat.from(context).notify(NOTIF_ID, b.build()) } catch (_: SecurityException) {}
}

private fun dismissNotif(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIF_ID)

// ═══════════════════════════════════════════════════════════════════════════
// PATCHER (NXT_SHARK537 METHOD TRANSLATED)
// ═══════════════════════════════════════════════════════════════════════════
private object SharkPatcher {

    private val FAKE_SAMPLE_SIZE = 8L
    private val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)
    private val VIDEO_TIMESCALE = 90000L
    private val VIDEO_DURATION = 2269500L
    private val VIDEO_EDIT_MEDIA_TIME = 0L
    private val VIDEO_SAMPLE_DELTA = 1500L
    private val CONTAINER_BOXES = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta", "meta", "ilst")

    private class Mp4Box(
        val type: String, val offset: Int, val size: Long, val headerSize: Int,
        val contentStart: Int, val end: Int, val path: String, val data: ByteArray,
        var children: List<Mp4Box> = emptyList(), var prefixStart: Int = 0, var prefixEnd: Int = 0
    )

    private fun getUint32(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF) shl 24) or
               ((buf[offset + 1].toLong() and 0xFF) shl 16) or
               ((buf[offset + 2].toLong() and 0xFF) shl 8) or
               (buf[offset + 3].toLong() and 0xFF)
    }

    private fun setUint32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun getBoxType(buf: ByteArray, offset: Int) = String(buf, offset, 4)

    private fun setBoxType(buf: ByteArray, offset: Int, type: String) {
        val bytes = type.toByteArray()
        for (i in 0..3) buf[offset + i] = bytes[i]
    }

    private fun readBox(data: ByteArray, offset: Int, end: Int, parentPath: String = ""): Mp4Box {
        if (offset + 8 > end) throw Exception("MP4 invalido: caixa incompleta.")
        val smallSize = getUint32(data, offset)
        val type = getBoxType(data, offset + 4)
        var size = smallSize
        var headerSize = 8

        if (smallSize == 1L) {
            if (offset + 16 > end) throw Exception("MP4 invalido: caixa $type incompleta.")
            val high = getUint32(data, offset + 8)
            val low = getUint32(data, offset + 12)
            size = (high shl 32) or low
            headerSize = 16
        } else if (smallSize == 0L) {
            size = (end - offset).toLong()
        }
        if (size < headerSize || offset + size > end) throw Exception("MP4 invalido: tamanho incorreto na caixa $type.")

        return Mp4Box(type, offset, size, headerSize, offset + headerSize, offset + size.toInt(),
            if (parentPath.isNotEmpty()) "$parentPath/$type" else type, data, prefixStart = offset + headerSize, prefixEnd = offset + headerSize)
    }

    private fun childStartForBox(box: Mp4Box) = if (box.type == "meta") box.contentStart + 4 else box.contentStart

    private fun parseBoxes(data: ByteArray, start: Int = 0, end: Int = data.size, parentPath: String = ""): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var offset = start
        while (offset + 8 <= end) {
            val box = readBox(data, offset, end, parentPath)
            if (CONTAINER_BOXES.contains(box.type)) {
                val childStart = childStartForBox(box)
                if (childStart > box.end) throw Exception("MP4 invalido: container ${box.type} curto demais.")
                box.prefixStart = box.contentStart
                box.prefixEnd = childStart
                box.children = parseBoxes(data, childStart, box.end, box.path)
            }
            boxes.add(box)
            offset = box.end
        }
        return boxes
    }

    private fun findChild(box: Mp4Box, type: String) = box.children.find { it.type == type }
    
    private fun findDescendant(box: Mp4Box, path: List<String>): Mp4Box? {
        var current: Mp4Box? = box
        for (type in path) {
            current = current?.let { findChild(it, type) } ?: return null
        }
        return current
    }

    private fun handlerTypeForTrak(trak: Mp4Box): String? {
        val hdlr = findDescendant(trak, listOf("mdia", "hdlr")) ?: return null
        if (hdlr.offset + 20 > hdlr.end) return null
        return getBoxType(hdlr.data, hdlr.offset + 16)
    }

    private fun parseStsz(stsz: Mp4Box): List<Long> {
        val sampleSize = getUint32(stsz.data, stsz.offset + 12)
        val count = getUint32(stsz.data, stsz.offset + 16).toInt()
        if (sampleSize != 0L) return List(count) { sampleSize }
        val tableStart = stsz.offset + 20
        val sizes = mutableListOf<Long>()
        for (i in 0 until count) sizes.add(getUint32(stsz.data, tableStart + i * 4))
        return sizes
    }

    private fun parseStco(stco: Mp4Box): List<Long> {
        val count = getUint32(stco.data, stco.offset + 12).toInt()
        val tableStart = stco.offset + 16
        val offsets = mutableListOf<Long>()
        for (i in 0 until count) offsets.add(getUint32(stco.data, tableStart + i * 4))
        return offsets
    }

    private fun parseStsc(stsc: Mp4Box): List<LongArray> {
        val count = getUint32(stsc.data, stsc.offset + 12).toInt()
        val tableStart = stsc.offset + 16
        val rows = mutableListOf<LongArray>()
        for (i in 0 until count) {
            val offset = tableStart + i * 12
            rows.add(longArrayOf(getUint32(stsc.data, offset), getUint32(stsc.data, offset + 4), getUint32(stsc.data, offset + 8)))
        }
        return rows
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val box = ByteArray(size)
        setUint32(box, 0, size.toLong())
        setBoxType(box, 4, type)
        System.arraycopy(payload, 0, box, 8, payload.size)
        return box
    }

    private fun concatBytes(parts: List<ByteArray>): ByteArray {
        var total = 0
        for (p in parts) total += p.size
        val output = ByteArray(total)
        var offset = 0
        for (p in parts) {
            System.arraycopy(p, 0, output, offset, p.size)
            offset += p.size
        }
        return output
    }

    private fun boxBytes(box: Mp4Box) = box.data.copyOfRange(box.offset, box.end)
    private fun boxPayload(box: Mp4Box) = box.data.copyOfRange(box.contentStart, box.end)

    private fun buildMdhd(box: Mp4Box): ByteArray {
        val payload = boxPayload(box)
        setUint32(payload, 12, VIDEO_TIMESCALE)
        setUint32(payload, 16, VIDEO_DURATION)
        return makeBox("mdhd", payload)
    }

    private fun buildElst(box: Mp4Box): ByteArray {
        val payload = boxPayload(box)
        setUint32(payload, 12, VIDEO_EDIT_MEDIA_TIME)
        return makeBox("elst", payload)
    }

    private fun buildStts(realSampleCount: Long, fakeSampleCount: Long): ByteArray {
        val payload = ByteArray(4 + 4 + 8 + 8)
        setUint32(payload, 4, 2L)
        setUint32(payload, 8, realSampleCount)
        setUint32(payload, 12, VIDEO_SAMPLE_DELTA)
        setUint32(payload, 16, fakeSampleCount)
        setUint32(payload, 20, VIDEO_SAMPLE_DELTA)
        return makeBox("stts", payload)
    }

    private fun buildStsz(originalSizes: List<Long>, fakeSampleCount: Long): ByteArray {
        val totalSamples = originalSizes.size + fakeSampleCount.toInt()
        val payload = ByteArray(4 + 4 + 4 + totalSamples * 4)
        setUint32(payload, 8, totalSamples.toLong())
        var offset = 12
        for (s in originalSizes) { setUint32(payload, offset, s); offset += 4 }
        for (i in 0 until fakeSampleCount.toInt()) { setUint32(payload, offset, FAKE_SAMPLE_SIZE); offset += 4 }
        return makeBox("stsz", payload)
    }

    private fun buildStsc(originalRows: List<LongArray>, originalChunkCount: Long): ByteArray {
        val rows = originalRows.map { it.copyOf() }.toMutableList()
        if (rows.isEmpty() || rows.last()[1] != 1L) rows.add(longArrayOf(originalChunkCount + 1, 1L, 1L))
        val payload = ByteArray(4 + 4 + rows.size * 12)
        setUint32(payload, 4, rows.size.toLong())
        var offset = 8
        for (row in rows) {
            setUint32(payload, offset, row[0]); setUint32(payload, offset + 4, row[1]); setUint32(payload, offset + 8, row[2])
            offset += 12
        }
        return makeBox("stsc", payload)
    }

    private fun buildStco(originalOffsets: List<Long>, delta: Long, fakeOffset: Long?, fakeSampleCount: Long): ByteArray {
        val count = originalOffsets.size + (if (fakeOffset == null) 0 else fakeSampleCount.toInt())
        val payload = ByteArray(4 + 4 + count * 4)
        setUint32(payload, 4, count.toLong())
        var tableOffset = 8
        for (off in originalOffsets) { setUint32(payload, tableOffset, off + delta); tableOffset += 4 }
        if (fakeOffset != null) {
            for (i in 0 until fakeSampleCount.toInt()) { setUint32(payload, tableOffset, fakeOffset); tableOffset += 4 }
        }
        return makeBox("stco", payload)
    }

    private fun rebuildBox(box: Mp4Box, replacements: Map<Mp4Box, ByteArray>): ByteArray {
        if (replacements.containsKey(box)) return replacements[box]!!
        if (box.children.isEmpty()) return boxBytes(box)
        val parts = mutableListOf<ByteArray>()
        parts.add(box.data.copyOfRange(box.prefixStart, box.prefixEnd))
        for (child in box.children) parts.add(rebuildBox(child, replacements))
        return makeBox(box.type, concatBytes(parts))
    }

    // ── INTI SHARK SAMPLE TABLE METHOD ──────────────────────────────────────
    private fun patchSharkSampleTableMethod(data: ByteArray): ByteArray {
        val topLevel = parseBoxes(data)
        val ftyp = topLevel.find { it.type == "ftyp" } ?: throw Exception("MP4 ftyp box not found")
        val moov = topLevel.find { it.type == "moov" } ?: throw Exception("MP4 moov box not found")
        val mdat = topLevel.find { it.type == "mdat" } ?: throw Exception("MP4 mdat box not found")

        val videoTrak = moov.children.find { it.type == "trak" && handlerTypeForTrak(it) == "vide" } ?: throw Exception("Video track not found")
        val stbl = findDescendant(videoTrak, listOf("mdia", "minf", "stbl")) ?: throw Exception("stbl missing")
        val mdhd = findDescendant(videoTrak, listOf("mdia", "mdhd")) ?: throw Exception("mdhd missing")
        val elst = findDescendant(videoTrak, listOf("edts", "elst")) ?: throw Exception("elst missing")
        val stts = findChild(stbl, "stts") ?: throw Exception("stts missing")
        val stsc = findChild(stbl, "stsc") ?: throw Exception("stsc missing")
        val stsz = findChild(stbl, "stsz") ?: throw Exception("stsz missing")
        val stco = findChild(stbl, "stco") ?: throw Exception("stco missing")

        val originalSizes = parseStsz(stsz)
        val realSampleCount = originalSizes.size.toLong()
        val fakeSampleCount = realSampleCount * 9L

        val originalStscRows = parseStsc(stsc)
        val originalChunkOffsets = parseStco(stco)
        val stcoBoxes = mutableListOf<Mp4Box>()
        moov.children.filter { it.type == "trak" }.forEach { t ->
            findDescendant(t, listOf("mdia", "minf", "stbl"))?.let { s ->
                if (findChild(s, "co64") != null) throw Exception("co64 not supported yet")
                findChild(s, "stco")?.let { stcoBoxes.add(it) }
            }
        }

        val preservedTopLevel = topLevel.filter { it.type !in listOf("ftyp", "moov", "mdat") }.map { boxBytes(it) }
        val fixedReplacements = mapOf(
            mdhd to buildMdhd(mdhd),
            elst to buildElst(elst),
            stts to buildStts(realSampleCount, fakeSampleCount),
            stsc to buildStsc(originalStscRows, originalChunkOffsets.size.toLong()),
            stsz to buildStsz(originalSizes, fakeSampleCount)
        )

        // Pass 1: Kalkulasi Moov size & delta
        val placeholderReplacements = HashMap(fixedReplacements)
        stcoBoxes.forEach { b -> placeholderReplacements[b] = buildStco(parseStco(b), 0L, if (b == stco) 0L else null, fakeSampleCount) }
        val moovPlaceholder = rebuildBox(moov, placeholderReplacements)

        val preservedBytes = concatBytes(preservedTopLevel)
        val oldMdatPayload = boxPayload(mdat)
        val newMdatPayloadStart = ftyp.size + moovPlaceholder.size + preservedBytes.size + 8
        var delta = (newMdatPayloadStart - mdat.contentStart).toLong()
        var fakeOffset = (newMdatPayloadStart + oldMdatPayload.size).toLong()

        // Pass 2: Rebuild stco dengan delta pergeseran
        var finalReplacements = HashMap(fixedReplacements)
        stcoBoxes.forEach { b -> finalReplacements[b] = buildStco(parseStco(b), delta, if (b == stco) fakeOffset else null, fakeSampleCount) }
        var moovNew = rebuildBox(moov, finalReplacements)

        // Pass 3: Koreksi offset mdat setelah rebuild
        val recalculatedMdatPayloadStart = ftyp.size + moovNew.size + preservedBytes.size + 8
        delta = (recalculatedMdatPayloadStart - mdat.contentStart).toLong()
        fakeOffset = (recalculatedMdatPayloadStart + oldMdatPayload.size).toLong()

        finalReplacements = HashMap(fixedReplacements)
        stcoBoxes.forEach { b -> finalReplacements[b] = buildStco(parseStco(b), delta, if (b == stco) fakeOffset else null, fakeSampleCount) }
        moovNew = rebuildBox(moov, finalReplacements)

        val mdatPayloadNew = concatBytes(listOf(oldMdatPayload, FAKE_SAMPLE_BYTES))
        val mdatNew = makeBox("mdat", mdatPayloadNew)

        return concatBytes(listOf(boxBytes(ftyp), moovNew, preservedBytes, mdatNew))
    }

    // ── Encoder string → Lavf59.16.100 (Masih dipertahankan) ─────────────
    private fun patchEncoder(buf: ByteArray) {
        val lavf = "Lavf".toByteArray()
        val target = "Lavf59.16.100".toByteArray()
        var i = 0
        while (i <= buf.size - 16) {
            if (buf[i] == lavf[0] && buf[i+1] == lavf[1] && buf[i+2] == lavf[2] && buf[i+3] == lavf[3] && buf[i+4] in 0x30..0x39) {
                var end = i + 4
                while (end < buf.size && buf[end] >= 0x20 && buf[end] < 0x7F) end++
                val ol = end - i
                for (j in 0 until ol) buf[i+j] = if (j < target.size) target[j] else 0x00
                break
            }
            i++
        }
    }

    // ── Z-Payload (Kythera 60fps only) ───────────────────────────────────
    private fun zPayload(buf: ByteArray) {
        var mdatOff = -1
        var pos = 0
        while (pos + 8 <= buf.size) {
            val sz = getUint32(buf, pos).toInt()
            if (sz < 8) break
            if (getBoxType(buf, pos) == "mdat") { mdatOff = pos; break }
            pos += sz
        }
        if (mdatOff == -1) return
        val zt = mdatOff + 10
        for (i in 0 until 128) if (zt + i < buf.size) buf[zt + i] = 0x5A
    }

    // ── PUBLIC ────────────────────────────────────────────────────────────
    fun patchShark(input: ByteArray): ByteArray {
        val newBuf = patchSharkSampleTableMethod(input)
        patchEncoder(newBuf)
        return newBuf
    }

    fun patchKythera60(input: ByteArray): ByteArray {
        val buf = input.copyOf()
        zPayload(buf)
        patchEncoder(buf)
        return buf
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIPELINE
// ═══════════════════════════════════════════════════════════════════════════
private suspend fun runPipeline(
    context:    Context,
    sourceUri:  Uri,
    mode:       PipelineMode,
    crf:        Int,
    preset:     String,
    onProgress: (String, Int) -> Unit
): Uri? = withContext(Dispatchers.IO) {

    val ts         = System.currentTimeMillis()
    val cacheDir   = context.cacheDir
    val inputFile  = File(cacheDir, "ky_in_$ts.mp4")
    val encFile    = File(cacheDir, "ky_enc_$ts.mp4")
    val outputFile = File(cacheDir, "ky_out_$ts.mp4")

    // 🔥 Trik narik durasi video biar progress barnya akurat (Bukan Dummy)
    var durationMs = 0L
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, sourceUri)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
    } catch (e: Exception) {
        durationMs = 0L
    }

    try {
        onProgress("📂 Menyalin video...", 5)
        context.contentResolver.openInputStream(sourceUri)?.use { ins ->
            inputFile.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext null

        when (mode) {
            PipelineMode.PATCH_ONLY -> {
                onProgress("🩹 Membaca video...", 20)
                val raw = inputFile.readBytes()
                onProgress("🩹 Rebuilding Shark MP4 Table...", 60)
                outputFile.writeBytes(SharkPatcher.patchShark(raw))
            }

            PipelineMode.ENCODE_PATCH -> {
                onProgress("🎬 Menyiapkan encoder...", 5)
                
                FFmpegKitConfig.enableStatisticsCallback { stats ->
                    // 🔥 Progress Bar Real-time
                    if (durationMs > 0) {
                        val timeMs = stats.time.toFloat()
                        // Hitung rasio waktu render FFmpeg dibanding durasi total video
                        val ratio = (timeMs / durationMs).coerceIn(0f, 1f)
                        // Alokasikan persentase 5% sampai 85% untuk proses encoding
                        val p = (5 + (ratio * 80)).toInt() 
                        onProgress("🎬 Encoding $p%...", p)
                    } else {
                        // Kalau durasinya gagal ke-load, kasih statis di tengah jalan
                        onProgress("🎬 Encoding...", 50)
                    }
                }

                val cmd = "-y -i \"${inputFile.absolutePath}\" " +
                          "-vf format=yuv420p " +
                          "-c:v libx264 -preset $preset -crf $crf " +
                          "-bf 0 -movflags +faststart " +
                          "-c:a aac -b:a 128k -shortest " +
                          "-metadata copyright=\"By Kythera\" " +
                          "-metadata artist=\"D4nzxml\" " +
                          "\"${encFile.absolutePath}\""

                val session = FFmpegKit.execute(cmd)
                FFmpegKitConfig.enableStatisticsCallback(null)

                if (!ReturnCode.isSuccess(session.returnCode)) {
                    onProgress("❌ Encode gagal", -1)
                    return@withContext null
                }

                onProgress("🩹 Rebuilding Shark MP4 Table...", 85)
                outputFile.writeBytes(SharkPatcher.patchShark(encFile.readBytes()))
                onProgress("✅ Menyelesaikan...", 95)
            }

            PipelineMode.KYTHERA_60 -> {
                onProgress("⚡ Membaca video...", 20)
                val raw = inputFile.readBytes()
                onProgress("⚡ Menyematkan Z-Payload & encoder tag...", 60)
                outputFile.writeBytes(SharkPatcher.patchKythera60(raw))
            }
        }

        onProgress("✅ Selesai!", 100)
        Uri.fromFile(outputFile)

    } catch (e: Exception) {
        onProgress("❌ ${e.message}", -1)
        null
    } finally {
        inputFile.delete()
        encFile.delete()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ANIMASI AI SCAN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AiScanAnimation(statusMsg: String, progress: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "scanY"
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(90.dp).scale(pulse).clip(CircleShape).background(Color(0x337C4DFF)))
            Box(
                modifier = Modifier.size(70.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF7C4DFF), Color(0xFF3D1A78)))),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 28.sp, color = Color.White) }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A3E))
        ) {
            if (progress in 0..100) {
                Box(modifier = Modifier.fillMaxWidth(progress / 100f).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)))))
            }
            Box(modifier = Modifier.fillMaxWidth(scanY).fillMaxHeight().background(Color(0x88B388FF)))
        }

        Text(statusMsg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(Color(0xFF7C4DFF).copy(alpha = if (i == 0) dotAlpha else 1f - dotAlpha * 0.3f)))
            }
        }

        if (progress in 0..100) Text("$progress%", color = Color(0xFFB388FF), fontSize = 12.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelineSheet(onConfirm: (mode: PipelineMode, crf: Int, preset: String) -> Unit, onDismiss: () -> Unit) {
    var selectedMode by remember { mutableStateOf(PipelineMode.ENCODE_PATCH) }
    var selectedCrf by remember { mutableStateOf(18) }
    var selectedPreset by remember { mutableStateOf("fast") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A2E), contentColor = Color.White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⚙️ Kythera Pipeline", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF7C4DFF))
            Text("Mode Proses", fontSize = 13.sp, color = Color(0xFFAAAAAA))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PipelineMode.entries.forEach { mode ->
                    val active = selectedMode == mode
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E), RoundedCornerShape(12.dp))
                            .clickable { selectedMode = mode }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(mode.icon, fontSize = 20.sp)
                        Column {
                            Text(mode.label, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Color(0xFFCCCCCC))
                            Text(mode.desc, fontSize = 11.sp, color = if (active) Color(0xFFDDCCFF) else Color(0xFF777777))
                        }
                    }
                }
            }

            if (selectedMode == PipelineMode.ENCODE_PATCH) {
                Text("Kualitas (CRF)", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(18 to "Maksimal", 20 to "Seimbang").forEach { (crf, lbl) ->
                        val active = selectedCrf == crf
                        Box(
                            modifier = Modifier.weight(1f).background(if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E), RoundedCornerShape(10.dp))
                                .clickable { selectedCrf = crf }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CRF $crf", fontWeight = FontWeight.Bold, color = if (active) Color.White else Color(0xFFCCCCCC))
                                Text(lbl, fontSize = 11.sp, color = if (active) Color(0xFFDDCCFF) else Color(0xFF888888))
                            }
                        }
                    }
                }
                Text("Preset Encode", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ultrafast" to "⚡ Ultrafast", "fast" to "🚀 Fast", "medium" to "🎯 Medium").forEach { (preset, label) ->
                        val active = selectedPreset == preset
                        Box(
                            modifier = Modifier.weight(1f).background(if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E), RoundedCornerShape(10.dp))
                                .clickable { selectedPreset = preset }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else Color(0xFFCCCCCC)) }
                    }
                }
            }

            Button(
                onClick = { onConfirm(selectedMode, selectedCrf, selectedPreset) },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)), shape = RoundedCornerShape(12.dp)
            ) { Text("🚀 Proses & Upload ke TikTok", modifier = Modifier.padding(vertical = 4.dp)) }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Batalkan", color = Color(0xFF888888)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var showSheet           by remember { mutableStateOf(false) }
    var pendingUri          by remember { mutableStateOf<Uri?>(null) }
    var statusMsg           by remember { mutableStateOf("") }
    var progressVal         by remember { mutableStateOf(0) }
    var isProcessing        by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { pendingUri = uri; showSheet = true } else { fileChooserCallback?.onReceiveValue(null); fileChooserCallback = null }
    }

    if (showSheet) {
        PipelineSheet(
            onConfirm = { mode, crf, preset ->
                showSheet = false
                val src = pendingUri ?: return@PipelineSheet
                pendingUri = null
                isProcessing = true
                statusMsg = "Menyiapkan..."
                progressVal = 0

                scope.launch {
                    val resultUri = runPipeline(context, src, mode, crf, preset) { msg, p ->
                        statusMsg = msg; progressVal = p.coerceAtLeast(0); postNotif(context, "Kythera Pipeline", msg, p)
                    }
                    isProcessing = false
                    dismissNotif(context)
                    if (resultUri != null) fileChooserCallback?.onReceiveValue(arrayOf(resultUri)) else fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    statusMsg = ""; progressVal = 0
                }
            },
            onDismiss = { showSheet = false; pendingUri = null; fileChooserCallback?.onReceiveValue(null); fileChooserCallback = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.apply {
                        javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true; mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        useWideViewPort = true; loadWithOverviewMode = true; setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) return false
                            return try {
                                val intent = if (url.startsWith("intent://")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                ctx.startActivity(intent); true
                            } catch (_: Exception) { true }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("""
                                var meta = document.querySelector('meta[name="viewport"]');
                                if (meta) { meta.setAttribute('content','width=1280'); }
                                else { var m=document.createElement('meta'); m.name='viewport'; m.content='width=1280'; document.head.appendChild(m); }
                                document.body.style.overflow='auto'; document.documentElement.style.overflow='auto';
                            """.trimIndent(), null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                            fileChooserCallback?.onReceiveValue(null); fileChooserCallback = filePathCallback; filePickerLauncher.launch("video/*"); return true
                        }
                    }
                    loadUrl("https://www.tiktok.com/upload")
                }
            }
        )

        // ── Processing Overlay ──
        if (isProcessing) {
            Box(
                // 🔥 Ini nih posisi terbarunya, ngedorong kotak loading dari tengah ke arah atas layar!
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp) // Sesuaikan jaraknya dari atas layaknya screenshot ijo lu
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .background(Brush.verticalGradient(listOf(Color(0xF01A1A2E), Color(0xF0110D2E))), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                    Text("KYTHERA AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C4DFF), letterSpacing = 4.sp)
                    Spacer(Modifier.height(4.dp))
                    AiScanAnimation(statusMsg = statusMsg, progress = progressVal)
                }
            }
        }
    }
}
