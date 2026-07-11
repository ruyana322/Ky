package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

enum class UpscalePreset(
    val label: String,
    val desc: String,
    val sharpen: String,
    val bitrate: String
) {
    SHARP_FAST(
        label   = "Tajam & Cepet",
        desc    = "Lanczos + unsharp ringan, GPU encode",
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

// Deteksi orientasi video via MediaMetadataRetriever
data class VideoInfo(val width: Int, val height: Int, val rotation: Int) {
    val isPortrait: Boolean get() {
        // Kalau rotation 90 atau 270, width/height kebalik
        return if (rotation == 90 || rotation == 270) width < height
        else height > width
    }
}

fun getVideoInfo(context: android.content.Context, uri: Uri): VideoInfo {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val w   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
        val h   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        retriever.release()
        VideoInfo(w, h, rot)
    } catch (e: Exception) {
        VideoInfo(1080, 1920, 0)
    }
}

// Build vf filter yang aman sesuai orientasi
fun buildVf(info: VideoInfo, preset: UpscalePreset): String {
    // Scale target:
    // Portrait  → lebar max 1080 (bukan 1920!)
    // Landscape → lebar max 1920
    val scaleFilter = if (info.isPortrait) {
        "scale=1080:-2:flags=lanczos"   // portrait: max width 1080
    } else {
        "scale=1920:-2:flags=lanczos"   // landscape: max width 1920
    }
    return "$scaleFilter,${preset.sharpen}"
}

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var inputUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing   by rememberSaveable { mutableStateOf(false) }
    var statusText     by rememberSaveable { mutableStateOf("") }
    var errorLog       by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess      by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri  by remember { mutableStateOf<Uri?>(null) }
    var selectedPreset by remember { mutableStateOf(UpscalePreset.SHARP_FAST) }
    var videoInfo      by remember { mutableStateOf<VideoInfo?>(null) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            errorLog       = null
            isSuccess      = false
            savedVideoUri  = null
            // Deteksi orientasi saat video dipilih
            scope.launch(Dispatchers.IO) {
                val info = getVideoInfo(context, it)
                withContext(Dispatchers.Main) {
                    videoInfo  = info
                    statusText = "✅ Video siap! ${info.width}x${info.height} rot=${info.rotation}° " +
                                 "(${if (info.isPortrait) "Portrait" else "Landscape"})"
                }
            }
        }
    }

    fun processVideo() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true
            isSuccess    = false
            errorLog     = null
            savedVideoUri = null
            statusText   = "Deteksi orientasi video..."

            val info = withContext(Dispatchers.IO) {
                videoInfo ?: getVideoInfo(context, inputUri)
            }

            val fileName = "Kythera_HD_${System.currentTimeMillis()}.mp4"
            val outPath  = File(context.getExternalFilesDir(null), fileName).absolutePath
            val safUrl   = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
            val preset   = selectedPreset
            val vf       = buildVf(info, preset)

            statusText = "Rendering: ${preset.label} (${if (info.isPortrait) "Portrait" else "Landscape"})..."

            val bufSize = "${preset.bitrate.replace("M", "").toInt() * 10}M"

            val command = buildString {
    append("-hide_banner ")
    append("-i \"$safUrl\" ")
    append("-threads 0 ")
    append("-vf \"$vf\" ")
    append("-c:v libx264 ")        // ← ganti dari h264_mediacodec
    append("-preset ultrafast ")   // ← cepet tapi file agak besar
    append("-crf 23 ")             // ← kualitas (18=HQ, 23=default, 28=kecil)
    append("-c:a aac ")
    append("-b:a 192k ")
    append("-ar 44100 ")
    append("-movflags +faststart ")
    append("\"$outPath\"")
}

            withContext(Dispatchers.IO) {
                val session = FFmpegKit.execute(command)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            FileInputStream(File(outPath)).use { it.copyTo(out) }
                        }
                        File(outPath).delete()
                        savedVideoUri = destUri
                    }
                    isSuccess  = true
                    statusText = "✅ Berhasil! Tersimpan di Galeri/Kythera"
                } else {
                    statusText = "❌ Gagal!"
                    errorLog   = session.allLogsAsString
                }
            }
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Video Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        Text("Lanczos GPU — Auto Portrait/Landscape", color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            KDropZone(
                onTap       = { videoPicker.launch("video/*") },
                title       = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle    = "MP4, MOV, AVI — Auto detect orientasi",
                icon        = Icons.Rounded.Movie,
                accentColor = KColor.Accent
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = if (isSuccess) KColor.Accent else KColor.Text2, fontSize = 13.sp)
            }
        }

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
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) KColor.Accent.copy(0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.label,
                            color      = if (isActive) KColor.Accent else KColor.Text,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 13.sp)
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

        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("⚠️ ERROR LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
        }

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
                    Text("⏳ $statusText", color = KColor.Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color    = KColor.Accent
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

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
                        statusText     = ""
                        savedVideoUri  = null
                        videoInfo      = null
                    }
                )
            }
        } else {
            KPrimaryButton(
                label   = "Upscale Sekarang!",
                icon    = Icons.Rounded.AutoAwesome,
                enabled = inputUri != null && !isProcessing,
                onClick = ::processVideo
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
