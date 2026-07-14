package com.d4nzxml.kythera.ui.screen

import android.os.Environment
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) } 
    var progressPercent by remember { mutableIntStateOf(0) } // 🔥 State Baru Buat Persen (0-100)
    
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

            // --- TOMBOL COMPRESS (LOGIKA FFMPEG PERCENTAGE) ---
            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val inputPath = FFmpegKitConfig.getSafParameterForRead(context, selectedFileUri)
                                    
                                    // 🔥 Tarik Info Durasi Video Buat Ngitung Persen
                                    val mediaInfo = FFprobeKit.getMediaInformation(inputPath)
                                    val durationStr = mediaInfo?.mediaInformation?.duration
                                    val totalDurationMs = durationStr?.toFloatOrNull()?.times(1000)?.toLong() ?: 0L

                                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                    val kytheraDir = File(moviesDir, "KytheraTools")
                                    if (!kytheraDir.exists()) kytheraDir.mkdirs()
                                    
                                    val outputFile = File(kytheraDir, "Compress_${System.currentTimeMillis()}.mp4")
                                    val outputPath = outputFile.absolutePath

                                    val sharedPref = context.getSharedPreferences("KytheraPrefs", android.content.Context.MODE_PRIVATE)
                                    val audioArgs = if (isAudioCompression) {
                                        sharedPref.getString("comp_audio_compress", "-c:a aac -b:a 128k") ?: "-c:a aac -b:a 128k"
                                    } else {
                                        sharedPref.getString("comp_audio_copy", "-c:a copy") ?: "-c:a copy"
                                    }
                                    val metaArgs = if (isRemoveMetadata) {
                                        sharedPref.getString("comp_meta", "-map_metadata -1") ?: "-map_metadata -1"
                                    } else ""

                                    val crfValue = when (selectedTarget) {
                                        "30%" -> "23" 
                                        "60%" -> "28" 
                                        "85%" -> "32" 
                                        else -> "28"
                                    }
                                    val passArgs = if (isTwoPass) "-preset slow" else "-preset fast"
                                    val command = "-i \"$inputPath\" -c:v libx264 -crf $crfValue $passArgs $audioArgs $metaArgs -y \"$outputPath\""

                                    // Reset UI ke loading
                                    withContext(Dispatchers.Main) {
                                        progressPercent = 0
                                        isLoading = true
                                    }

                                    // 🔥 Eksekusi FFmpeg Async (Bisa Nyadap Persen)
                                    FFmpegKit.executeAsync(command, { session ->
                                        // Kalau udah selesai
                                        val returnCode = session.returnCode
                                        scope.launch(Dispatchers.Main) {
                                            isLoading = false
                                            if (ReturnCode.isSuccess(returnCode)) {
                                                Toast.makeText(context, "Selesai! Disimpan di Movies/KytheraTools", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Gagal Kompresi!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }, { log ->
                                        // (Log ga perlu kita pantau)
                                    }, { statistics ->
                                        // 🔥 Ngitung Persen Tiap Detik
                                        if (totalDurationMs > 0) {
                                            val timeMs = statistics.time.toFloat()
                                            val percentage = ((timeMs / totalDurationMs) * 100).toInt()
                                            
                                            scope.launch(Dispatchers.Main) {
                                                progressPercent = percentage.coerceIn(0, 100)
                                            }
                                        }
                                    })
                                    
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Pilih video terlebih dahulu!", Toast.LENGTH_SHORT).show()
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
            Spacer(modifier = Modifier.height(80.dp))
        }

        // 🔥 TAMPILAN LOADING OVERLAY DENGAN PERSENTASE
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Lingkaran Progress (Bisa muter ngikutin persen)
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progressPercent / 100f,
                            color = AccentGreen,
                            trackColor = CardSolidBg,
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp
                        )
                        // Angka Persen di tengah lingkaran
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
                    Text("Mohon jangan tutup aplikasi", color = TextDesc, fontSize = 11.sp)
                }
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
            Text(desc, color = TextDesc, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
