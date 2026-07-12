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
// NOTIFICATION
// ═══════════════════════════════════════════════════════════════════════════

private const val NOTIF_CHANNEL_ID = "kythera_pipeline"
private const val NOTIF_ID = 7777

private fun ensureNotifChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            NOTIF_CHANNEL_ID, "Kythera Pipeline",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Reencoder + Patch progress" }
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
// SHARK PATCHER ENGINE
// ═══════════════════════════════════════════════════════════════════════════

private object SharkPatcher {

    private val FAKE_SAMPLE_SIZE  = 8L
    private val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)
    private val VIDEO_TIMESCALE   = 90000L
    private val VIDEO_DURATION    = 2269500L
    private val VIDEO_EDIT_MEDIA_TIME = 0L
    private val VIDEO_SAMPLE_DELTA    = 1500L
    private val CONTAINER_BOXES = setOf(
        "moov","trak","mdia","minf","stbl","edts","dinf","udta","meta","ilst"
    )

    private class Mp4Box(
        val type: String, val offset: Int, val size: Long, val headerSize: Int,
        val contentStart: Int, val end: Int, val path: String, val data: ByteArray,
        var children: List<Mp4Box> = emptyList(), var prefixStart: Int = 0, var prefixEnd: Int = 0
    )

    private fun getUint32(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF) shl 24) or
        ((buf[offset+1].toLong() and 0xFF) shl 16) or
        ((buf[offset+2].toLong() and 0xFF) shl 8) or
        (buf[offset+3].toLong() and 0xFF)

    private fun setUint32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset]   = ((value shr 24) and 0xFF).toByte()
        buf[offset+1] = ((value shr 16) and 0xFF).toByte()
        buf[offset+2] = ((value shr  8) and 0xFF).toByte()
        buf[offset+3] = (value and 0xFF).toByte()
    }

    private fun getBoxType(buf: ByteArray, offset: Int) = String(buf, offset, 4)
    private fun setBoxType(buf: ByteArray, offset: Int, type: String) {
        type.toByteArray().forEachIndexed { i, v -> buf[offset+i] = v }
    }

    private fun readBox(data: ByteArray, offset: Int, end: Int, parentPath: String = ""): Mp4Box {
        if (offset + 8 > end) throw Exception("MP4 tidak valid: box tidak lengkap.")
        val smallSize = getUint32(data, offset)
        val type = getBoxType(data, offset + 4)
        var size = smallSize; var hdr = 8
        if (smallSize == 1L) {
            if (offset + 16 > end) throw Exception("MP4 tidak valid: box $type incomplete.")
            size = (getUint32(data, offset+8) shl 32) or getUint32(data, offset+12); hdr = 16
        } else if (smallSize == 0L) { size = (end - offset).toLong() }
        if (size < hdr || offset + size > end) throw Exception("MP4 tidak valid: ukuran salah di box $type.")
        val path = if (parentPath.isNotEmpty()) "$parentPath/$type" else type
        return Mp4Box(type, offset, size, hdr, offset+hdr, offset+size.toInt(), path, data,
            prefixStart = offset+hdr, prefixEnd = offset+hdr)
    }

    private fun childStartForBox(box: Mp4Box) =
        if (box.type == "meta") box.contentStart + 4 else box.contentStart

    private fun parseBoxes(data: ByteArray, start: Int = 0, end: Int = data.size, parentPath: String = ""): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>(); var offset = start
        while (offset + 8 <= end) {
            val box = readBox(data, offset, end, parentPath)
            if (box.type in CONTAINER_BOXES) {
                val cs = childStartForBox(box)
                if (cs > box.end) throw Exception("MP4 tidak valid: container ${box.type} terlalu pendek.")
                box.prefixStart = box.contentStart; box.prefixEnd = cs
                box.children = parseBoxes(data, cs, box.end, box.path)
            }
            boxes.add(box); offset = box.end
        }
        return boxes
    }

    private fun findChild(box: Mp4Box, type: String) = box.children.find { it.type == type }
    private fun findDesc(box: Mp4Box, path: List<String>): Mp4Box? {
        var cur: Mp4Box? = box
        for (t in path) { cur = cur?.let { findChild(it, t) } ?: return null }
        return cur
    }
    private fun hdlrType(trak: Mp4Box): String? {
        val hdlr = findDesc(trak, listOf("mdia","hdlr")) ?: return null
        return if (hdlr.offset + 20 <= hdlr.end) getBoxType(hdlr.data, hdlr.offset + 16) else null
    }

    private fun parseStsz(b: Mp4Box): List<Long> {
        val ss = getUint32(b.data, b.offset+12); val cnt = getUint32(b.data, b.offset+16).toInt()
        if (ss != 0L) return List(cnt) { ss }
        return (0 until cnt).map { getUint32(b.data, b.offset+20+it*4) }
    }
    private fun parseStco(b: Mp4Box): List<Long> {
        val cnt = getUint32(b.data, b.offset+12).toInt()
        return (0 until cnt).map { getUint32(b.data, b.offset+16+it*4) }
    }
    private fun parseStsc(b: Mp4Box): List<LongArray> {
        val cnt = getUint32(b.data, b.offset+12).toInt()
        return (0 until cnt).map { i ->
            val o = b.offset+16+i*12
            longArrayOf(getUint32(b.data, o), getUint32(b.data, o+4), getUint32(b.data, o+8))
        }
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        setUint32(out, 0, (8 + payload.size).toLong()); setBoxType(out, 4, type)
        System.arraycopy(payload, 0, out, 8, payload.size); return out
    }
    private fun concatBytes(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size }); var p = 0
        for (a in parts) { System.arraycopy(a, 0, out, p, a.size); p += a.size }; return out
    }
    private fun boxBytes(b: Mp4Box) = b.data.copyOfRange(b.offset, b.end)
    private fun boxPayload(b: Mp4Box) = b.data.copyOfRange(b.contentStart, b.end)

    private fun buildMdhd(b: Mp4Box): ByteArray {
        val p = boxPayload(b); setUint32(p, 12, VIDEO_TIMESCALE); setUint32(p, 16, VIDEO_DURATION)
        return makeBox("mdhd", p)
    }
    private fun buildElst(b: Mp4Box): ByteArray {
        val p = boxPayload(b); setUint32(p, 12, VIDEO_EDIT_MEDIA_TIME); return makeBox("elst", p)
    }
    private fun buildStts(real: Long, fake: Long): ByteArray {
        val p = ByteArray(4+4+8+8); setUint32(p, 4, 2L)
        setUint32(p, 8, real); setUint32(p, 12, VIDEO_SAMPLE_DELTA)
        setUint32(p, 16, fake); setUint32(p, 20, VIDEO_SAMPLE_DELTA)
        return makeBox("stts", p)
    }
    private fun buildStsz(sizes: List<Long>, fake: Long): ByteArray {
        val total = sizes.size + fake.toInt()
        val p = ByteArray(4+4+4+total*4); setUint32(p, 8, total.toLong()); var o = 12
        for (s in sizes) { setUint32(p, o, s); o += 4 }
        repeat(fake.toInt()) { setUint32(p, o, FAKE_SAMPLE_SIZE); o += 4 }
        return makeBox("stsz", p)
    }
    private fun buildStsc(rows: List<LongArray>, chunkCnt: Long): ByteArray {
        val r = rows.map { it.copyOf() }.toMutableList()
        if (r.isEmpty() || r.last()[1] != 1L) r.add(longArrayOf(chunkCnt+1, 1L, 1L))
        val p = ByteArray(4+4+r.size*12); setUint32(p, 4, r.size.toLong()); var o = 8
        for (row in r) { setUint32(p, o, row[0]); setUint32(p, o+4, row[1]); setUint32(p, o+8, row[2]); o += 12 }
        return makeBox("stsc", p)
    }
    private fun buildStco(offsets: List<Long>, delta: Long, fakeOff: Long?, fake: Long): ByteArray {
        val cnt = offsets.size + (if (fakeOff != null) fake.toInt() else 0)
        val p = ByteArray(4+4+cnt*4); setUint32(p, 4, cnt.toLong()); var o = 8
        for (off in offsets) { setUint32(p, o, off+delta); o += 4 }
        if (fakeOff != null) repeat(fake.toInt()) { setUint32(p, o, fakeOff); o += 4 }
        return makeBox("stco", p)
    }
    private fun rebuildBox(box: Mp4Box, rep: Map<Mp4Box, ByteArray>): ByteArray {
        rep[box]?.let { return it }
        if (box.children.isEmpty()) return boxBytes(box)
        val parts = mutableListOf(box.data.copyOfRange(box.prefixStart, box.prefixEnd))
        for (child in box.children) parts.add(rebuildBox(child, rep))
        return makeBox(box.type, concatBytes(parts))
    }

    fun patchShark(input: ByteArray): ByteArray {
        val top  = parseBoxes(input)
        val ftyp = top.find { it.type == "ftyp" } ?: throw Exception("ftyp tidak ditemukan.")
        val moov = top.find { it.type == "moov" } ?: throw Exception("moov tidak ditemukan.")
        val mdat = top.find { it.type == "mdat" } ?: throw Exception("mdat tidak ditemukan.")

        val vTrak = moov.children.find { it.type == "trak" && hdlrType(it) == "vide" }
            ?: throw Exception("Track video tidak ditemukan.")
        val stbl = findDesc(vTrak, listOf("mdia","minf","stbl")) ?: throw Exception("stbl missing")
        val mdhd = findDesc(vTrak, listOf("mdia","mdhd"))        ?: throw Exception("mdhd missing")
        val elst = findDesc(vTrak, listOf("edts","elst"))        ?: throw Exception("elst missing")
        val stts = findChild(stbl, "stts") ?: throw Exception("stts missing")
        val stsc = findChild(stbl, "stsc") ?: throw Exception("stsc missing")
        val stsz = findChild(stbl, "stsz") ?: throw Exception("stsz missing")
        val stco = findChild(stbl, "stco") ?: throw Exception("stco missing")

        val origSizes    = parseStsz(stsz)
        val realCnt      = origSizes.size.toLong()
        val fakeCnt      = realCnt * 9L
        val origStscRows = parseStsc(stsc)
        val origOffsets  = parseStco(stco)

        val allStco = mutableListOf<Mp4Box>()
        moov.children.filter { it.type == "trak" }.forEach { t ->
            findDesc(t, listOf("mdia","minf","stbl"))?.let { s ->
                if (findChild(s, "co64") != null) throw Exception("co64 tidak didukung.")
                findChild(s, "stco")?.let { allStco.add(it) }
            }
        }

        val preserved      = top.filter { it.type !in listOf("ftyp","moov","mdat") }.map { boxBytes(it) }
        val preservedBytes = concatBytes(preserved)
        val fixed = mapOf(
            mdhd to buildMdhd(mdhd),
            elst to buildElst(elst),
            stts to buildStts(realCnt, fakeCnt),
            stsc to buildStsc(origStscRows, origOffsets.size.toLong()),
            stsz to buildStsz(origSizes, fakeCnt)
        )

        // Pass 1 placeholder
        val ph = HashMap(fixed)
        allStco.forEach { b -> ph[b] = buildStco(parseStco(b), 0L, if (b==stco) 0L else null, fakeCnt) }
        val moovPh     = rebuildBox(moov, ph)
        val mdatPayload = boxPayload(mdat)
        val startPos   = ftyp.size + moovPh.size + preservedBytes.size + 8

        // Pass 2
        var delta   = (startPos - mdat.contentStart).toLong()
        var fakeOff = (startPos + mdatPayload.size).toLong()
        var rep2 = HashMap(fixed)
        allStco.forEach { b -> rep2[b] = buildStco(parseStco(b), delta, if (b==stco) fakeOff else null, fakeCnt) }
        var moovNew = rebuildBox(moov, rep2)

        // Pass 3 recalculate
        val recalc = ftyp.size + moovNew.size + preservedBytes.size + 8
        delta   = (recalc - mdat.contentStart).toLong()
        fakeOff = (recalc + mdatPayload.size).toLong()
        val rep3 = HashMap(fixed)
        allStco.forEach { b -> rep3[b] = buildStco(parseStco(b), delta, if (b==stco) fakeOff else null, fakeCnt) }
        moovNew = rebuildBox(moov, rep3)

        val mdatNew = makeBox("mdat", concatBytes(listOf(mdatPayload, FAKE_SAMPLE_BYTES)))
        val result  = concatBytes(listOf(boxBytes(ftyp), moovNew, preservedBytes, mdatNew))

        // Patch encoder string in-place
        val lavf   = "Lavf".toByteArray()
        val target = "Lavf59.16.100".toByteArray()
        var i = 0
        while (i <= result.size - 16) {
            if (result[i]==lavf[0] && result[i+1]==lavf[1] && result[i+2]==lavf[2] &&
                result[i+3]==lavf[3] && result[i+4] in 0x30..0x39) {
                var end2 = i + 4
                while (end2 < result.size && result[end2] >= 0x20 && result[end2] < 0x7F) end2++
                val ol = end2 - i
                for (j in 0 until ol) result[i+j] = if (j < target.size) target[j] else 0x00
                break
            }
            i++
        }
        return result
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIPELINE — Auto: Reencoder (CBR 17M ultrafast) + Shark Patch
// ═══════════════════════════════════════════════════════════════════════════

private suspend fun runPipeline(
    context:    Context,
    sourceUri:  Uri,
    onProgress: (String, Int) -> Unit
): Uri? = withContext(Dispatchers.IO) {

    val ts         = System.currentTimeMillis()
    val cacheDir   = context.cacheDir
    val inputFile  = File(cacheDir, "ky_in_$ts.mp4")
    val encFile    = File(cacheDir, "ky_enc_$ts.mp4")
    val outputFile = File(cacheDir, "ky_out_$ts.mp4")

    var durationMs = 0L
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, sourceUri)
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
    } catch (_: Exception) {}

    try {
        onProgress("📂 Menyalin video...", 5)
        context.contentResolver.openInputStream(sourceUri)?.use { ins ->
            inputFile.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext null

        // Step 1 — Reencoder CBR 17M ultrafast (sama kayak Senze tapi native = lebih cepat)
        onProgress("🎬 Memulai Reencoder...", 8)

        FFmpegKitConfig.enableStatisticsCallback { stats ->
            if (durationMs > 0) {
                val ratio = (stats.time.toFloat() / durationMs).coerceIn(0f, 1f)
                val p = (8 + ratio * 72).toInt()
                onProgress("🎬 Encoding ${(ratio * 100).toInt()}%...", p)
            } else {
                onProgress("🎬 Encoding...", 50)
            }
        }

        val cmd = "-y -i \"${inputFile.absolutePath}\" " +
                  "-vf fps=60 " +
                  "-c:v libx264 -preset ultrafast " +
                  "-b:v 17M -minrate 17M -maxrate 17M -bufsize 17M " +
                  "-c:a aac -b:a 128k " +
                  "-af aresample=async=1:first_pts=0 " +
                  "-shortest " +
                  "-movflags +faststart " +
                  "\"${encFile.absolutePath}\""

        val session = FFmpegKit.execute(cmd)
        FFmpegKitConfig.enableStatisticsCallback(null)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            onProgress("❌ Encode gagal", -1)
            return@withContext null
        }

        // Step 2 — Shark Sample Table patch
        onProgress("🩹 Rebuilding Shark MP4 Table...", 85)
        outputFile.writeBytes(SharkPatcher.patchShark(encFile.readBytes()))

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
// UI: HOLOGRAM OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AiScanAnimation(statusMsg: String, progress: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
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
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.width(200.dp).height(4.dp),
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFF2A2A3E)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TikTokScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingUri          by remember { mutableStateOf<Uri?>(null) }
    var statusMsg           by remember { mutableStateOf("") }
    var progressVal         by remember { mutableStateOf(0) }
    var isProcessing        by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            // Langsung proses tanpa sheet pilihan mode
            val src = uri
            pendingUri = null
            isProcessing = true
            statusMsg = "Menyiapkan..."
            progressVal = 0

            scope.launch {
                val resultUri = runPipeline(context, src) { msg, p ->
                    statusMsg = msg
                    progressVal = p.coerceAtLeast(0)
                    postNotif(context, "Kythera Reencoder + Patch", msg, p)
                }
                isProcessing = false
                dismissNotif(context)
                if (resultUri != null) fileChooserCallback?.onReceiveValue(arrayOf(resultUri))
                else fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
                statusMsg = ""; progressVal = 0
            }
        } else {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled      = true
                        domStorageEnabled      = true
                        allowFileAccess        = true
                        mixedContentMode       = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        useWideViewPort        = true
                        loadWithOverviewMode   = true
                        setSupportZoom(true)
                        builtInZoomControls    = true
                        displayZoomControls    = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) return false
                            return try {
                                val intent = if (url.startsWith("intent://"))
                                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                else Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                ctx.startActivity(intent); true
                            } catch (_: Exception) { true }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val js = """
                                var meta = document.querySelector('meta[name="viewport"]');
                                if (meta) { meta.setAttribute('content','width=1280'); }
                                else { var m=document.createElement('meta'); m.name='viewport'; m.content='width=1280'; document.head.appendChild(m); }
                                document.body.style.overflow='auto'; document.documentElement.style.overflow='auto';

                                (function () {
                                  if (window.__kyToastDone) return;
                                  window.__kyToastDone = true;
                                  function showToast() {
                                    const style = document.createElement('style');
                                    style.textContent = `
                                      #__ky_toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); z-index: 2147483647; background: #0a0a0a; border: 1.5px solid #fe2c55; color: #fff; border-radius: 50px; padding: 9px 20px; font-family: 'Segoe UI', system-ui, sans-serif; font-size: 12px; font-weight: 800; display: flex; align-items: center; gap: 8px; box-shadow: 0 4px 20px rgba(254,44,85,0.4); letter-spacing: 0.06em; pointer-events: none; animation: __kyFadeIn 0.4s ease; }
                                      .__ky_dot { width: 7px; height: 7px; border-radius: 50%; background: #fe2c55; animation: __kyPulse 1.4s infinite; flex-shrink: 0; }
                                      @keyframes __kyFadeIn { from { opacity:0; transform: translateX(-50%) translateY(12px); } to { opacity:1; transform: translateX(-50%) translateY(0); } }
                                      @keyframes __kyPulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:0.3; transform:scale(0.65); } }
                                    `;
                                    document.head.appendChild(style);
                                    const toast = document.createElement('div');
                                    toast.id = '__ky_toast';
                                    toast.innerHTML = '<span class="__ky_dot"></span>KYTHERA ACTIVE';
                                    document.body.appendChild(toast);
                                  }
                                  if (document.body) { showToast(); } else { document.addEventListener('DOMContentLoaded', showToast); }
                                })();

                                (function() {
                                    if (window.__kyInjected) return;
                                    window.__kyInjected = true;
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
                                                ['text','markup_text','desc','title','caption'].forEach(key => {
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
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
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
                    loadUrl("https://www.tiktok.com/upload")
                }
            }
        )

        // Processing Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                AiScanAnimation(statusMsg = statusMsg, progress = progressVal)
            }
        }
    }
}
