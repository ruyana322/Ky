package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.superresolution.RealEsrganBridge
import com.d4nzxml.kythera.superresolution.VideoUpscaleProcessor
import com.d4nzxml.kythera.service.OpenCvBridge
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun getRealPath(context: Context, uri: Uri): String? {
    return try {
        val tmp = File(context.cacheDir, "ky_in_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        tmp.absolutePath
    } catch (e: Exception) { null }
}

/** Format seconds to mm:ss */
fun Int.toTimeStr(): String = "%02d:%02d".format(this / 60, this % 60)

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var inputUriStr  by rememberSaveable { mutableStateOf<String?>(null) }
    var videoMeta    by remember { mutableStateOf<OpenCvBridge.VideoMeta?>(null) }
    var thumbnail    by remember { mutableStateOf<Bitmap?>(null) }
    var engineReady  by remember { mutableStateOf(false) }
    var useGpuAccel  by remember { mutableStateOf(true) }
    var targetResMode by remember { mutableStateOf("1080p") } // "original", "1080p", "2x"

    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var isCancelled  by remember { mutableStateOf(false) }
    var statusMsg    by remember { mutableStateOf("") }
    var progressPct  by remember { mutableStateOf(0f) }
    var doneFrames   by remember { mutableStateOf(0) }
    var totalFrames  by remember { mutableStateOf(0) }
    var processFps   by remember { mutableStateOf(0f) }
    var elapsedSec   by remember { mutableStateOf(0) }
    var startTimeMs  by remember { mutableStateOf(0L) }

    var isSuccess    by rememberSaveable { mutableStateOf(false) }
    var outputUri    by remember { mutableStateOf<Uri?>(null) }
    var errorLog     by remember { mutableStateOf<String?>(null) }

    val inputUri = inputUriStr?.let { Uri.parse(it) }

    val processor = remember {
        VideoUpscaleProcessor(scale = 2, modelName = "realesr-animevideov3", useFaceRestore = false)
    }
    val processorProgress by processor.progress.collectAsState()
    val processorFps      by processor.fps.collectAsState()

    // Animated progress for smooth bar
    val animatedProgress by animateFloatAsState(targetValue = progressPct, label = "progress")

    // Engine ready check (reloads if GPU toggle changes)
    LaunchedEffect(useGpuAccel) {
        engineReady = false
        statusMsg = "Memuat AI engine..."
        withContext(Dispatchers.IO) { 
            RealEsrganBridge.release()
            engineReady = RealEsrganBridge.loadModel(context.assets, useGpuAccel) 
        }
        statusMsg = if (engineReady) "" else "⚠️ AI engine gagal dimuat"
    }

    // Elapsed timer — updates every second while processing
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            startTimeMs = System.currentTimeMillis()
            elapsedSec  = 0
            while (isProcessing) {
                delay(1000)
                elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
            }
        }
    }

    // Sync processor → UI
    LaunchedEffect(processorProgress) {
        if (isProcessing && processorProgress > 0f) {
            progressPct = 0.05f + 0.70f * processorProgress
            processFps  = processorFps
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUriStr  = it.toString()
            isSuccess    = false; outputUri = null; errorLog = null
            videoMeta    = null; totalFrames = 0; thumbnail = null; statusMsg = ""

            scope.launch(Dispatchers.IO) {
                // Load thumbnail
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, it)
                    val bmp = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    withContext(Dispatchers.Main) { thumbnail = bmp }
                } catch (_: Exception) {}

                // Load video meta
                val path = getRealPath(context, it) ?: return@launch
                try {
                    val meta = OpenCvBridge.openVideo(path)
                    OpenCvBridge.close()
                    withContext(Dispatchers.Main) {
                        if (meta != null) {
                            videoMeta   = meta
                            statusMsg = "Mengekstrak frame video..."
                            isExtracting = true
                        } else { statusMsg = "Gagal baca metadata video" }
                    }
                    
                    if (meta != null) {
                        val safUrl = FFmpegKitConfig.getSafParameterForRead(context, it)
                        val inputFramesDir = File(context.cacheDir, "ky_input_frames")
                        
                        if (!inputFramesDir.exists()) inputFramesDir.mkdirs()
                        else inputFramesDir.listFiles()?.forEach { f -> f.delete() }
                        
                        FFmpegKit.execute("-y -i \"$safUrl\" -qscale:v 2 \"${inputFramesDir.absolutePath}/frame_%05d.jpg\"")
                        
                        val inputFiles = inputFramesDir.listFiles()?.filter { f -> f.extension == "jpg" }?.sortedBy { f -> f.name } ?: emptyList()
                        
                        withContext(Dispatchers.Main) {
                            isExtracting = false
                            totalFrames = inputFiles.size
                            statusMsg = "Siap diproses (${inputFiles.size} frame)"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { 
                        isExtracting = false
                        statusMsg = "Error: ${e.message}" 
                    }
                }
            }
        }
    }

    fun processAI() {
        val uri  = inputUri ?: return
        val meta = videoMeta ?: return
        scope.launch {
            isProcessing = true; isCancelled = false; isSuccess = false
            outputUri = null; errorLog = null
            doneFrames = 0; progressPct = 0f; processFps = 0f
            processor.resetProgress()

            val outName   = "Kythera_${System.currentTimeMillis()}.mp4"
            val outFile   = File(context.getExternalFilesDir(null), outName)
            val framesDir = File(context.cacheDir, "ky_frames")

            try {
                val path = withContext(Dispatchers.IO) { getRealPath(context, uri) }
                if (path == null) { errorLog = "Tidak bisa akses file"; isProcessing = false; return@launch }

                val openedMeta = withContext(Dispatchers.IO) { OpenCvBridge.openVideo(path) }
                val fps = openedMeta?.fps ?: 30f
                withContext(Dispatchers.IO) { try { OpenCvBridge.close() } catch (_: Exception) {} }
                
                if (openedMeta == null) { errorLog = "Gagal buka video"; isProcessing = false; return@launch }

                val inputFramesDir = File(context.cacheDir, "ky_input_frames")
                val inputFiles = inputFramesDir.listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()
                val totalF = inputFiles.size
                
                if (totalF == 0) {
                    errorLog = "Frame video belum terekstrak"; isProcessing = false; return@launch
                }

                withContext(Dispatchers.IO) {
                    if (!framesDir.exists()) framesDir.mkdirs()
                    else framesDir.listFiles()?.forEach { it.delete() }
                }

                statusMsg = "Memproses $totalF frame AI..."; progressPct = 0.05f

                withContext(Dispatchers.IO) {
                    for ((frameIdx, file) in inputFiles.withIndex()) {
                        if (isCancelled) break
                        
                        val frame = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (frame == null) continue
                        
                        val enhanced: Bitmap? = processor.processFrame(
                            bitmap = frame, frameIndex = frameIdx, totalFrames = totalF,
                            targetResMode = targetResMode
                        )
                        if (enhanced != null && !enhanced.isRecycled) {
                            FileOutputStream(File(framesDir, String.format("frame_%05d.jpg", frameIdx))).use { fos ->
                                enhanced.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                            }
                            enhanced.recycle()
                        }
                        withContext(Dispatchers.Main) { doneFrames = frameIdx + 1 }
                    }
                }

                if (isCancelled) {
                    statusMsg = "Dibatalkan"
                    withContext(Dispatchers.IO) { 
                        framesDir.deleteRecursively() 
                        inputFramesDir.deleteRecursively()
                    }
                    isProcessing = false; return@launch
                }

                progressPct = 0.77f; statusMsg = "Encoding video..."

                val encodeSession = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(
                        "-y -framerate $fps -i \"${framesDir.absolutePath}/frame_%05d.jpg\" " +
                        "-i \"$safUrl\" -map 0:v -map 1:a? " +
                        "-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k " +
                        "-movflags +faststart -shortest \"${outFile.absolutePath}\""
                    )
                }

                val finalSession = if (!ReturnCode.isSuccess(encodeSession.returnCode)) {
                    withContext(Dispatchers.IO) {
                        FFmpegKit.execute(
                            "-y -framerate $fps -i \"${framesDir.absolutePath}/frame_%05d.jpg\" " +
                            "-c:v libx264 -preset fast -crf 18 -movflags +faststart \"${outFile.absolutePath}\""
                        )
                    }
                } else encodeSession

                withContext(Dispatchers.IO) { 
                    framesDir.deleteRecursively() 
                    inputFramesDir.deleteRecursively()
                }

                if (!ReturnCode.isSuccess(finalSession.returnCode)) {
                    errorLog = finalSession.allLogsAsString
                    statusMsg = "Gagal encode video"; isProcessing = false; return@launch
                }

                progressPct = 0.95f
                val cv = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val savedUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                savedUri?.let { dest ->
                    context.contentResolver.openOutputStream(dest)?.use { os ->
                        FileInputStream(outFile).use { it.copyTo(os) }
                    }
                    outFile.delete(); outputUri = dest
                }
                progressPct = 1f; isSuccess = true
                statusMsg = "Selesai! $doneFrames frame · ${elapsedSec.toTimeStr()} ✅"

            } catch (e: Exception) {
                errorLog = "${e.javaClass.simpleName}: ${e.message}"
                statusMsg = "Error"
                withContext(Dispatchers.IO) {
                    if (framesDir.exists()) framesDir.deleteRecursively()
                    if (outFile.exists()) outFile.delete()
                }
            }
            isProcessing = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── [1] FULLWIDTH VIDEO PREVIEW (thumbnail setelah pilih video) ──────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (videoMeta?.isPortrait == true) 9f / 16f else 16f / 9f)
                .background(Color(0xFF0A0A0F))
                .clickable { if (!isProcessing) picker.launch("video/*") }
        ) {
            if (thumbnail != null) {
                // Blurred background fill
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(20.dp)
                )
                // Sharp center image
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // Dark gradient overlay at bottom for text readability
                Box(modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color(0xCC000000)
                    )))
                // Video info overlay
                videoMeta?.let { meta ->
                    Column(modifier = Modifier.align(Alignment.BottomStart)
                        .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(meta.displayRes, color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text("${meta.displayFps} · ${meta.displayDur} · ${meta.totalFrames} frames",
                            color = Color.White.copy(0.75f), fontSize = 12.sp)
                    }
                }
                // Tap to change hint
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Ganti ✎", color = Color.White, fontSize = 11.sp)
                }
            } else {
                // Empty state
                Column(modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(KColor.Accent.copy(0.15f))
                        .border(1.dp, KColor.Accent.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VideoFile, contentDescription = null,
                            tint = KColor.Accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Tap untuk pilih video", color = KColor.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("MP4, MOV, AVI", color = KColor.Text2, fontSize = 12.sp)
                }
                if (statusMsg.isNotEmpty()) {
                    Text(statusMsg, color = Color(0xFFFF9800), fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                }
            }

            // Processing overlay on top of thumbnail (plain if — no scope receiver ambiguity)
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xDD000000)),
                    contentAlignment = Alignment.Center) {

                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)) {

                        // Big circular progress
                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = KColor.Accent,
                                trackColor = Color.White.copy(0.1f),
                                strokeWidth = 6.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(animatedProgress * 100).toInt()}%",
                                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                                if (progressPct in 0.05f..0.76f)
                                    Text("$doneFrames/$totalFrames", color = Color.White.copy(0.6f), fontSize = 10.sp)
                            }
                        }

                        // Time stats row
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            // Elapsed
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(elapsedSec.toTimeStr(), color = KColor.Accent, fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold)
                                Text("Berlangsung", color = Color.White.copy(0.5f), fontSize = 10.sp)
                            }
                            // Divider dot
                            Text("·", color = Color.White.copy(0.3f), fontSize = 20.sp)
                            // Remaining
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val remaining = if (processFps > 0 && totalFrames > doneFrames && doneFrames > 0)
                                    ((totalFrames - doneFrames) / processFps).toInt() else null
                                Text(remaining?.toTimeStr() ?: "--:--", color = Color.White, fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold)
                                Text("Sisa Estimasi", color = Color.White.copy(0.5f), fontSize = 10.sp)
                            }
                            // Divider dot
                            Text("·", color = Color.White.copy(0.3f), fontSize = 20.sp)
                            // FPS
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (processFps > 0) "%.1f".format(processFps) else "--",
                                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("fps", color = Color.White.copy(0.5f), fontSize = 10.sp)
                            }
                        }

                        // Linear progress bar
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.12f))
                            Text(statusMsg, color = Color.White.copy(0.7f), fontSize = 11.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }

                        Text("⚠️ Jangan kunci layar selama proses",
                            color = Color(0xFFFF9800), fontSize = 11.sp, textAlign = TextAlign.Center)

                        OutlinedButton(onClick = { isCancelled = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Batalkan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── [2] CONTENT BELOW PREVIEW ────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Engine settings card
            AnimatedVisibility(visible = !isProcessing, enter = fadeIn(), exit = fadeOut()) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Model info
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Model AI", color = KColor.Text2, fontSize = 11.sp)
                                Text("realesr-animevideov3  ·  2x", color = KColor.Text,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (engineReady) Color(0x2269F0AE) else Color(0x22FF9800))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(if (engineReady) "✓ Ready" else "Loading...",
                                    color = if (engineReady) Color(0xFF69F0AE) else Color(0xFFFF9800),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Divider(color = Color.White.copy(0.07f))

                        // Target Resolution Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Target Resolusi Output", color = KColor.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("original" to "Asli", "1080p" to "1080p", "2x" to "Max 2x").forEach { (mode, label) ->
                                    val selected = targetResMode == mode
                                    Box(modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) KColor.Accent else Color.White.copy(0.05f))
                                        .clickable { targetResMode = mode }
                                        .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (selected) Color.White else Color.White.copy(0.7f), 
                                            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            Text(
                                text = when(targetResMode) {
                                    "original" -> "Resolusi output = resolusi video asli (Sangat Cepat)"
                                    "1080p" -> "Resolusi output maks 1080p (Seimbang)"
                                    else -> "Resolusi output 2x lipat dari asli (Sangat Lambat)"
                                },
                                color = KColor.Text2, fontSize = 11.sp
                            )
                        }

                        Divider(color = Color.White.copy(0.07f))

                        // GPU Switch
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Akselerasi GPU", color = KColor.Text, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (useGpuAccel) "Vulkan GPU aktif — lebih cepat"
                                    else "CPU only — lebih lambat ~3-5x",
                                    color = if (useGpuAccel) KColor.Accent else Color(0xFFFF9800),
                                    fontSize = 11.sp)
                            }
                            Switch(checked = useGpuAccel, onCheckedChange = { useGpuAccel = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = KColor.Accent,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(0.2f)))
                        }
                    }
                }
            }

            // Error log
            AnimatedVisibility(visible = errorLog != null && !isProcessing) {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEF5350).copy(0.5f), RoundedCornerShape(10.dp))
                    .background(Color(0x15EF5350)).padding(12.dp)
                    .verticalScroll(rememberScrollState())) {
                    Text("Error Log", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(errorLog ?: "", color = Color(0xFFFFAB91),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                }
            }

            // Status / Success result
            AnimatedVisibility(visible = isSuccess && outputUri != null) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✅ $statusMsg", color = Color(0xFF69F0AE), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KPrimaryButton(label = "Tonton", icon = Icons.Rounded.PlayArrow,
                                modifier = Modifier.weight(1f), onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(outputUri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                })
                            KPrimaryButton(label = "Reset", icon = Icons.Rounded.Refresh,
                                modifier = Modifier.weight(1f), onClick = {
                                    inputUriStr = null; videoMeta = null; thumbnail = null
                                    isSuccess = false; outputUri = null; errorLog = null; statusMsg = ""
                                    doneFrames = 0; totalFrames = 0; progressPct = 0f; processFps = 0f
                                })
                        }
                    }
                }
            }

            // Process button
            AnimatedVisibility(visible = !isProcessing && !isSuccess) {
                KPrimaryButton(
                    label = when {
                        !engineReady      -> "⏳ Engine Loading..."
                        inputUri == null  -> "Pilih Video Dulu"
                        isExtracting      -> "⏳ Mengekstrak Frame..."
                        else              -> "✨ Mulai AI Enhance"
                    },
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = inputUri != null && videoMeta != null && engineReady && !isExtracting,
                    onClick = ::processAI)
            }
        }
    }
}
