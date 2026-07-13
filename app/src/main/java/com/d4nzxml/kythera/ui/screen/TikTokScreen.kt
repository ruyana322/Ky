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
import kotlin.math.max

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
        ).apply { description = "Kythera AI Processing" }
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
// KYTHERA BINARY PATCHER
// ═══════════════════════════════════════════════════════════════════════════
private object KytheraPatcher {

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

    private fun patchSampleTableMethod(data: ByteArray): ByteArray {
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

        val placeholderReplacements = HashMap(fixedReplacements)
        stcoBoxes.forEach { b -> placeholderReplacements[b] = buildStco(parseStco(b), 0L, if (b == stco) 0L else null, fakeSampleCount) }
        val moovPlaceholder = rebuildBox(moov, placeholderReplacements)

        val preservedBytes = concatBytes(preservedTopLevel)
        val oldMdatPayload = boxPayload(mdat)
        val newMdatPayloadStart = ftyp.size + moovPlaceholder.size + preservedBytes.size + 8
        var delta = (newMdatPayloadStart - mdat.contentStart).toLong()
        var fakeOffset = (newMdatPayloadStart + oldMdatPayload.size).toLong()

        var finalReplacements = HashMap(fixedReplacements)
        stcoBoxes.forEach { b -> finalReplacements[b] = buildStco(parseStco(b), delta, if (b == stco) fakeOffset else null, fakeSampleCount) }
        var moovNew = rebuildBox(moov, finalReplacements)

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

    fun patchVideo(input: ByteArray): ByteArray {
        val newBuf = patchSampleTableMethod(input)
        patchEncoder(newBuf)
        return newBuf
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SMART KYTHERA PIPELINE (AUTO-DETECT ENGINE)
// ═══════════════════════════════════════════════════════════════════════════
private suspend fun runSmartPipeline(
    context:    Context,
    sourceUri:  Uri,
    onProgress: (String, Int) -> Unit
): Uri? = withContext(Dispatchers.IO) {

    val ts         = System.currentTimeMillis()
    val cacheDir   = context.cacheDir
    val inputFile  = File(cacheDir, "ky_in_$ts.mp4")
    val encFile    = File(cacheDir, "ky_enc_$ts.mp4")
    val outputFile = File(cacheDir, "ky_out_$ts.mp4")

    // 🔥 1. Ekstrak Metadata untuk Auto-Detect
    var durationMs = 0L
    var videoWidth = 0
    var videoHeight = 0
    var mimeType = ""
    
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, sourceUri)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
        retriever.release()
    } catch (e: Exception) {
        durationMs = 0L
    }

    try {
        onProgress("📂 Menyalin video...", 5)
        context.contentResolver.openInputStream(sourceUri)?.use { ins ->
            inputFile.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext null

        // 🔥 2. Smart Logic: Tentukan Mode
                // 🔥 2. Smart Logic: Tentukan Mode (Disesuaikan persis dengan Web)
        val maxRes = max(videoWidth, videoHeight)
        
        // Cukup cek apakah dia MP4, persis kayak di main_2.js
        val isMp4 = mimeType.contains("mp4", ignoreCase = true) || 
                    sourceUri.toString().endsWith(".mp4", ignoreCase = true)
        
        // Jika lebih besar dari 1080p atau bukan MP4, baru Re-encode!
        val needsReencode = maxRes > 1920 || !isMp4


        FFmpegKitConfig.enableStatisticsCallback { stats ->
            if (durationMs > 0) {
                val timeMs = stats.time.toFloat()
                val ratio = (timeMs / durationMs).coerceIn(0f, 1f)
                val p = (5 + (ratio * 80)).toInt() 
                onProgress(if (needsReencode) "🎬 Re-encoding (CRF 20) $p%..." else "🎬 Remuxing $p%...", p)
            } else {
                onProgress(if (needsReencode) "🎬 Re-encoding..." else "🎬 Remuxing...", 50)
            }
        }

        // 🔥 MESIN KUALITAS DEWA (CRF 20) TAPI WAKTU LEBIH WARAS
        val cmd = if (needsReencode) {
            "-y -i \"${inputFile.absolutePath}\" " +
            "-threads 0 " + // 🔥 Gaspol semua core CPU
            "-c:v libx264 -preset veryfast -crf 20 " + // 🔥 Titik tengah: Kualitas tetep tajam ala CRF 20, tapi speed jauh lebih ngebut dari 'fast'
            "-bf 0 -movflags +faststart " +
            "-c:a copy " + // 🔥 Audio numpang lewat aja (wajib!)
            "-metadata copyright=\"By Kythera\" " +
            "-metadata artist=\"D4nzxml\" " +
            "\"${encFile.absolutePath}\""
        } else {

            "-y -i \"${inputFile.absolutePath}\" " +
            "-threads 0 " + // Aktifkan semua core CPU
            "-c copy " + // Remux super kilat
            "-movflags +faststart " +
            "\"${encFile.absolutePath}\""
        }

        val session = FFmpegKit.execute(cmd)
        FFmpegKitConfig.enableStatisticsCallback(null)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            onProgress("❌ Proses video gagal", -1)
            return@withContext null
        }

        // 🔥 4. Suntik Biner (Kythera Patcher)
        onProgress("🩹 Menerapkan Kythera Patch...", 85)
        outputFile.writeBytes(KytheraPatcher.patchVideo(encFile.readBytes()))
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
// UI: HOLOGRAM ANIMATION
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AiScanAnimation(statusMsg: String, progress: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(110.dp).scale(pulse).clip(CircleShape).background(Color(0x407C4DFF)))
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)))),
                contentAlignment = Alignment.Center
            ) { Text("✦", fontSize = 36.sp, color = Color.White) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            
            if (progress in 0..100) {
                Text("$progress%", color = Color(0xFFC4B5FD), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// JS SNIPPETS (pisah biar onPageFinished ringan)
// ═══════════════════════════════════════════════════════════════════════════

// 🔥 Critical JS: hook fetch/XHR + viewport fix — inject segera setelah page finish
private val JS_CRITICAL = """
    (function() {
        var meta = document.querySelector('meta[name="viewport"]');
        if (meta) { meta.setAttribute('content','width=1280'); }
        else { var m=document.createElement('meta'); m.name='viewport'; m.content='width=1280'; document.head.appendChild(m); }
        document.body.style.overflow='auto';
        document.documentElement.style.overflow='auto';
    })();

    (function() {
        if (window.__cUInjected) return;
        window.__cUInjected = true;
        const SIGNATURE = "\n\n⚡ Upload method by Kythera ⚡";
        function modifyPayload(bodyStr) {
            try {
                let data = JSON.parse(bodyStr);
                let modified = false;
                if (data.single_post_req_list && data.single_post_req_list[0]) {
                    let featureInfo = data.single_post_req_list[0].single_post_feature_info;
                    if (featureInfo) {
                        featureInfo.has_original_audio = 1;
                        if (featureInfo.music_info) delete featureInfo.music_info;
                        modified = true;
                    }
                }
                const injectText = (obj) => {
                    if (!obj || typeof obj !== 'object') return;
                    ['text', 'markup_text', 'desc', 'title', 'caption'].forEach(key => {
                        if (typeof obj[key] === 'string' && !obj[key].includes('Kythera')) {
                            obj[key] += SIGNATURE; modified = true;
                        }
                    });
                    Object.values(obj).forEach(val => injectText(val));
                };
                injectText(data);
                return modified ? JSON.stringify(data) : bodyStr;
            } catch(e) { return bodyStr; }
        }
        const origFetch = window.fetch;
        window.fetch = async function(resource, config) {
            let url = typeof resource === 'string' ? resource : resource?.url;
            if (url && (url.includes('/upload') || url.includes('/publish') || url.includes('/post')) && config && typeof config.body === 'string') {
                config.body = modifyPayload(config.body);
            }
            return origFetch.apply(this, arguments);
        };
        const origOpen = XMLHttpRequest.prototype.open;
        const origSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(method, url) { this._url = url; return origOpen.apply(this, arguments); };
        XMLHttpRequest.prototype.send = function(body) {
            if (this._url && (this._url.includes('/upload') || this._url.includes('/publish') || this._url.includes('/post')) && typeof body === 'string') {
                body = modifyPayload(body);
            }
            return origSend.call(this, body);
        };
    })();
""".trimIndent()

// 🎨 Toast JS: kosmetik, delay 600ms biar gak block render awal
private val JS_TOAST = """
    (function () {
        if (window.__d4nzToastDone) return;
        window.__d4nzToastDone = true;
        function showToast() {
            const style = document.createElement('style');
            style.textContent = '#__d4nz_toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); z-index: 2147483647; background: #0a0a0a; border: 1.5px solid #7c4dff; color: #fff; border-radius: 50px; padding: 9px 20px; font-family: "Segoe UI", system-ui, sans-serif; font-size: 12px; font-weight: 800; display: flex; align-items: center; gap: 8px; box-shadow: 0 4px 20px rgba(124,77,255,0.4); letter-spacing: 0.06em; pointer-events: none; animation: __d4nzFadeIn 0.4s ease; } .__d4nz_dot { width: 7px; height: 7px; border-radius: 50%; background: #7c4dff; animation: __d4nzPulse 1.4s infinite; flex-shrink: 0; } @keyframes __d4nzFadeIn { from { opacity:0; transform: translateX(-50%) translateY(12px); } to { opacity:1; transform: translateX(-50%) translateY(0); } } @keyframes __d4nzPulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:0.3; transform:scale(0.65); } }';
            document.head.appendChild(style);
            const toast = document.createElement('div');
            toast.id = '__d4nz_toast';
            toast.innerHTML = '<span class="__d4nz_dot"></span>D4NZXML GODMODE ACTIVE';
            document.body.appendChild(toast);
        }
        if (document.body) { showToast(); } else { document.addEventListener('DOMContentLoaded', showToast); }
    })();
""".trimIndent()

// ═══════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var statusMsg           by remember { mutableStateOf("") }
    var progressVal         by remember { mutableStateOf(0) }
    var isProcessing        by remember { mutableStateOf(false) }

    // 🔥 WebView dibikin lebih awal (preload) — bukan di dalam factory AndroidView
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 🔥 Hardware acceleration eksplisit
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled          = true
                domStorageEnabled          = true
                databaseEnabled            = true
                allowFileAccess            = true
                mixedContentMode           = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString            = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                useWideViewPort            = true
                loadWithOverviewMode       = true
                setSupportZoom(true)
                builtInZoomControls        = true
                displayZoomControls        = false
                // 🔥 Cache: pakai cache disk bawaan browser
                cacheMode                  = WebSettings.LOAD_DEFAULT
                // 🔥 Prioritas render tinggi
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                mediaPlaybackRequiresUserGesture = false
            }
        }
    }

    // 🔥 Preload URL segera saat composable masuk — WebView udah siap sebelum keliatan
    LaunchedEffect(Unit) {
        webView.loadUrl("https://www.tiktok.com/upload")
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            statusMsg    = "Menyiapkan Kythera Engine..."
            progressVal  = 0

            // 🛑 Pause WebView selama proses encoding
            webView.onPause()
            webView.pauseTimers()

            scope.launch {
                val resultUri = runSmartPipeline(context, uri) { msg, p ->
                    statusMsg   = msg
                    progressVal = p.coerceAtLeast(0)
                    postNotif(context, "Kythera Pipeline", msg, p)
                }

                isProcessing = false
                dismissNotif(context)

                // ▶️ Resume WebView setelah selesai
                webView.onResume()
                webView.resumeTimers()

                if (resultUri != null) {
                    fileChooserCallback?.onReceiveValue(arrayOf(resultUri))
                } else {
                    fileChooserCallback?.onReceiveValue(null)
                }
                fileChooserCallback = null
                statusMsg   = ""
                progressVal = 0
            }
        } else {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
        }
    }

    // 🔥 Setup client sekali — pisah dari factory biar gak bikin WebView baru tiap recompose
    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) return false
                return try {
                    val intent = if (url.startsWith("intent://"))
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    else
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent); true
                } catch (_: Exception) { true }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 🔥 Inject critical hook SEGERA
                view?.evaluateJavascript(JS_CRITICAL, null)
                // 🎨 Toast delay 600ms — kosmetik, gak rush
                view?.postDelayed({ view.evaluateJavascript(JS_TOAST, null) }, 600)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                filePickerLauncher.launch("video/*")
                return true
            }
        }

        onDispose {
            webView.webViewClient  = WebViewClient()
            webView.webChromeClient = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 🔥 AndroidView pakai instance preload — factory cuma attach, bukan init ulang
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { webView }
        )

        // ── Processing Overlay (Hologram Full Block) ──
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AiScanAnimation(statusMsg = statusMsg, progress = progressVal)
                }
            }
        }
    }
}
