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
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentCyan = Color(0xFF00CEC9)
private val AccentPurple = Color(0xFF8A2BE2)
private val InputBg = Color(0xFF1D1A31)

@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) } 
    var progressPercent by remember { mutableIntStateOf(0) } // 🔥 State buat Persen

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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap untuk mengganti video", color = TextDesc, fontSize = 10.sp)
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Drop video atau klik upload", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("MP4, AVI, MKV, MOV, WEBM, GIF", color = TextDesc, fontSize = 10.sp)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SettingLabel("CRF Value (Semakin kecil semakin HD)")
                        Text(crfValue.toInt().toString(), color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = crfValue, onValueChange = { crfValue = it }, valueRange = 0f..51f,
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = InputBg)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                SettingLabel("Encoder Preset")
                CustomDropdown(selectedPreset, listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow")) { selectedPreset = it }
                Spacer(modifier = Modifier.height(20.dp))
                SettingLabel("Resolution")
                CustomDropdown(selectedResolution, listOf("Original", "1080p", "720p", "480p", "360p")) { selectedResolution = it }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TOMBOL KONVERSI (FFMPEG LOGIC) ---
            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val inputPath = FFmpegKitConfig.getSafParameterForRead(context, selectedFileUri)
                                    
                                    // 🔥 Ambil durasi buat ngitung persen
                                    val mediaInfo = FFprobeKit.getMediaInformation(inputPath)
                                    val durationStr = mediaInfo?.mediaInformation?.duration
                                    val totalDurationMs = durationStr?.toFloatOrNull()?.times(1000)?.toLong() ?: 0L

                                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                    val kytheraDir = File(moviesDir, "KytheraTools")
                                    if (!kytheraDir.exists()) kytheraDir.mkdirs()
                                    
                                    // Bikin nama file sesuai format yang dipilih user
                                    val ext = selectedFormat.lowercase()
                                    val outputFile = File(kytheraDir, "Converter_${System.currentTimeMillis()}.$ext")
                                    val outputPath = outputFile.absolutePath

                                    // Mapping Codec Video
                                    val vCodec = when (selectedCodec) {
                                        "H.264 (AVC) - Compatible" -> "libx264"
                                        "H.265 (HEVC) - High Compression" -> "libx265"
                                        "VP9" -> "libvpx-vp9"
                                        "AV1" -> "libaom-av1"
                                        else -> "libx264"
                                    }

                                    // Mapping Kualitas & Resolusi
                                    val rateControl = if (isCrfMode) "-crf ${crfValue.toInt()}" else "-b:v 3M"
                                    val resFilter = when (selectedResolution) {
                                        "1080p" -> "-vf scale=-2:1080"
                                        "720p" -> "-vf scale=-2:720"
                                        "480p" -> "-vf scale=-2:480"
                                        "360p" -> "-vf scale=-2:360"
                                        else -> ""
                                    }

                                    // Tarik racikan global dari brankas (Gist)
                                    val sharedPref = context.getSharedPreferences("KytheraPrefs", android.content.Context.MODE_PRIVATE)
                                    val globalArgs = sharedPref.getString("conv_global", "-movflags +faststart") ?: "-movflags +faststart"
                                    val audioArgs = sharedPref.getString("conv_audio", "-c:a aac -b:a 192k") ?: "-c:a aac -b:a 192k"

                                    // Susun Command FFmpeg!
                                    val command = "-i \"$inputPath\" -c:v $vCodec $rateControl -preset $selectedPreset $resFilter $audioArgs $globalArgs -y \"$outputPath\""

                                    // Mulai Loading
                                    withContext(Dispatchers.Main) {
                                        progressPercent = 0
                                        isLoading = true
                                    }

                                    // Eksekusi Async FFmpeg
                                    FFmpegKit.executeAsync(command, { session ->
                                        val returnCode = session.returnCode
                                        scope.launch(Dispatchers.Main) {
                                            isLoading = false
                                            if (ReturnCode.isSuccess(returnCode)) {
                                                Toast.makeText(context, "Selesai! Disimpan di Movies/KytheraTools", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Gagal Mengkonversi Video!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }, { log -> 
                                        // Abaikan log teks
                                    }, { statistics ->
                                        // 🔥 Update progress persentase realtime
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
                                if (selectedFileUri != null) listOf(Color(0xFF4A3E85), Color(0xFF6C5CE7))
                                else listOf(Color(0xFF3B414B), Color(0xFF3B414B))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Konversi Sekarang", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progressPercent / 100f,
                            color = AccentPurple, // 🔥 Lingkaran warna Ungu
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
                    Text("Sedang Mengkonversi Video...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mohon jangan tutup aplikasi", color = TextDesc, fontSize = 11.sp)
                }
            }
        }
    }
}

// --- Komponen Pelengkap UI ---
@Composable
fun StepBadge(number: String, color: Color) {
    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Text(number, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun SettingLabel(text: String) {
    Text(text, color = TextDesc, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
}
@Composable
fun FormatButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentCyan.copy(alpha = 0.1f) else InputBg)
            .border(1.dp, if (isSelected) AccentCyan else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (isSelected) AccentCyan else TextDesc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}
@Composable
fun CustomDropdown(value: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                .background(InputBg).clickable { expanded = true }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(value, color = TextTitle, fontSize = 13.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = TextDesc)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(CardSolidBg)) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption, color = TextTitle, fontSize = 13.sp) },
                    onClick = { onValueChange(selectionOption); expanded = false }
                )
            }
        }
    }
}
