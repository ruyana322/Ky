package com.d4nzxml.kythera.ui.screen

import android.content.Context
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.FfmpegService
import com.d4nzxml.kythera.service.GalleryService // 🔥 KURIR RESMI LU MASUK SINI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentCyan = Color(0xFF00CEC9)
private val AccentPurple = Color(0xFF8A2BE2)
private val InputBg = Color(0xFF1D1A31)

// 🔥 Fungsi Ngakalin Error SAF & Moov Atom (Buat File Input)
suspend fun copyUriToTempFile(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val tempFile = File(context.cacheDir, "temp_ffmpeg_in_${System.currentTimeMillis()}.mp4")
        FileOutputStream(tempFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ffmpegService = remember { FfmpegService(context) }
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) } 
    var isPreparing by remember { mutableStateOf(false) } 
    var progressPercent by remember { mutableIntStateOf(0) } 
    
    // 🔥 State buat Notif Premium
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isErrorToast by remember { mutableStateOf(false) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
    }

    var selectedFormat by remember { mutableStateOf("MP4") }
    var selectedCodec by remember { mutableStateOf("H.264 (AVC) - Compatible") }
    var isCrfMode by remember { mutableStateOf(true) }
    var crfValue by remember { mutableStateOf(23f) }
    var selectedPreset by remember { mutableStateOf("medium") }
    var selectedResolution by remember { mutableStateOf("Original") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashBg)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text(
                text = "Konversi video ke berbagai format dengan kontrol kualitas penuh.",
                color = TextDesc, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(number = "1", color = AccentCyan)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Input Video", color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // --- KOTAK UPLOAD DASHED ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSolidBg.copy(alpha = 0.5f))
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF4A3E85),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                        )
                    }
                    .clickable { filePickerLauncher.launch("video/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedFileUri != null) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Video Siap Dikonversi!", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Drop video atau klik upload", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(number = "2", color = AccentPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Output Settings", color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardSolidBg).padding(20.dp)) {
                SettingLabel("Format Output")
                val formats = listOf("MP4", "MKV", "AVI", "WEBM", "MOV", "GIF")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.take(3).forEach { format -> FormatButton(format, selectedFormat == format, Modifier.weight(1f)) { selectedFormat = format } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.takeLast(3).forEach { format -> FormatButton(format, selectedFormat == format, Modifier.weight(1f)) { selectedFormat = format } }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                SettingLabel("Codec")
                CustomDropdown(selectedCodec, listOf("H.264 (AVC) - Compatible", "H.265 (HEVC) - High Compression", "VP9", "AV1")) { selectedCodec = it }

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(InputBg)) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                            .background(if (isCrfMode) CardSolidBg else Color.Transparent)
                            .border(if (isCrfMode) 1.dp else 0.dp, if (isCrfMode) AccentCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { isCrfMode = true },
                        contentAlignment = Alignment.Center
                    ) { Text("CRF", color = if (isCrfMode) AccentCyan else TextDesc, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                            .background(if (!isCrfMode) CardSolidBg else Color.Transparent)
                            .border(if (!isCrfMode) 1.dp else 0.dp, if (!isCrfMode) AccentCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { isCrfMode = false },
                        contentAlignment = Alignment.Center
                    ) { Text("CBR", color = if (!isCrfMode) AccentCyan else TextDesc, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }

                Spacer(modifier = Modifier.height(20.dp))
                if (isCrfMode) {
                    SettingLabel("CRF Value (${crfValue.toInt()})")
                    Slider(
                        value = crfValue, onValueChange = { crfValue = it }, valueRange = 0f..51f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = InputBg)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                SettingLabel("Encoder Preset")
                CustomDropdown(selectedPreset, listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow")) { selectedPreset = it }
                Spacer(modifier = Modifier.height(20.dp))
                SettingLabel("Resolution")
                CustomDropdown(selectedResolution, listOf("Original", "1080p", "720p", "480p", "360p")) { selectedResolution = it }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TOMBOL KONVERSI ---
            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        scope.launch {
                            // 1. Loading Menyiapkan File
                            withContext(Dispatchers.Main) {
                                isPreparing = true
                                isLoading = true
                            }
                            
                            // 2. Ngopi URI ke Cache biar anti error SAF
                            val inputPath = copyUriToTempFile(context, selectedFileUri!!)
                            
                            withContext(Dispatchers.Main) {
                                isPreparing = false
                                progressPercent = 0
                            }

                            if (inputPath == null) {
                                toastMessage = "Gagal membaca file dari penyimpanan!"
                                isErrorToast = true
                                isLoading = false
                                return@launch
                            }

                            val ext = selectedFormat.lowercase()
                            val vCodec = when {
                                ext == "gif" -> "gif"
                                ext == "webm" && selectedCodec.contains("H.26") -> "libvpx-vp9"
                                else -> when (selectedCodec) {
                                    "H.264 (AVC) - Compatible" -> "libx264"
                                    "H.265 (HEVC) - High Compression" -> "libx265"
                                    "VP9" -> "libvpx-vp9"
                                    "AV1" -> "libaom-av1"
                                    else -> "libx264"
                                }
                            }
                            val modeString = if (isCrfMode) "CRF" else "CBR"
                            
                            // 3. Eksekusi Engine FFMPEG Asli
                            val result = ffmpegService.convertVideoPro(
                                inputPath = inputPath,
                                targetFormat = ext,
                                codec = vCodec,
                                resolution = if (selectedResolution == "Original") "original" else selectedResolution.replace("p", ""),
                                mode = modeString,
                                crf = crfValue.toInt(),
                                bitrateM = 3, 
                                preset = selectedPreset,
                                onProgress = { percent ->
                                    scope.launch(Dispatchers.Main) { progressPercent = percent }
                                }
                            )

                            // 4. Hapus File Temp Input
                            File(inputPath).delete()

                            // 5. Panggil GalleryService buat nyimpen hasil Output ke Galeri
                            if (result.success) {
                                val savedToGallery = GalleryService.saveVideo(context, result.outputPath)
                                
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    if (savedToGallery) {
                                        toastMessage = "Selesai! Tersimpan di Galeri (Movies/Kythera)"
                                        isErrorToast = false
                                    } else {
                                        toastMessage = "Selesai, tapi gagal masuk ke Galeri."
                                        isErrorToast = true
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    toastMessage = "Gagal Mengkonversi Video!"
                                    isErrorToast = true
                                }
                            }
                        }
                    } else {
                        toastMessage = "Pilih video terlebih dahulu!"
                        isErrorToast = true
                    }
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.horizontalGradient(if (selectedFileUri != null) listOf(Color(0xFF4A3E85), Color(0xFF6C5CE7)) else listOf(Color(0xFF3B414B), Color(0xFF3B414B))))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Konversi Sekarang", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }

        // 🔥 OVERLAY LOADING 
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(enabled = false) {}, 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isPreparing) {
                        CircularProgressIndicator(color = AccentPurple, modifier = Modifier.size(60.dp), strokeWidth = 5.dp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Menyiapkan File...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = progressPercent / 100f, color = AccentPurple, trackColor = CardSolidBg, modifier = Modifier.size(80.dp), strokeWidth = 6.dp)
                            Text("$progressPercent%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Mengkonversi...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // 🔥 CUSTOM NOTIFIKASI PREMIUM ALA COMPOSE
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isErrorToast) Color(0xFFE74C3C).copy(alpha = 0.95f) else Color(0xFF27AE60).copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(toastMessage ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }

        // Timer buat ngilangin Notif otomatis
        LaunchedEffect(toastMessage) {
            if (toastMessage != null) {
                delay(3500) // Ilang dalam 3.5 detik
                toastMessage = null
            }
        }
    }
}

// --- Komponen Pelengkap UI ---
@Composable
fun StepBadge(number: String, color: Color) { Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text(number, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
@Composable
fun SettingLabel(text: String) { Text(text, color = TextDesc, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
@Composable
fun FormatButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) { Box(modifier = modifier.height(40.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) AccentCyan.copy(alpha = 0.1f) else InputBg).border(1.dp, if (isSelected) AccentCyan else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(text, color = if (isSelected) AccentCyan else TextDesc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
@Composable
fun CustomDropdown(value: String, options: List<String>, onValueChange: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(InputBg).clickable { expanded = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(value, color = TextTitle, fontSize = 13.sp); Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = TextDesc) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(CardSolidBg)) { options.forEach { selectionOption -> DropdownMenuItem(text = { Text(selectionOption, color = TextTitle, fontSize = 13.sp) }, onClick = { onValueChange(selectionOption); expanded = false }) } } } }
