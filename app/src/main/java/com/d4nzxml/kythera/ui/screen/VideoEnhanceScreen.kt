package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.service.MnnVideoBridge
import com.d4nzxml.kythera.service.MnnVideoBridge.Accelerator
import com.d4nzxml.kythera.service.MnnVideoBridge.VideoScale
import com.d4nzxml.kythera.service.OpenCvBridge
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

// ─── Enums ────────────────────────────────────────────────────────────────────

enum class EnhanceMode(val label: String, val emoji: String, val desc: String) {
    AI_QUALITY("AI Quality", "✨", "Frame-by-frame AI enhancement"),
    FAST_HD("Fast HD", "⚡", "Instant scale, no AI")
}

enum class HdPreset(val label: String, val desc: String, val vf: String, val bitrate: String) {
    SHARP(  "Tajam",  "Lanczos + unsharp ringan", "scale=%s:flags=lanczos,unsharp=3:3:0.8:3:3:0",                                 "12M"),
    ULTRA(  "Ultra",  "Lanczos + sharpen kuat",   "scale=%s:flags=lanczos,unsharp=5:5:1.2:5:5:0,eq=contrast=1.03:saturation=1.1","18M"),
    SMOOTH( "Smooth", "Lanczos + denoise",         "scale=%s:flags=lanczos,hqdn3d=1:1:3:3",                                       "15M"),
    TIKTOK( "TikTok", "Optimized untuk upload",    "scale=%s:flags=lanczos,unsharp=3:3:1.0:3:3:0,eq=saturation=1.2",             "8M")
}

// ─── Helper: get real file path from Uri ──────────────────────────────────────
fun getRealPath(context: Context, uri: Uri): String? {
    return try {
        val cursor = context.contentResolver.query(uri, arrayOf("_data"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: run {
            // Fallback: copy ke cache biar bisa dibaca OpenCV
            val tmp = File(context.cacheDir, "ky_input_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            tmp.absolutePath
        }
    } catch (e: Exception) { null }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // State
    var inputUriStr  by rememberSaveable { mutableStateOf<String?>(null) }
    var videoMeta    by remember { mutableStateOf<OpenCvBridge.VideoMeta?>(null) }
    var engineReady  by remember { mutableStateOf(false) }
    var mode         by remember { mutableStateOf(EnhanceMode.AI_QUALITY) }
    var accelerator  by remember { mutableStateOf(Accelerator.GPU) }
    var videoScale   by remember { mutableStateOf(VideoScale.X4) }
    var hdPreset     by remember { mutableStateOf(HdPreset.SHARP) }

    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var isCancelled  by remember { mutableStateOf(false) }
    var statusMsg    by remember { mutableStateOf("") }
    var progressPct  by remember { mutableStateOf(0f) }
    var doneFrames   by remember { mutableStateOf(0) }
    var totalFrames  by remember { mutableStateOf(0) }
    var processFps   by remember { mutableStateOf(0f) }

    var isSuccess    by rememberSaveable { mutableStateOf(false) }
    var outputUri    by remember { mutableStateOf<Uri?>(null) }
    var errorLog     by remember { mutableStateOf<String?>(null) }

    val inputUri = inputUriStr?.let { Uri.parse(it) }

    // Init MNN engine
    LaunchedEffect(Unit) {
        statusMsg   = "Memuat engine..."
        engineReady = MnnVideoBridge.setup(context, VideoScale.X4)
        statusMsg   = if (engineReady) "" else "Fast HD aktif"
        if (!engineReady) mode = EnhanceMode.FAST_HD
    }

    // Switch scale
    LaunchedEffect(videoScale) {
        if (engineReady || !isProcessing) {
            engineReady = false
            statusMsg   = "Memuat model ${videoScale.label}..."
            engineReady = MnnVideoBridge.switchScale(context, videoScale)
            statusMsg   = ""
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUriStr = it.toString()
            isSuccess = false; outputUri = null; errorLog = null
            videoMeta = null; totalFrames = 0

            // Buka video via OpenCV → dapat metadata INSTAN tanpa extract
            scope.launch(Dispatchers.IO) {
                val path = getRealPath(context, it)
                if (path != null) {
                    val meta = OpenCvBridge.openVideo(path)
                    withContext(Dispatchers.Main) {
                        if (meta != null) {
                            videoMeta   = meta
                            totalFrames = meta.totalFrames
                            statusMsg   = "${meta.width}×${meta.height} · ${meta.displayFps} · ${meta.displayDur}"
                        } else {
                            statusMsg = "Gagal baca metadata video"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { statusMsg = "Tidak bisa akses file" }
                }
            }
        }
    }

    // ─── AI Process (OpenCV + MNN) ────────────────────────────────────────────
    fun processAI() {
        val uri  = inputUri ?: return
        val meta = videoMeta ?: return

        scope.launch {
            isProcessing = true; isCancelled = false; isSuccess = false
            outputUri = null; errorLog = null
            doneFrames = 0; progressPct = 0f; processFps = 0f

            val outName = "Kythera_${System.currentTimeMillis()}.mp4"
            val outFile = File(context.getExternalFilesDir(null), outName)
            // Raw output dulu dari OpenCV (tanpa audio)
            val rawOut  = File(context.cacheDir, "ky_raw_${System.currentTimeMillis()}.mp4")

            try {
                // ── Buka VideoCapture ─────────────────────────────────────
                val path = withContext(Dispatchers.IO) { getRealPath(context, uri) }
                if (path == null) {
                    errorLog = "Tidak bisa akses file video"
                    isProcessing = false; return@launch
                }

                val openedMeta = withContext(Dispatchers.IO) { OpenCvBridge.openVideo(path) }
                if (openedMeta == null) {
                    errorLog = "Gagal buka video dengan OpenCV"
                    isProcessing = false; return@launch
                }

                // Scale factor berdasarkan pilihan
                val scaleFactor = if (videoScale == VideoScale.X2) 2 else 4
                val outW = openedMeta.width  * scaleFactor
                val outH = openedMeta.height * scaleFactor

                // Target output max 1080p
                val (finalW, finalH) = if (openedMeta.isPortrait) {
                    val h = minOf(outH, 1920)
                    val w = (outW * h / outH) / 2 * 2  // even number
                    Pair(w, h)
                } else {
                    val w = minOf(outW, 1920)
                    val h = (outH * w / outW) / 2 * 2
                    Pair(w, h)
                }

                // ── Buka VideoWriter ──────────────────────────────────────
                val writerOk = withContext(Dispatchers.IO) {
                    OpenCvBridge.openWriter(rawOut.absolutePath, finalW, finalH)
                }
                if (!writerOk) {
                    errorLog = "Gagal buka video writer"
                    isProcessing = false
                    OpenCvBridge.close()
                    return@launch
                }

                statusMsg   = "Memproses..."
                progressPct = 0.05f
                val startTime = System.currentTimeMillis()

                // ── Loop frame-by-frame ───────────────────────────────────
                withContext(Dispatchers.IO) {
                    var frameIdx = 0
                    while (true) {
                        if (isCancelled) break

                        // Baca frame via OpenCV
                        val frame = OpenCvBridge.readFrame() ?: break

                        // Enhance via MNN
                        val enhanced = MnnVideoBridge.enhance(frame, accelerator)
                        frame.recycle()

                        val toWrite = enhanced ?: run {
                            // Fallback: scale manual kalau MNN gagal
                            android.graphics.Bitmap.createScaledBitmap(
                                frame, finalW, finalH, true
                            )
                        }

                        // Tulis ke output
                        OpenCvBridge.writeFrame(toWrite)
                        if (toWrite != enhanced) toWrite.recycle()
                        enhanced?.recycle()

                        frameIdx++
                        val fd = frameIdx
                        withContext(Dispatchers.Main) {
                            doneFrames  = fd
                            progressPct = 0.05f + (0.80f * fd / openedMeta.totalFrames)
                            val elapsed = System.currentTimeMillis() - startTime
                            processFps  = if (elapsed > 0) fd * 1000f / elapsed else 0f
                        }
                    }
                }

                // ── Tutup OpenCV ──────────────────────────────────────────
                withContext(Dispatchers.IO) { OpenCvBridge.close() }

                if (isCancelled) {
                    statusMsg = "Dibatalkan"
                    isProcessing = false
                    rawOut.delete()
                    return@launch
                }

                progressPct = 0.87f
                statusMsg   = "Menambahkan audio..."

                // ── Merge audio dari video asli via FFmpeg ─────────────────
                val safUrl = FFmpegKitConfig.getSafParameterForRead(context, uri)
                val mergeSession = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(
                        "-y " +
                        "-i \"${rawOut.absolutePath}\" " +
                        "-i \"$safUrl\" " +
                        "-map 0:v -map 1:a " +
                        "-c:v copy " +
                        "-c:a aac -b:a 192k " +
                        "-movflags +faststart -shortest " +
                        "\"${outFile.absolutePath}\""
                    )
                }

                // Kalau merge audio gagal, tetap simpan video tanpa audio
                val finalPath = if (ReturnCode.isSuccess(mergeSession.returnCode)) {
                    rawOut.delete()
                    outFile.absolutePath
                } else {
                    rawOut.absolutePath
                }

                progressPct = 0.95f

                // ── Simpan ke Gallery ─────────────────────────────────────
                val cv = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val savedUri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv
                )
                savedUri?.let { dest ->
                    context.contentResolver.openOutputStream(dest)?.use { os ->
                        FileInputStream(File(finalPath)).use { it.copyTo(os) }
                    }
                    File(finalPath).delete()
                    outputUri = dest
                }

                progressPct = 1f
                isSuccess   = true
                statusMsg   = "Selesai! $doneFrames frame diproses"

            } catch (e: Exception) {
                errorLog  = "${e.javaClass.simpleName}: ${e.message}"
                statusMsg = "Terjadi kesalahan"
                withContext(Dispatchers.IO) {
                    OpenCvBridge.close()
                    rawOut.delete()
                }
            }
            isProcessing = false
        }
    }

    // ─── Fast HD (FFmpeg only) ────────────────────────────────────────────────
    fun processFastHD() {
        val uri  = inputUri ?: return
        val meta = videoMeta ?: return
        scope.launch {
            isProcessing = true; isSuccess = false; outputUri = null; errorLog = null
            val outName = "Kythera_${System.currentTimeMillis()}.mp4"
            val outFile = File(context.getExternalFilesDir(null), outName)
            val safUrl  = FFmpegKitConfig.getSafParameterForRead(context, uri)
            val scale   = if (meta.isPortrait) "1080:-2" else "1920:-2"
            val vf      = hdPreset.vf.format(scale)
            statusMsg   = "Memproses..."; progressPct = 0.3f

            val session = withContext(Dispatchers.IO) {
                FFmpegKit.execute(
                    "-hide_banner -y -i \"$safUrl\" " +
                    "-vf \"$vf\" -c:v libx264 -preset ultrafast -crf 23 " +
                    "-b:v ${hdPreset.bitrate} -c:a aac -b:a 192k " +
                    "-movflags +faststart \"${outFile.absolutePath}\""
                )
            }
            if (ReturnCode.isSuccess(session.returnCode)) {
                val cv = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val dest = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                dest?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        FileInputStream(outFile).use { fis -> fis.copyTo(os) }
                    }
                    outFile.delete(); outputUri = it
                }
                progressPct = 1f; isSuccess = true; statusMsg = "Selesai!"
            } else {
                errorLog = session.allLogsAsString; statusMsg = "Gagal"
            }
            isProcessing = false
        }
    }

    fun onProcess() = if (mode == EnhanceMode.AI_QUALITY && engineReady) processAI() else processFastHD()

    // ─── UI ───────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Column {
            Text("Video Enhance", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("by D4nzxml Studio", color = KColor.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Drop Zone
        GlassCard {
            KDropZone(
                onTap       = { picker.launch("video/*") },
                title       = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle    = if (videoMeta != null)
                    "${videoMeta!!.width}×${videoMeta!!.height} · ${videoMeta!!.displayFps} · ${videoMeta!!.displayDur} · ${videoMeta!!.totalFrames} frames"
                    else "MP4, MOV, AVI",
                icon        = Icons.Rounded.Movie,
                accentColor = KColor.Accent
            )
        }

        // Mode Selector
        GlassCard {
            Text("Mode", color = KColor.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnhanceMode.entries.forEach { m ->
                    val active = mode == m
                    val canUse = m != EnhanceMode.AI_QUALITY || engineReady
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) KColor.Accent.copy(0.14f) else Color.White.copy(0.04f))
                            .border(1.dp, if (active) KColor.Accent.copy(0.5f) else Color.White.copy(0.1f),
                                RoundedCornerShape(12.dp))
                            .clickable(enabled = canUse) { mode = m }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(m.emoji, fontSize = 24.sp)
                        Text(m.label,
                            color      = if (active) KColor.Accent else if (canUse) KColor.Text else KColor.Text2,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 13.sp)
                        Text(m.desc, color = KColor.Text2, fontSize = 10.sp,
                            textAlign = TextAlign.Center, lineHeight = 13.sp)
                        if (!canUse) Text("Tidak tersedia", color = Color(0xFFFF6D00), fontSize = 9.sp)
                    }
                }
            }
        }

        // AI Settings
        AnimatedVisibility(
            visible = mode == EnhanceMode.AI_QUALITY && engineReady,
            enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()
        ) {
            GlassCard {
                // Scale 2x / 4x
                Text("Kualitas", color = KColor.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VideoScale.entries.forEach { s ->
                        val active = videoScale == s
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) KColor.Accent.copy(0.14f) else Color.White.copy(0.04f))
                                .border(1.dp, if (active) KColor.Accent.copy(0.5f) else Color.White.copy(0.1f),
                                    RoundedCornerShape(10.dp))
                                .clickable { videoScale = s }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(s.label,
                                color      = if (active) KColor.Accent else KColor.Text,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 13.sp)
                            Text(if (s == VideoScale.X2) "Lebih cepat" else "Kualitas max",
                                color = KColor.Text2, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Akselerasi
                Text("Akselerasi", color = KColor.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accelerator.entries.forEach { acc ->
                        val active = accelerator == acc
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) KColor.Accent.copy(0.12f) else Color.White.copy(0.04f))
                                .border(1.dp, if (active) KColor.Accent.copy(0.45f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(10.dp))
                                .clickable { accelerator = acc }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = active, onClick = { accelerator = acc },
                                colors = RadioButtonDefaults.colors(selectedColor = KColor.Accent))
                            Column {
                                Text(acc.label,
                                    color = if (active) KColor.Accent else KColor.Text,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp)
                                Text(acc.desc, color = KColor.Text2, fontSize = 10.sp)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = accelerator == Accelerator.CPU) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x22FF9800)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 14.sp)
                        Text("CPU mode lebih lambat ~3-5x.",
                            color = Color(0xFFFF9800), fontSize = 11.sp)
                    }
                }
            }
        }

        // Fast HD Preset
        AnimatedVisibility(
            visible = mode == EnhanceMode.FAST_HD,
            enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()
        ) {
            GlassCard {
                Text("Preset", color = KColor.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HdPreset.entries.forEach { p ->
                        val active = hdPreset == p
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) KColor.Accent.copy(0.12f) else Color.Transparent)
                                .border(if (active) 1.dp else 0.dp,
                                    if (active) KColor.Accent.copy(0.4f) else Color.Transparent,
                                    RoundedCornerShape(10.dp))
                                .clickable { hdPreset = p }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label,
                                    color = if (active) KColor.Accent else KColor.Text,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp)
                                Text(p.desc, color = KColor.Text2, fontSize = 11.sp)
                            }
                            RadioButton(selected = active, onClick = { hdPreset = p },
                                colors = RadioButtonDefaults.colors(selectedColor = KColor.Accent))
                        }
                    }
                }
            }
        }

        // Processing UI — persis Speedkythera
        AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
            GlassCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Circle counter
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (totalFrames > 0) {
                            Box(
                                modifier = Modifier.size(90.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(
                                        listOf(KColor.Accent.copy(0.3f), KColor.Accent.copy(0.1f))
                                    ))
                                    .border(2.dp, KColor.Accent.copy(0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$doneFrames\n/$totalFrames",
                                    color = Color.White, fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center, lineHeight = 20.sp)
                            }
                        } else {
                            CircularProgressIndicator(color = KColor.Accent)
                        }
                    }

                    // Info
                    videoMeta?.let { meta ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total frames: ${meta.totalFrames}", color = KColor.Text2, fontSize = 11.sp)
                            if (processFps > 0) Text("%.1f fps".format(processFps),
                                color = KColor.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Input ${meta.displayFps}", color = KColor.Text2, fontSize = 11.sp)
                            Text(videoScale.label, color = KColor.Accent, fontSize = 11.sp)
                        }
                    }

                    // Progress
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (progressPct > 0f) {
                            LinearProgressIndicator(
                                progress = { progressPct },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.1f)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.1f)
                            )
                        }
                        Text(
                            if (totalFrames > 0) "Enhancing quality...${(progressPct * 100).toInt()}%"
                            else statusMsg,
                            color = KColor.Text, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Please DON'T lock the screen or switch to other apps during processing.",
                            color = Color(0xFFEF5350), fontSize = 11.sp,
                            textAlign = TextAlign.Center, lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedButton(
                        onClick = { isCancelled = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Error Log
        AnimatedVisibility(visible = errorLog != null && !isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEF5350).copy(0.5f), RoundedCornerShape(10.dp))
                    .background(Color(0x15EF5350)).padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Log", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(errorLog ?: "", color = Color(0xFFFFAB91),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
            }
        }

        // Status
        AnimatedVisibility(visible = statusMsg.isNotEmpty() && !isProcessing) {
            Text(statusMsg,
                color = if (isSuccess) Color(0xFF69F0AE) else KColor.Text2,
                fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }

        // Buttons
        if (!isProcessing) {
            if (isSuccess && outputUri != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KPrimaryButton(
                        label = "Tonton", icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(outputUri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    )
                    KPrimaryButton(
                        label = "Reset", icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            inputUriStr = null; videoMeta = null; isSuccess = false
                            outputUri = null; errorLog = null; statusMsg = ""
                            doneFrames = 0; totalFrames = 0; progressPct = 0f; processFps = 0f
                        }
                    )
                }
            } else {
                KPrimaryButton(
                    label   = if (mode == EnhanceMode.AI_QUALITY && engineReady) "✨ Mulai Enhance" else "⚡ Fast HD",
                    icon    = Icons.Rounded.AutoAwesome,
                    enabled = inputUri != null && videoMeta != null,
                    onClick = ::onProcess
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
