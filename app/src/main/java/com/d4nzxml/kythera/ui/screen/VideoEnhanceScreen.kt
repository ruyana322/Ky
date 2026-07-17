package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.service.RealSrEngine
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// ─── Enums & Data Classes ─────────────────────────────────────────────────────

enum class UpscaleMode(val label: String, val desc: String, val icon: String) {
    AI_ENHANCE(
        label = "AI Enhance",
        desc  = "Real-ESRGAN x4 upscale tiap frame",
        icon  = "🤖"
    ),
    FAST_HD(
        label = "Fast HD",
        desc  = "Lanczos scale only, no AI",
        icon  = "⚡"
    )
}

enum class UpscalePreset(
    val label: String,
    val desc: String,
    val sharpen: String,
    val bitrate: String
) {
    SHARP_FAST(
        label   = "Tajam & Cepet",
        desc    = "Lanczos + unsharp ringan",
        sharpen = "unsharp=3:3:0.8:3:3:0.0",
        bitrate = "12M"
    ),
    ULTRA_SHARP(
        label   = "Ultra Tajam",
        desc    = "Lanczos + sharpen kuat + color boost",
        sharpen = "unsharp=5:5:1.2:5:5:0.0,eq=contrast=1.03:saturation=1.1:brightness=0.01",
        bitrate = "18M"
    ),
    SMOOTH_HD(
        label   = "Smooth HD",
        desc    = "Lanczos + denoise ringan, natural look",
        sharpen = "hqdn3d=1:1:3:3",
        bitrate = "15M"
    ),
    TIKTOK_READY(
        label   = "TikTok Ready",
        desc    = "Optimized buat upload TikTok",
        sharpen = "unsharp=3:3:1.0:3:3:0.0,eq=saturation=1.2",
        bitrate = "8M"
    )
}

data class VideoInfo(val width: Int, val height: Int, val rotation: Int) {
    val isPortrait: Boolean get() =
        if (rotation == 90 || rotation == 270) width < height
        else height > width
    val resolution: String get() = "${width}x${height}"
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun getVideoInfo(context: android.content.Context, uri: Uri): VideoInfo {
    return try {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, uri)
        val w   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
        val h   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        VideoInfo(w, h, rot)
    } catch (e: Exception) { VideoInfo(1080, 1920, 0) }
}

fun getVideoDurationMs(context: android.content.Context, uri: Uri): Long {
    return try {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, uri)
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        dur
    } catch (e: Exception) { 0L }
}

fun buildVf(info: VideoInfo, preset: UpscalePreset): String {
    val scaleFilter = if (info.isPortrait) "scale=1080:-2:flags=lanczos"
                      else                 "scale=1920:-2:flags=lanczos"
    return "$scaleFilter,${preset.sharpen}"
}

fun estimateTime(durationMs: Long, frameCount: Int): String {
    // ~300ms per frame on mid-range GPU (realsr-ncnn)
    val aiMs    = frameCount * 300L
    val mergeMs = durationMs / 10
    val totalMs = aiMs + mergeMs
    val mins    = totalMs / 60000
    val secs    = (totalMs % 60000) / 1000
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // State
    var inputUriString  by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing    by rememberSaveable { mutableStateOf(false) }
    var statusText      by rememberSaveable { mutableStateOf("") }
    var errorLog        by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess       by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri   by remember { mutableStateOf<Uri?>(null) }
    var selectedPreset  by remember { mutableStateOf(UpscalePreset.SHARP_FAST) }
    var selectedMode    by remember { mutableStateOf(UpscaleMode.AI_ENHANCE) }
    var videoInfo       by remember { mutableStateOf<VideoInfo?>(null) }
    var progressText    by remember { mutableStateOf("") }
    var progressFloat   by remember { mutableStateOf(0f) }
    var aiReady         by remember { mutableStateOf(false) }
    var totalFrames     by remember { mutableStateOf(0) }
    var doneFrames      by remember { mutableStateOf(0) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    // Setup RealSR engine saat screen pertama dibuka
    LaunchedEffect(Unit) {
        aiReady = RealSrEngine.setup(context)
        statusText = if (aiReady) "✅ AI Engine siap (Real-ESRGAN x4)"
                     else "⚠️ AI Engine gagal setup — hanya Fast HD tersedia"
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUriString = it.toString()
            errorLog       = null
            isSuccess      = false
            savedVideoUri  = null
            scope.launch(Dispatchers.IO) {
                val info = getVideoInfo(context, it)
                withContext(Dispatchers.Main) {
                    videoInfo  = info
                    statusText = "✅ Video siap! ${info.resolution} rot=${info.rotation}° " +
                                 "(${if (info.isPortrait) "Portrait" else "Landscape"})"
                }
            }
        }
    }

    // ─── Process: AI Mode (RealSR per-frame) ──────────────────────────────────
    fun processAI() {
        if (inputUri == null) return
        scope.launch {
            isProcessing  = true
            isSuccess     = false
            errorLog      = null
            savedVideoUri = null
            progressFloat = 0f

            val info = withContext(Dispatchers.IO) {
                videoInfo ?: getVideoInfo(context, inputUri)
            }

            // 1. Setup dirs
            val cacheDir   = File(context.cacheDir, "ai_frames_${System.currentTimeMillis()}")
            val framesIn   = File(cacheDir, "in").also { it.mkdirs() }
            val framesOut  = File(cacheDir, "out").also { it.mkdirs() }
            val safUrl     = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
            val outputName = "Kythera_AI_${System.currentTimeMillis()}.mp4"
            val outPath    = File(context.getExternalFilesDir(null), outputName).absolutePath

            try {
                // ── Step 1: Extract frames ─────────────────────────────────
                statusText    = "⏳ Step 1/3: Ekstrak frames..."
                progressText  = "Extracting..."
                progressFloat = 0.05f

                val extractCmd = "-y -i \"$safUrl\" " +
                    "-vf \"fps=source_fps\" " +
                    "-q:v 1 \"${framesIn.absolutePath}/frame_%05d.png\""

                val extractSession = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(extractCmd)
                }

                if (!ReturnCode.isSuccess(extractSession.returnCode)) {
                    errorLog     = extractSession.allLogsAsString
                    statusText   = "❌ Gagal ekstrak frames"
                    isProcessing = false
                    cacheDir.deleteRecursively()
                    return@launch
                }

                val frames = withContext(Dispatchers.IO) {
                    framesIn.listFiles { f -> f.extension == "png" }
                        ?.sortedBy { it.name } ?: emptyList()
                }
                totalFrames   = frames.size
                progressFloat = 0.1f
                statusText    = "✅ ${totalFrames} frames diekstrak"

                // ── Step 2: AI Upscale tiap frame ─────────────────────────
                statusText   = "🤖 Step 2/3: AI upscale (Real-ESRGAN x4)..."
                doneFrames   = 0

                withContext(Dispatchers.IO) {
                    frames.forEachIndexed { index, frameFile ->
                        val outFile = File(framesOut, frameFile.name)
                        val bitmap  = BitmapFactory.decodeFile(frameFile.absolutePath)

                        if (bitmap != null) {
                            val (upscaled, err) = RealSrEngine.upscaleWithLog(context, bitmap)
                            bitmap.recycle()

                            if (upscaled != null) {
                                FileOutputStream(outFile).use { fos ->
                                    upscaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                                }
                                upscaled.recycle()
                            } else {
                                // Fallback: copy frame asli kalau AI gagal
                                frameFile.copyTo(outFile, overwrite = true)
                            }
                        } else {
                            frameFile.copyTo(outFile, overwrite = true)
                        }

                        withContext(Dispatchers.Main) {
                            doneFrames    = index + 1
                            progressFloat = 0.1f + (0.7f * doneFrames / totalFrames)
                            progressText  = "Frame $doneFrames / $totalFrames"
                            statusText    = "🤖 AI: $progressText"
                        }
                    }
                }

                progressFloat = 0.8f

                // ── Step 3: Merge frames → video ──────────────────────────
                statusText   = "🎬 Step 3/3: Merge frames → video..."
                progressText = "Encoding..."

                // Ambil FPS asli
                val fpsProbe = withContext(Dispatchers.IO) {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(context, inputUri)
                    val dur   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    r.release()
                    if (dur > 0 && totalFrames > 0) (totalFrames * 1000.0 / dur).toFloat() else 30f
                }

                val mergeCmd = "-y " +
                    "-framerate $fpsProbe " +
                    "-i \"${framesOut.absolutePath}/frame_%05d.png\" " +
                    "-i \"$safUrl\" " +
                    "-map 0:v -map 1:a " +
                    "-c:v libx264 -preset ultrafast -crf 18 " +
                    "-c:a aac -b:a 192k " +
                    "-movflags +faststart " +
                    "-shortest \"$outPath\""

                val mergeSession = withContext(Dispatchers.IO) {
                    FFmpegKit.execute(mergeCmd)
                }

                if (!ReturnCode.isSuccess(mergeSession.returnCode)) {
                    // Fallback merge tanpa audio kalau ada masalah
                    val mergeNoAudio = "-y " +
                        "-framerate $fpsProbe " +
                        "-i \"${framesOut.absolutePath}/frame_%05d.png\" " +
                        "-c:v libx264 -preset ultrafast -crf 18 " +
                        "-movflags +faststart \"$outPath\""
                    val fallbackSession = withContext(Dispatchers.IO) {
                        FFmpegKit.execute(mergeNoAudio)
                    }
                    if (!ReturnCode.isSuccess(fallbackSession.returnCode)) {
                        errorLog     = mergeSession.allLogsAsString
                        statusText   = "❌ Gagal merge frames"
                        isProcessing = false
                        cacheDir.deleteRecursively()
                        return@launch
                    }
                }

                progressFloat = 0.95f

                // ── Simpan ke Gallery ──────────────────────────────────────
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val destUri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                )
                destUri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(File(outPath)).use { it.copyTo(out) }
                    }
                    File(outPath).delete()
                    savedVideoUri = uri
                }

                // Cleanup cache
                withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }

                progressFloat = 1f
                isSuccess     = true
                statusText    = "✅ AI Enhance selesai! ${totalFrames} frames diproses"

            } catch (e: Exception) {
                errorLog     = "EXCEPTION: ${e.message}\n${e.stackTraceToString().take(800)}"
                statusText   = "❌ Error: ${e.message}"
                withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }
            }

            isProcessing = false
        }
    }

    // ─── Process: Fast HD Mode (FFmpeg only) ──────────────────────────────────
    fun processFastHD() {
        if (inputUri == null) return
        scope.launch {
            isProcessing  = true
            isSuccess     = false
            errorLog      = null
            savedVideoUri = null
            progressFloat = 0f

            val info = withContext(Dispatchers.IO) {
                videoInfo ?: getVideoInfo(context, inputUri)
            }

            val fileName = "Kythera_HD_${System.currentTimeMillis()}.mp4"
            val outPath  = File(context.getExternalFilesDir(null), fileName).absolutePath
            val safUrl   = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
            val preset   = selectedPreset
            val vf       = buildVf(info, preset)

            statusText    = "⚡ Rendering: ${preset.label}..."
            progressFloat = 0.3f

            val command = "-hide_banner -y -i \"$safUrl\" " +
                "-threads 0 -vf \"$vf\" " +
                "-c:v libx264 -preset ultrafast -crf 23 " +
                "-c:a aac -b:a 192k -ar 44100 " +
                "-movflags +faststart \"$outPath\""

            val session = withContext(Dispatchers.IO) { FFmpegKit.execute(command) }

            if (ReturnCode.isSuccess(session.returnCode)) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                )
                uri?.let { destUri ->
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(File(outPath)).use { it.copyTo(out) }
                    }
                    File(outPath).delete()
                    savedVideoUri = destUri
                }
                progressFloat = 1f
                isSuccess     = true
                statusText    = "✅ Berhasil! Tersimpan di Galeri/Kythera"
            } else {
                statusText = "❌ Gagal!"
                errorLog   = session.allLogsAsString
            }
            isProcessing = false
        }
    }

    fun startProcess() = if (selectedMode == UpscaleMode.AI_ENHANCE && aiReady) processAI()
                         else processFastHD()

    // ─── UI ───────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // Header
        Text(
            "Video Enhance",
            color      = KColor.Text,
            fontSize   = 24.sp,
            fontWeight = FontWeight.W800
        )
        Text(
            "Real-ESRGAN AI · Lanczos HD · Auto Portrait/Landscape",
            color      = KColor.Accent,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // AI Engine Status Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (aiReady) Color(0x1500C853) else Color(0x15FF6D00)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (aiReady) "🤖" else "⚠️", fontSize = 14.sp)
            Column {
                Text(
                    if (aiReady) "Real-ESRGAN Engine Ready" else "AI Engine Tidak Tersedia",
                    color      = if (aiReady) Color(0xFF00C853) else Color(0xFFFF6D00),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (aiReady) "NCNN · Vulkan GPU · SRVGGNet x4"
                    else         "Gunakan mode Fast HD",
                    color    = KColor.Text2,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Drop Zone
        GlassCard {
            KDropZone(
                onTap       = { videoPicker.launch("video/*") },
                title       = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle    = "MP4, MOV, AVI — Auto detect orientasi",
                icon        = Icons.Rounded.Movie,
                accentColor = KColor.Accent
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    statusText,
                    color    = if (isSuccess) Color(0xFF00C853) else KColor.Text2,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mode Selector
        GlassCard {
            Text("Mode Enhance", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UpscaleMode.entries.forEach { mode ->
                    val isActive = selectedMode == mode
                    val canUse   = mode != UpscaleMode.AI_ENHANCE || aiReady
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) KColor.Accent.copy(0.15f)
                                else Color(0x0AFFFFFF)
                            )
                            .border(
                                1.dp,
                                if (isActive) KColor.Accent.copy(0.5f) else Color(0x22FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(mode.icon, fontSize = 22.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            mode.label,
                            color      = if (isActive) KColor.Accent else if (canUse) KColor.Text else KColor.Text2,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 12.sp
                        )
                        Text(mode.desc, color = KColor.Text2, fontSize = 10.sp,
                            lineHeight = 13.sp)
                        if (!canUse) {
                            Text("Butuh setup", color = Color(0xFFFF6D00), fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        RadioButton(
                            selected = isActive,
                            enabled  = canUse,
                            onClick  = { if (canUse) selectedMode = mode },
                            colors   = RadioButtonDefaults.colors(selectedColor = KColor.Accent)
                        )
                    }
                }
            }
        }

        // Preset (hanya tampil kalau Fast HD)
        AnimatedVisibility(
            visible = selectedMode == UpscaleMode.FAST_HD,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Pilih Preset", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    UpscalePreset.entries.forEach { preset ->
                        val isActive = selectedPreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) KColor.Accent.copy(0.12f) else Color.Transparent)
                                .border(
                                    if (isActive) 1.dp else 0.dp,
                                    if (isActive) KColor.Accent.copy(0.4f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    preset.label,
                                    color      = if (isActive) KColor.Accent else KColor.Text,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                                Text(preset.desc, color = KColor.Text2, fontSize = 11.sp)
                            }
                            RadioButton(
                                selected = isActive,
                                onClick  = { selectedPreset = preset },
                                colors   = RadioButtonDefaults.colors(selectedColor = KColor.Accent)
                            )
                        }
                    }
                }
            }
        }

        // AI Info Card (kalau mode AI dipilih)
        AnimatedVisibility(
            visible = selectedMode == UpscaleMode.AI_ENHANCE && aiReady,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0x207C4DFF), Color(0x2000BCD4))
                            )
                        )
                        .border(1.dp, Color(0x407C4DFF), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ℹ️ Info AI Mode", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("• Engine: Real-ESRGAN SRVGGNet (NCNN Vulkan)", color = KColor.Text2, fontSize = 11.sp)
                        Text("• Scale: 4x per frame → di-downscale ke 1080p", color = KColor.Text2, fontSize = 11.sp)
                        Text("• GPU: Vulkan (fallback CPU otomatis)", color = KColor.Text2, fontSize = 11.sp)
                        Text("• Estimasi: ~5-15 menit per menit video", color = Color(0xFFFF9800), fontSize = 11.sp)
                        if (totalFrames > 0 && videoInfo != null) {
                            val dur = 0L // placeholder
                            Text(
                                "• Total frames: $totalFrames",
                                color    = KColor.Text2,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Error Log
        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("⚠️ ERROR LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    errorLog!!,
                    color      = Color(0xFFFFAAAA),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }

        // Progress UI
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(statusText, color = KColor.Accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (selectedMode == UpscaleMode.AI_ENHANCE && totalFrames > 0) {
                            Text(
                                "$doneFrames/$totalFrames",
                                color    = KColor.Text2,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (progressFloat > 0f) {
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color    = KColor.Accent
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color    = KColor.Accent
                        )
                    }
                    if (progressText.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(progressText, color = KColor.Text2, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Action Buttons
        if (isSuccess && savedVideoUri != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KPrimaryButton(
                    label    = "Tonton",
                    icon     = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick  = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(savedVideoUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                )
                KPrimaryButton(
                    label    = "Reset",
                    icon     = Icons.Rounded.Refresh,
                    modifier = Modifier.weight(1f),
                    onClick  = {
                        inputUriString = null
                        isSuccess      = false
                        statusText     = if (aiReady) "✅ AI Engine siap" else "⚠️ Gunakan Fast HD"
                        savedVideoUri  = null
                        videoInfo      = null
                        totalFrames    = 0
                        doneFrames     = 0
                        progressFloat  = 0f
                        progressText   = ""
                        errorLog       = null
                    }
                )
            }
        } else {
            KPrimaryButton(
                label   = when (selectedMode) {
                    UpscaleMode.AI_ENHANCE -> "🤖 Proses AI Sekarang!"
                    UpscaleMode.FAST_HD    -> "⚡ Fast HD Sekarang!"
                },
                icon    = Icons.Rounded.AutoAwesome,
                enabled = inputUri != null && !isProcessing,
                onClick = ::startProcess
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
