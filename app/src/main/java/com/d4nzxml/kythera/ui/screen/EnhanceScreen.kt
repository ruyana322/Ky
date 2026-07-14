package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
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
import com.d4nzxml.kythera.service.RealSrEngine // 🔥 ENGINE DEWA LU MASUK SINI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// --- Palette Warna ---
private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentCyan = Color(0xFF00CEC9)
private val ButtonDarkBg = Color(0xFF2D284B)

@Composable
fun EnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) } 

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashBg)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            // --- HEADER ---
            Text("Kythera Upscale", color = TextTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("powered By AI", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            // --- KOTAK UPLOAD DASHED ---
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

            // --- TOMBOL EKSEKUSI REAL-SR ---
            Button(
                onClick = { 
                    if (selectedFileUri != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    // 1. Munculin UI Loading
                                    withContext(Dispatchers.Main) {
                                        progressPercent = 0
                                        isLoading = true
                                    }

                                    // 2. Baca URI jadi Bitmap (Format yang dibutuhin engine lu)
                                    val inputStream = context.contentResolver.openInputStream(selectedFileUri!!)
                                    val inputBitmap = BitmapFactory.decodeStream(inputStream)
                                    inputStream?.close()

                                    if (inputBitmap == null) {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            Toast.makeText(context, "Gagal membaca foto!", Toast.LENGTH_SHORT).show()
                                        }
                                        return@withContext
                                    }

                                    // 3. Animasi Loading Pintar (Jalan pelan ke 95% sambil nunggu NCNN mikir)
                                    var isProcessing = true
                                    scope.launch(Dispatchers.Main) {
                                        var currentP = 0
                                        while (isProcessing && currentP < 95) {
                                            delay(500) // Ngisi pelan-pelan karena NCNN agak lama
                                            currentP += 1
                                            progressPercent = currentP.coerceAtMost(95)
                                        }
                                    }

                                    // 4. PANGGIL ENGINE LU!
                                    // Setup ekstrak aset dulu (kalo belum ada)
                                    RealSrEngine.setup(context) 
                                    
                                    // Eksekusi NCNN Vulkan
                                    val (resultBitmap, errorLog) = RealSrEngine.upscaleWithLog(context, inputBitmap)

                                    // Berhentiin animasi muter
                                    isProcessing = false 

                                    // 5. Cek Hasilnya
                                    if (resultBitmap != null) {
                                        // Siapin Folder Output ke Galeri
                                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                        val kytheraDir = File(picturesDir, "KytheraTools")
                                        if (!kytheraDir.exists()) kytheraDir.mkdirs()
                                        
                                        val outputFile = File(kytheraDir, "RealSR_${System.currentTimeMillis()}.png")

                                        // Simpan Bitmap hasil AI ke file
                                        FileOutputStream(outputFile).use { out ->
                                            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }

                                        withContext(Dispatchers.Main) {
                                            progressPercent = 100
                                            delay(400) // Tahan dikit angka 100%
                                            isLoading = false
                                            Toast.makeText(context, "Upscale Sukses! Cek KytheraTools", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        // Kalau engine ngeluarin error
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            Toast.makeText(context, "Upscale Gagal, Cek Logcat!", Toast.LENGTH_LONG).show()
                                            android.util.Log.e("Kythera_AI_UI", "Error dari engine: $errorLog")
                                        }
                                    }

                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        Toast.makeText(context, "Aplikasi Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Pilih foto terlebih dahulu!", Toast.LENGTH_SHORT).show()
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
                            color = AccentCyan,
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
                    Text("AI RealSR sedang merender...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Menggunakan GPU Vulkan NCNN", color = TextDesc, fontSize = 11.sp)
                }
            }
        }
    }
}
