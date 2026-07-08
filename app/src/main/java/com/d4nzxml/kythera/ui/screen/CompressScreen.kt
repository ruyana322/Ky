package com.d4nzxml.kythera.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.FfmpegService
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import com.d4nzxml.kythera.util.getRealPathFromUri
import kotlinx.coroutines.launch

@Composable
fun CompressScreen() {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val ffmpeg   = remember { FfmpegService(context) }
    val snackbar = remember { SnackbarHostState() }

    var inputPath       by remember { mutableStateOf<String?>(null) }
    var fileName        by remember { mutableStateOf<String?>(null) }
    var fileSize        by remember { mutableStateOf<String?>(null) }
    var compressPercent by remember { mutableStateOf(60) }
    var audioCompress   by remember { mutableStateOf(true) }
    var removeMetadata  by remember { mutableStateOf(false) }
    var twoPass         by remember { mutableStateOf(true) }
    var isProcessing    by remember { mutableStateOf(false) }
    var progressVal     by remember { mutableStateOf(-1.0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputPath = getRealPathFromUri(context, it)
            fileName  = it.lastPathSegment ?: "video"
            fileSize  = context.contentResolver.openFileDescriptor(it, "r")?.use { fd ->
                FfmpegService.formatSize(fd.statSize)
            }
        }
    }

    fun compress() {
        if (inputPath == null) {
            scope.launch { snackbar.showSnackbar("Pilih video dulu Kang!") }
            return
        }
        scope.launch {
            isProcessing = true
            progressVal = 0.0
            
            // OBAT BUG: Two Pass butuh nulis audio. Kalau audio dimatiin, Two Pass dipaksa mati biar ga crash
            val safeTwoPass = if (!audioCompress) false else twoPass

            val result = ffmpeg.compressVideo(
                inputPath       = inputPath!!,
                compressPercent = compressPercent,
                compressAudio   = audioCompress,
                removeMetadata  = removeMetadata,
                twoPass         = safeTwoPass,
                onProgress      = { p -> progressVal = p }
            )
            isProcessing = false
            if (result.success) {
                val saved = GalleryService.saveVideo(context, result.outputPath)
                snackbar.showSnackbar(
                    if (saved) "SUKSES! Video tersimpan di Galeri/Kythera 🎉"
                    else "Selesai, tapi gagal simpan ke galeri."
                )
            } else {
                snackbar.showSnackbar("ERROR FFmpeg: ${result.errorMessage}")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Compress Video", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Text("Kurangi ukuran file video dengan algoritma kompresi cerdas.",
                color = KColor.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            GlassCard {
                KDropZone(
                    onTap = { picker.launch("video/*") },
                    title = "Drop video untuk compress",
                    subtitle = "Maksimal file 2GB per proses",
                    icon = Icons.Rounded.Compress,
                    accentColor = KColor.Accent3,
                    selectedFileName = fileName,
                    selectedFileSize = fileSize,
                )
            }
            Spacer(Modifier.height(14.dp))

            GlassCard {
                Text("Target Kompresi", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompressOption(30, "Light", "Kualitas hampir sama", KColor.Accent3, compressPercent == 30) { compressPercent = 30 }
                    CompressOption(60, "Balanced", "Recommended", KColor.Accent3, compressPercent == 60) { compressPercent = 60 }
                    CompressOption(85, "Aggressive", "Ukuran minimal", KColor.Orange, compressPercent == 85) { compressPercent = 85 }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = KColor.Border)
                Spacer(Modifier.height(16.dp))
                KToggleRow("Audio Compression", "Kompres juga track audio", audioCompress) { audioCompress = it }
                Spacer(Modifier.height(14.dp))
                KToggleRow("Remove Metadata", "Hapus data EXIF dan metadata", removeMetadata) { removeMetadata = it }
                Spacer(Modifier.height(14.dp))
                KToggleRow("Two-Pass Encoding", "Kualitas lebih baik, proses lebih lama", twoPass) { twoPass = it }
            }
            Spacer(Modifier.height(14.dp))

            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimasi Output", color = KColor.Text, fontWeight = FontWeight.W500, fontSize = 13.sp)
                    Text("Pengurangan $compressPercent%", color = KColor.Accent3, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 1f - (compressPercent / 100f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = KColor.Accent3,
                    trackColor = KColor.Surface2,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 MB", color = KColor.Text3, fontSize = 10.sp)
                    Text("$compressPercent% size reduction", color = KColor.Accent3, fontSize = 10.sp, fontWeight = FontWeight.W500)
                    Text("Original", color = KColor.Text3, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(20.dp))

            KPrimaryButton(
                label = "Compress Video",
                icon = Icons.Rounded.Compress,
                enabled = !isProcessing,
                onClick = ::compress,
                startColor = KColor.Accent3,
                endColor = androidx.compose.ui.graphics.Color(0xFF059669),
            )
            Spacer(Modifier.height(24.dp))
        }

        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KColor.Accent3, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    val progressText = if (progressVal >= 0) "${progressVal.toInt()}%" else "Memproses..."
                    Text(progressText, color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Sabarin aja Kang...", color = KColor.Text2, fontSize = 14.sp)
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RowScope.CompressOption(percent: Int, label: String, sub: String, color: androidx.compose.ui.graphics.Color, isActive: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) color.copy(0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (isActive) 1.5.dp else 1.dp, if (isActive) color else KColor.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", color = color, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.height(4.dp))
            Text(label, color = KColor.Text3, fontSize = 10.sp, fontWeight = FontWeight.W500)
            Text(sub, color = KColor.Text3, fontSize = 9.sp)
        }
    }
}
