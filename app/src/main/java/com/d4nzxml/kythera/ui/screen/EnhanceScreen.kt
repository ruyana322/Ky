package com.d4nzxml.kythera.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentCyan = Color(0xFF00CEC9)
private val ButtonDarkBg = Color(0xFF2D284B)

@Composable
fun EnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 🔥 Buat jalanin proses di background
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) } // 🔥 State buat nampilin Loading

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
    }

    // 🔥 Pake Box di paling luar biar kita bisa numpuk layar loading di atas UI
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashBg)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("Kythera Upscale", color = TextTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("powered By AI", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSolidBg.copy(alpha = 0.5f))
                    .drawBehind {
                        drawRoundRect(
                            color = AccentCyan.copy(alpha = 0.4f),
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                        )
                    }
                    .clickable { filePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedFileUri != null) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Foto Siap Diproses!", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap untuk mengganti foto", color = TextDesc, fontSize = 10.sp)
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Upload Foto", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("JPG, PNG, WEBP", color = TextDesc, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        // 🔥 JALANIN LOADING PROSES
                        scope.launch {
                            isLoading = true // Layar loading muncul
                            delay(3000) // Simulasi AI mikir 3 detik (Nanti lu ganti pake kodingan FFmpeg asli)
                            isLoading = false // Layar loading ilang
                            Toast.makeText(context, "Upscale Berhasil!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Harap pilih foto terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedFileUri != null) AccentCyan else ButtonDarkBg,
                    contentColor = if (selectedFileUri != null) DashBg else TextDesc
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tingkatkan Resolusi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }

        // 🔥 TAMPILAN LOADING OVERLAY (Layar Item Transparan + Muter-muter)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    // clickable kosong ini biar user gak bisa mencet tombol di belakangnya pas lagi loading
                    .clickable(enabled = false) {}, 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        modifier = Modifier.size(50.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AI sedang memproses gambar...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
