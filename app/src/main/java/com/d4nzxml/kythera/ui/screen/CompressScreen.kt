package com.d4nzxml.kythera.ui.screen

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
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
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.d4nzxml.kythera.service.FfmpegService
import com.d4nzxml.kythera.service.GalleryService // 🔥 KURIR RESMI LU MASUK SINI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val InputBg = Color(0xFF1D1A31)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentGreen = Color(0xFF1DD1A1)
private val AccentOrange = Color(0xFFF39C12)

@Composable
fun CompressScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() 
    
    val ffmpegService = remember { FfmpegService(context) }
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) } 
    var progressPercent by remember { mutableIntStateOf(0) } 
    
    // 🔥 State buat Notif Premium
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isErrorToast by remember { mutableStateOf(false) }
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
    }

    var selectedTarget by remember { mutableStateOf("60%") }
    var isAudioCompression by remember { mutableStateOf(true) }
    var isRemoveMetadata by remember { mutableStateOf(false) }
    var isTwoPass by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashBg)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("Compress Video", color = TextTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kurangi ukuran file video dengan algoritma kompresi cerdas.",
                color = TextDesc, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- KOTAK UPLOAD DASHED ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSolidBg.copy(alpha = 0.5f))
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF2B4752), 
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                        )
                    }
                    .clickable { filePickerLauncher.launch("video/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedFileUri != null) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Video Siap Diccompress!", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap untuk mengganti video", color = TextDesc, fontSize = 10.sp)
                    } else {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = AccentGreen)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Drop video untuk compress", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Maksimal file 2GB per proses", color = TextDesc, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Target Kompresi", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompressionTargetCard("30%", "Light", "Kualitas hampir sama", AccentGreen, selectedTarget == "30%", Modifier.weight(1f)) { selectedTarget = "30%" }
                CompressionTargetCard("60%", "Balanced", "Recommended", AccentGreen, selectedTarget == "60%", Modifier.weight(1f)) { selectedTarget = "60%" }
                CompressionTargetCard("85%", "Aggressive", "Ukuran minimal", AccentOrange, selectedTarget == "85%", Modifier.weight(1f)) { selectedTarget = "85%" }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SwitchSettingRow("Audio Compression", "Kompres juga track audio", isAudioCompression) { isAudioCompression = it }
            SwitchSettingRow("Remove Metadata", "Hapus data EXIF dan metadata", isRemoveMetadata) { isRemoveMetadata = it }
            SwitchSettingRow("Two-Pass Encoding", "Kualitas lebih baik, proses lebih lama", isTwoPass) { isTwoPass = it }

            Spacer(modifier = Modifier.height(24.dp))

            val reductionFraction = when (selectedTarget) {
                "30%" -> 0.3f
                "60%" -> 0.6f
                "85%" -> 0.85f
                else -> 0.5f
            }

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardSolidBg).padding(20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimasi Output", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Pengurangan $selectedTarget", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(InputBg)) {
                    Box(modifier = Modifier.fillMaxWidth(reductionFraction).fillMaxHeight().clip(CircleShape).background(AccentGreen))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 MB", color = TextDesc, fontSize = 10.sp)
                    Text("$selectedTarget size reduction", color = AccentGreen.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text("Original", color = TextDesc, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TOMBOL COMPRESS ---
            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        scope.launch {
                            withContext(Dispatchers.Main) {
                                progressPercent = 0
                                isLoading = true
                            }
                            
                            val inputPath = FFmpegKitConfig.getSafParameterForRead(context, selectedFileUri)
                            val percentInt = selectedTarget.replace("%", "").toIntOrNull() ?: 60

                            // Loading pintar nunggu Service kelar
                            var isProcessing = true
                            scope.launch(Dispatchers.Main) {
                                var currentP = 0
                                val delayTime = if (isTwoPass) 400L else 200L 
                                while (isProcessing && currentP < 95) {
                                    delay(delayTime)
                                    currentP += 1
                                    progressPercent = currentP.coerceAtMost(95)
                                }
                            }

                            // 1. Eksekusi FFmpeg
                            val result = ffmpegService.compressVideo(
                                inputPath = inputPath,
                                compressPercent = percentInt,
                                compressAudio = isAudioCompression,
                                removeMetadata = isRemoveMetadata,
                                twoPass = isTwoPass,
                                onProgress = { }
                            )

                            isProcessing = false
                            
                            // 2. Oper hasil ke GalleryService
                            if (result.success) {
                                val savedToGallery = GalleryService.saveVideo(context, result.outputPath)
                                
                                withContext(Dispatchers.Main) {
                                    progressPercent = 100
                                    delay(300)
                                    isLoading = false
                                    
                                    if (savedToGallery) {
                                        toastMessage = "Selesai! Tersimpan di Galeri (Movies/Kythera)"
                                        isErrorToast = false
                                    } else {
                                        toastMessage = "Video sukses, tapi gagal masuk ke Galeri."
                                        isErrorToast = true
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    toastMessage = "Gagal Kompresi Video!"
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
                        .background(
                            Brush.horizontalGradient(
                                if (selectedFileUri != null) listOf(Color(0xFF00CEC9), AccentGreen) 
                                else listOf(Color(0xFF3B414B), Color(0xFF3B414B))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = DashBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compress Video", color = DashBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }

        // TAMPILAN LOADING OVERLAY DENGAN PERSENTASE
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progressPercent / 100f,
                            color = AccentGreen,
                            trackColor = CardSolidBg,
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "$progressPercent%", 
                            color = Color.White, 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Sedang Mengkompresi Video...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (isTwoPass) "Mode Two-Pass (Lebih Lama)" else "Mohon jangan tutup aplikasi", color = TextDesc, fontSize = 11.sp)
                }
            }
        }

        // 🔥 CUSTOM NOTIFIKASI PREMIUM
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

        LaunchedEffect(toastMessage) {
            if (toastMessage != null) {
                delay(3500)
                toastMessage = null
            }
        }
    }
}

// --- Komponen Pelengkap ---
@Composable
fun CompressionTargetCard(
    percentage: String, title: String, desc: String, 
    accentColor: Color, isSelected: Boolean, 
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.1f) else CardSolidBg)
            .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        if (isSelected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.align(Alignment.TopEnd).size(14.dp))
        }
        Column(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(percentage, color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = TextDesc, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SwitchSettingRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = TextDesc, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3498DB),
                uncheckedThumbColor = TextDesc, uncheckedTrackColor = InputBg, uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
