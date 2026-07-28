package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import com.d4nzxml.kythera.superresolution.RealEsrganBridge
import com.d4nzxml.kythera.superresolution.VideoUpscaleProcessor
import com.d4nzxml.kythera.service.OpenCvBridge
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
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

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var inputUriStr  by rememberSaveable { mutableStateOf<String?>(null) }
    var videoMeta    by remember { mutableStateOf<OpenCvBridge.VideoMeta?>(null) }
    var engineReady  by remember { mutableStateOf(false) }

    // GPU toggle — true = Vulkan GPU aktif, false = CPU only
    var useGpuAccel  by remember { mutableStateOf(true) }

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

    // Processor — fixed x2, model "realesr-animevideov3"
    val processor = remember {
        VideoUpscaleProcessor(scale = 2, modelName = "realesr-animevideov3", useFaceRestore = false)
    }
    val processorProgress by processor.progress.collectAsState()
    val processorFps      by processor.fps.collectAsState()

    // Engine ready check — preloaded in MainActivity
    LaunchedEffect(Unit) {
        engineReady = RealEsrganBridge.isReady()
        if (!engineReady) {
            statusMsg = "Memuat AI engine..."
            withContext(Dispatchers.IO) { engineReady = RealEsrganBridge.loadModel(context.assets) }
            statusMsg = if (engineReady) "" else "⚠️ AI engine gagal dimuat"
        }
    }

    // Sync processor StateFlow → UI progress
    LaunchedEffect(processorProgress) {
        if (isProcessing && processorProgress > 0f) {
            progressPct = 0.05f + (0.70f * processorProgress)
            processFps  = processorFps
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUriStr = it.toString()
            isSuccess = false; outputUri = null; errorLog = null
            videoMeta = null; totalFrames = 0

            scope.launch(Dispatchers.IO) {
                val path = getRealPath(context, it)
                if (path != null) {
                    try {
                        val meta = OpenCvBridge.openVideo(path)
                        withContext(Dispatchers.Main) {
                            if (meta != null) {
                                videoMeta   = meta
                                totalFrames = meta.totalFrames
                                statusMsg   = "${meta.displayRes} · ${meta.displayFps} · ${meta.displayDur} · ${meta.totalFrames} frames"
                            } else { statusMsg = "Gagal baca metadata" }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { statusMsg = "Error: ${e.message}" }
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

                // Close any video still open from the picker meta-read
                withContext(Dispatchers.IO) { try { OpenCvBridge.close() } catch (_: Exception) {} }

                val openedMeta = withContext(Dispatchers.IO) { OpenCvBridge.openVideo(path) }
                if (openedMeta == null) { errorLog = "Gagal buka video"; isProcessing = false; return@launch }

                statusMsg = "Memproses frames AI..."; progressPct = 0.05f

                withContext(Dispatchers.IO) {
                    if (!framesDir.exists()) framesDir.mkdirs()
                    else framesDir.listFiles()?.forEach { it.delete() }
                }

                var frameIdx = 0
                val totalF   = openedMeta.totalFrames

                withContext(Dispatchers.IO) {
                    while (true) {
                        if (isCancelled) break
                        val frame: Bitmap = OpenCvBridge.readFrame() ?: break

                        val enhanced: Bitmap? = processor.processFrame(
                            bitmap = frame, frameIndex = frameIdx, totalFrames = totalF)

                        if (enhanced != null && !enhanced.isRecycled) {
                            FileOutputStream(File(framesDir, String.format("frame_%05d.jpg", frameIdx))).use { fos ->
                                enhanced.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                            }
                            enhanced.recycle()
                        }
                        frameIdx++
                        withContext(Dispatchers.Main) { doneFrames = frameIdx }
                    }
                    OpenCvBridge.close()
                }

                if (isCancelled) {
                    statusMsg = "Dibatalkan"
                    withContext(Dispatchers.IO) { framesDir.deleteRecursively() }
                    isProcessing = false; return@launch
                }

                progressPct = 0.77f; statusMsg = "Encoding video..."

                val safUrl = FFmpegKitConfig.getSafParameterForRead(context, uri)
                val fps    = openedMeta.fps

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

                withContext(Dispatchers.IO) { framesDir.deleteRecursively() }

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
                statusMsg = "Selesai! $doneFrames frame ✅"

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

    // ─── UI ──────────────────────────────────────────────────────────────────
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
            Text("Real-ESRGAN · AI Upscale 2x", color = KColor.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // ── Video Picker ──────────────────────────────────────────────────────
        GlassCard {
            KDropZone(
                onTap = { picker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle = if (videoMeta != null)
                    "${videoMeta!!.displayRes} · ${videoMeta!!.displayFps} · ${videoMeta!!.displayDur} · ${videoMeta!!.totalFrames} frames"
                else "MP4, MOV, AVI",
                icon = Icons.Rounded.Movie, accentColor = KColor.Accent
            )
        }

        // ── Video Preview (setelah selesai) ───────────────────────────────────
        AnimatedVisibility(visible = isSuccess && outputUri != null,
            enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preview Hasil", color = KColor.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.PlayCircleFilled, contentDescription = null,
                                tint = KColor.Accent, modifier = Modifier.size(52.dp))
                            Text("Tap untuk putar video", color = KColor.Text2, fontSize = 12.sp)
                        }
                        Box(modifier = Modifier.fillMaxSize().clickable {
                            outputUri?.let {
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(it, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }
                        })
                    }
                    Text("✅ $statusMsg", color = Color(0xFF69F0AE), fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // ── Engine Settings ───────────────────────────────────────────────────
        AnimatedVisibility(visible = !isProcessing,
            enter = fadeIn(), exit = fadeOut()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Model info row
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Model AI", color = KColor.Text2, fontSize = 11.sp)
                            Text("realesr-animevideov3  ·  2x", color = KColor.Text, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (engineReady) Color(0x2269F0AE) else Color(0x22FF5252))
                            .padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(if (engineReady) "Ready ✓" else "Loading...",
                                color = if (engineReady) Color(0xFF69F0AE) else Color(0xFFFF5252),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = Color.White.copy(0.07f))

                    // GPU Acceleration Switch
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
                        Switch(
                            checked = useGpuAccel,
                            onCheckedChange = { useGpuAccel = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KColor.Accent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(0.2f)
                            )
                        )
                    }
                }
            }
        }

        // ── Progress Panel ────────────────────────────────────────────────────
        AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
            GlassCard {
                Column(modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    // Circular progress with percentage
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0x15FFFFFF)),
                        contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(100.dp).clip(CircleShape)
                            .background(Brush.radialGradient(
                                listOf(KColor.Accent.copy(0.25f), KColor.Accent.copy(0.08f))))
                            .border(2.dp, KColor.Accent.copy(0.6f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val pct = (progressPct * 100).toInt()
                                Text("$pct%", color = Color.White, fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold)
                                if (totalFrames > 0)
                                    Text("$doneFrames / $totalFrames", color = Color.White.copy(0.7f), fontSize = 10.sp)
                            }
                        }
                    }

                    // Stats row
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (processFps > 0) "%.1f fps".format(processFps) else "—",
                                color = KColor.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Kecepatan", color = KColor.Text2, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$doneFrames", color = KColor.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Frame Done", color = KColor.Text2, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val remaining = if (processFps > 0 && totalFrames > doneFrames)
                                ((totalFrames - doneFrames) / processFps).toInt() else -1
                            Text(if (remaining > 0) "${remaining}s" else "—",
                                color = KColor.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Estimasi", color = KColor.Text2, fontSize = 10.sp)
                        }
                    }

                    // Linear progress bar
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (progressPct > 0f) {
                            LinearProgressIndicator(progress = { progressPct },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.1f))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.1f))
                        }
                        Text(statusMsg, color = KColor.Text2, fontSize = 12.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text("⚠️ Jangan kunci layar atau keluar saat proses berlangsung",
                            color = Color(0xFFFF9800), fontSize = 11.sp,
                            textAlign = TextAlign.Center, lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth())
                    }

                    OutlinedButton(onClick = { isCancelled = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Batalkan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Error Log ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = errorLog != null && !isProcessing) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
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

        // ── Status text ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = statusMsg.isNotEmpty() && !isProcessing && !isSuccess) {
            Text(statusMsg, color = KColor.Text2, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        // ── Action Buttons ────────────────────────────────────────────────────
        if (!isProcessing) {
            if (isSuccess && outputUri != null) {
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
                            inputUriStr = null; videoMeta = null; isSuccess = false
                            outputUri = null; errorLog = null; statusMsg = ""
                            doneFrames = 0; totalFrames = 0; progressPct = 0f; processFps = 0f
                        })
                }
            } else {
                KPrimaryButton(
                    label = if (engineReady) "✨ Mulai AI Enhance" else "⏳ Engine Loading...",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = inputUri != null && videoMeta != null && engineReady,
                    onClick = ::processAI)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
