package com.d4nzxml.kythera.ui.screen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// MODE
// ═══════════════════════════════════════════════════════════════════════════
enum class PipelineMode(val label: String, val icon: String, val desc: String) {
    PATCH_ONLY(
        label = "Patch Only",
        icon  = "🩹",
        desc  = "Shark HD patch langsung ke video asli"
    ),
    ENCODE_PATCH(
        label = "Encode + Patch",
        icon  = "🎬",
        desc  = "Re-encode dulu lalu Shark patch"
    ),
    KYTHERA_60(
        label = "Kythera 60fps",
        icon  = "⚡",
        desc  = "Metadata stamp Z-payload + MTLib + encoder tag"
    )
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
    try { NotificationManagerCompat.from(context).notify(NOTIF_ID, b.build()) }
    catch (_: SecurityException) {}
}

private fun dismissNotif(context: Context) =
    NotificationManagerCompat.from(context).cancel(NOTIF_ID)

// ═══════════════════════════════════════════════════════════════════════════
// SHARK PATCHER (port dari patcher.js)
// ═══════════════════════════════════════════════════════════════════════════
private object SharkPatcher {

    private fun u32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong()   and 0xFF) shl 24) or
        ((buf[off+1].toLong() and 0xFF) shl 16) or
        ((buf[off+2].toLong() and 0xFF) shl 8)  or
        (buf[off+3].toLong()  and 0xFF)

    private fun putU32(buf: ByteArray, off: Int, v: Long) {
        buf[off]   = ((v shr 24) and 0xFF).toByte()
        buf[off+1] = ((v shr 16) and 0xFF).toByte()
        buf[off+2] = ((v shr 8)  and 0xFF).toByte()
        buf[off+3] = (v           and 0xFF).toByte()
    }

    private fun tag(buf: ByteArray, off: Int) =
        String(byteArrayOf(buf[off+4], buf[off+5], buf[off+6], buf[off+7]))

    private fun findBox(buf: ByteArray, start: Int, end: Int, name: String): Int {
        var pos = start
        while (pos + 8 <= end) {
            val sz = u32(buf, pos)
            if (sz < 8) break
            if (tag(buf, pos) == name) return pos
            pos += sz.toInt()
        }
        return -1
    }

    private fun findPath(buf: ByteArray, start: Int, end: Int, path: List<String>): Int {
        if (path.isEmpty()) return start
        val found = findBox(buf, start + 8, end, path[0])
        if (found == -1) return -1
        val sz = u32(buf, found).toInt()
        return findPath(buf, found, found + sz, path.drop(1))
    }

    // ── Z-Payload ────────────────────────────────────────────────────────
    private fun zPayload(buf: ByteArray) {
        val mdatOff = findBox(buf, 0, buf.size, "mdat")
        if (mdatOff == -1) return
        val zt = mdatOff + 10
        for (i in 0 until 128) if (zt + i < buf.size) buf[zt + i] = 0x5A
    }

    // ── Encoder string → Lavf59.16.100 ────────────────────────────────────
    private fun patchEncoder(buf: ByteArray) {
        val lavf   = "Lavf".toByteArray()
        val target = "Lavf59.16.100".toByteArray()
        var i = 0
        while (i <= buf.size - 16) {
            if (buf[i]==lavf[0] && buf[i+1]==lavf[1] &&
                buf[i+2]==lavf[2] && buf[i+3]==lavf[3] &&
                buf[i+4] in 0x30..0x39) {
                var end = i + 4
                while (end < buf.size && buf[end] >= 0x20 && buf[end] < 0x7F) end++
                val ol = end - i
                for (j in 0 until ol) buf[i+j] = if (j < target.size) target[j] else 0x00
                break
            }
            i++
        }
    }

    // ── MTLib inject (moov/udta/----) ─────────────────────────────────────
    private fun injectMTLib(input: ByteArray): ByteArray {
        val enc      = Charsets.US_ASCII
        val domain   = "com.apple.quicktime".toByteArray(enc)
        val keyBytes = "MTLib".toByteArray(enc)
        val valBytes = "PyPVGCodec".toByteArray(enc)

        fun makeAtom(name: String, payload: ByteArray): ByteArray {
            val total = 8 + payload.size
            val out = ByteArray(total)
            out[0] = ((total shr 24) and 0xFF).toByte()
            out[1] = ((total shr 16) and 0xFF).toByte()
            out[2] = ((total shr 8)  and 0xFF).toByte()
            out[3] = (total           and 0xFF).toByte()
            name.toByteArray(enc).copyInto(out, 4)
            payload.copyInto(out, 8)
            return out
        }

        val meanPayload = ByteArray(4) + domain
        val namePayload = ByteArray(4) + keyBytes
        val dataPayload = ByteArray(4).also {
            it[3] = 1  // type indicator
        } + valBytes

        val mean = makeAtom("mean", meanPayload)
        val name = makeAtom("name", namePayload)
        val data = makeAtom("data", dataPayload)
        val freeformInner = mean + name + data
        val freeform = makeAtom("----", freeformInner)

        // Cari moov
        val buf  = input
        var pos  = 0
        var moovOff = -1; var moovSz = 0
        while (pos + 8 <= buf.size) {
            val sz = u32(buf, pos).toInt()
            if (sz < 8) break
            if (tag(buf, pos) == "moov") { moovOff = pos; moovSz = sz; break }
            pos += sz
        }
        if (moovOff == -1) return input

        val moovEnd = moovOff + moovSz
        var udtaOff = -1; var udtaSz = 0
        pos = moovOff + 8
        while (pos + 8 <= moovEnd) {
            val sz = u32(buf, pos).toInt()
            if (sz < 8) break
            if (tag(buf, pos) == "udta") { udtaOff = pos; udtaSz = sz; break }
            pos += sz
        }

        val result: ByteArray
        if (udtaOff != -1) {
            val insertAt = udtaOff + udtaSz
            result = buf.copyOfRange(0, insertAt) + freeform + buf.copyOfRange(insertAt, buf.size)
            putU32(result, moovOff, (moovSz + freeform.size).toLong())
            putU32(result, udtaOff, (udtaSz + freeform.size).toLong())
        } else {
            val udtaAtom = makeAtom("udta", freeform)
            result = buf.copyOfRange(0, moovEnd) + udtaAtom + buf.copyOfRange(moovEnd, buf.size)
            putU32(result, moovOff, (moovSz + udtaAtom.size).toLong())
        }
        return result
    }

    // ── Shark Sample Table patch ──────────────────────────────────────────
    private fun sharkSampleTable(input: ByteArray): ByteArray {
        val buf = input.copyOf()

        val moovOff = findBox(buf, 0, buf.size, "moov")
        if (moovOff == -1) return buf
        val moovSz  = u32(buf, moovOff).toInt()
        val moovEnd = moovOff + moovSz

        // Cari video trak
        var pos = moovOff + 8
        var videoTrakOff = -1; var videoTrakEnd = -1
        while (pos + 8 <= moovEnd) {
            val sz = u32(buf, pos).toInt()
            if (sz < 8) break
            if (tag(buf, pos) == "trak") {
                val hdlr = findPath(buf, pos, pos + sz, listOf("mdia", "hdlr"))
                if (hdlr != -1 && String(buf, hdlr + 16, 4) == "vide") {
                    videoTrakOff = pos; videoTrakEnd = pos + sz
                }
            }
            pos += sz
        }
        if (videoTrakOff == -1) return buf

        // mdhd
        val mdhdOff = findPath(buf, videoTrakOff, videoTrakEnd, listOf("mdia", "mdhd"))
        if (mdhdOff != -1) {
            putU32(buf, mdhdOff + 8 + 12, 90000L)
            putU32(buf, mdhdOff + 8 + 16, 2269500L)
        }

        // elst
        val elstOff = findPath(buf, videoTrakOff, videoTrakEnd, listOf("edts", "elst"))
        if (elstOff != -1) putU32(buf, elstOff + 8 + 12, 3000L)

        // stts fake entry
        val stblOff = findPath(buf, videoTrakOff, videoTrakEnd, listOf("mdia", "minf", "stbl"))
        if (stblOff != -1) {
            val stblSz  = u32(buf, stblOff).toInt()
            val sttsOff = findBox(buf, stblOff + 8, stblOff + stblSz, "stts")
            if (sttsOff != -1) {
                val entryCount = u32(buf, sttsOff + 8 + 4).toInt()
                if (entryCount >= 2) {
                    val realCount    = u32(buf, sttsOff + 8 + 8)
                    val fakeCount    = realCount * 9
                    val lastEntryOff = sttsOff + 8 + 8 + (entryCount - 1) * 8
                    putU32(buf, lastEntryOff, fakeCount)
                    putU32(buf, lastEntryOff + 4, 1500L)
                }
            }
        }

        return buf
    }

    // ── PUBLIC: Patch Only ────────────────────────────────────────────────
    fun patchOnly(input: ByteArray): ByteArray {
        val buf = sharkSampleTable(input)
        zPayload(buf)
        patchEncoder(buf)
        return buf
    }

    // ── PUBLIC: Kythera 60fps (metadata stamp only) ───────────────────────
    fun metadataStamp(input: ByteArray): ByteArray {
        val buf = input.copyOf()
        zPayload(buf)
        val mt = injectMTLib(buf)
        val final = mt.copyOf()
        patchEncoder(final)
        return final
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIPELINE RUNNER
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

    try {
        // Salin ke cache
        onProgress("📂 Menyalin video...", 5)
        context.contentResolver.openInputStream(sourceUri)?.use { ins ->
            inputFile.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext null

        when (mode) {

            // ── PATCH ONLY ────────────────────────────────────────────────
            PipelineMode.PATCH_ONLY -> {
                onProgress("🩹 Membaca video...", 20)
                val raw    = inputFile.readBytes()
                onProgress("🩹 Menerapkan Shark Patch...", 60)
                val patched = SharkPatcher.patchOnly(raw)
                onProgress("💾 Menyimpan...", 90)
                outputFile.writeBytes(patched)
            }

            // ── ENCODE + PATCH ────────────────────────────────────────────
            PipelineMode.ENCODE_PATCH -> {
                onProgress("🎬 Encoding CRF $crf / $preset...", 10)

                var lastP = 10
                FFmpegKitConfig.enableStatisticsCallback { stats ->
                    val p = (10 + (stats.time / 1000.0).coerceIn(0.0, 1.0) * 60).toInt()
                    if (p > lastP) { lastP = p; onProgress("🎬 Encoding ${p - 10}%...", p) }
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

                onProgress("🩹 Menerapkan Shark Patch...", 80)
                val encBytes = encFile.readBytes()
                val patched  = SharkPatcher.patchOnly(encBytes)
                onProgress("💾 Menyimpan...", 95)
                outputFile.writeBytes(patched)
            }

            // ── KYTHERA 60FPS ─────────────────────────────────────────────
            PipelineMode.KYTHERA_60 -> {
                onProgress("⚡ Membaca video...", 20)
                val raw = inputFile.readBytes()
                onProgress("⚡ Menyematkan metadata stamp...", 55)
                val stamped = SharkPatcher.metadataStamp(raw)
                onProgress("💾 Menyimpan...", 90)
                outputFile.writeBytes(stamped)
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
// BOTTOM SHEET UI
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelineSheet(
    onConfirm: (mode: PipelineMode, crf: Int, preset: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode   by remember { mutableStateOf(PipelineMode.ENCODE_PATCH) }
    var selectedCrf    by remember { mutableStateOf(18) }
    var selectedPreset by remember { mutableStateOf("fast") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1A2E),
        contentColor     = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "⚙️ Kythera Pipeline",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = Color(0xFF7C4DFF)
            )

            // ── Pilih Mode ────────────────────────────────────────────────
            Text("Mode Proses", fontSize = 13.sp, color = Color(0xFFAAAAAA))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PipelineMode.entries.forEach { mode ->
                    val active = selectedMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = mode }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(mode.icon, fontSize = 22.sp)
                        Column {
                            Text(
                                mode.label,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else Color(0xFFCCCCCC)
                            )
                            Text(
                                mode.desc,
                                fontSize = 11.sp,
                                color    = if (active) Color(0xFFDDCCFF) else Color(0xFF777777)
                            )
                        }
                    }
                }
            }

            // ── CRF & Preset — hanya tampil kalau mode Encode+Patch ───────
            if (selectedMode == PipelineMode.ENCODE_PATCH) {

                Text("Kualitas (CRF)", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(18, 20).forEach { crf ->
                        val active = selectedCrf == crf
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCrf = crf }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "CRF $crf",
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else Color(0xFFCCCCCC)
                                )
                                Text(
                                    if (crf == 18) "Maksimal" else "Seimbang",
                                    fontSize = 11.sp,
                                    color    = if (active) Color(0xFFDDCCFF) else Color(0xFF888888)
                                )
                            }
                        }
                    }
                }

                Text("Kecepatan Encode (Preset)", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ultrafast" to "⚡ Ultrafast", "fast" to "🚀 Fast", "medium" to "🎯 Medium")
                        .forEach { (preset, label) ->
                            val active = selectedPreset == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (active) Color(0xFF7C4DFF) else Color(0xFF2A2A3E),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedPreset = preset }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (active) Color.White else Color(0xFFCCCCCC)
                                )
                            }
                        }
                }
            }

            // ── Tombol Proses ─────────────────────────────────────────────
            Button(
                onClick = { onConfirm(selectedMode, selectedCrf, selectedPreset) },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "🚀 Proses & Upload ke TikTok",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batalkan", color = Color(0xFF888888))
            }
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
    var isProcessing        by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            showSheet  = true
        } else {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
        }
    }

    // ── Bottom Sheet ──────────────────────────────────────────────────────
    if (showSheet) {
        PipelineSheet(
            onConfirm = { mode, crf, preset ->
                showSheet = false
                val src = pendingUri ?: return@PipelineSheet
                pendingUri = null
                isProcessing = true

                scope.launch {
                    val resultUri = runPipeline(
                        context    = context,
                        sourceUri  = src,
                        mode       = mode,
                        crf        = crf,
                        preset     = preset,
                        onProgress = { msg, progress ->
                            statusMsg = msg
                            postNotif(context, "Kythera Pipeline", msg, progress)
                        }
                    )

                    isProcessing = false
                    dismissNotif(context)

                    if (resultUri != null) {
                        fileChooserCallback?.onReceiveValue(arrayOf(resultUri))
                    } else {
                        fileChooserCallback?.onReceiveValue(null)
                    }
                    fileChooserCallback = null
                    statusMsg = ""
                }
            },
            onDismiss = {
                showSheet  = false
                pendingUri = null
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
            }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────
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
                        javaScriptEnabled    = true
                        domStorageEnabled    = true
                        allowFileAccess      = true
                        mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString      = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                              "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                              "Chrome/120.0.0.0 Safari/537.36"
                        useWideViewPort      = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls  = true
                        displayZoomControls  = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
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
                            view?.evaluateJavascript("""
                                var meta = document.querySelector('meta[name="viewport"]');
                                if (meta) { meta.setAttribute('content','width=1280'); }
                                else {
                                    var m=document.createElement('meta');
                                    m.name='viewport'; m.content='width=1280';
                                    document.head.appendChild(m);
                                }
                                document.body.style.overflow='auto';
                                document.documentElement.style.overflow='auto';
                            """.trimIndent(), null)
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

        // ── Status overlay ────────────────────────────────────────────────
        if (isProcessing && statusMsg.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xDD1A1A2E), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = Color(0xFF7C4DFF)
                    )
                    Text(statusMsg, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
