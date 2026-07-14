package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.RealSrEngine
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
private val ButtonDarkBg = Color(0xFF2D284B)

@Composable
fun EnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) } 

    // State buat Preview & File Sementara
    var inputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var pendingSavedPath by remember { mutableStateOf<String?>(null) }

    // State buat Notifikasi
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isErrorToast by remember { mutableStateOf(false) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedFileUri = uri
    }

    // Pembersih RAM Pintar
    LaunchedEffect(showPreview) {
        if (!showPreview) {
            delay(500) 
            inputBitmap = null
            resultBitmap = null
            pendingSavedPath = null
            System.gc() 
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // 1. LAYAR UTAMA (UPLOAD)
        // ==========================================
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
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    withContext(Dispatchers.Main) {
                                        progressPercent = 0
                                        isLoading = true
                                    }

                                    val inputStream = context.contentResolver.openInputStream(selectedFileUri!!)
                                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                                    inputStream?.close()

                                    if (loadedBitmap == null) {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            toastMessage = "Gagal membaca foto!"
                                            isErrorToast = true
                                        }
                                        return@withContext
                                    }

                                    withContext(Dispatchers.Main) { inputBitmap = loadedBitmap }

                                    var isProcessing = true
                                    scope.launch(Dispatchers.Main) {
                                        var currentP = 0
                                        while (isProcessing && currentP < 90) { 
                                            delay(500) 
                                            // 🔥 KUNCI ANTI-NABRAK: Langsung stop kalau isProcessing udah false
                                            if (!isProcessing) break 
                                            currentP += 1
                                            progressPercent = currentP
                                        }
                                    }

                                    RealSrEngine.setup(context) 
                                    val (resBitmap, errorLog) = RealSrEngine.upscaleWithLog(context, loadedBitmap)
                                    
                                    // Matiin loading palsu SEKARANG JUGA
                                    isProcessing = false 

                                    if (resBitmap != null) {
                                        System.gc() 

                                        // Simpan ke DCIM Public Storage diam-diam
                                        val savedFilePath = withContext(Dispatchers.IO) {
                                            try {
                                                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                                                val kytheraDir = File(dcimDir, "kytheraImg")
                                                if (!kytheraDir.exists()) kytheraDir.mkdirs()

                                                val outFile = File(kytheraDir, "Kythera_x4_${System.currentTimeMillis()}.png")
                                                FileOutputStream(outFile).use { out ->
                                                    resBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                }

                                                MediaScannerConnection.scanFile(
                                                    context,
                                                    arrayOf(outFile.absolutePath),
                                                    arrayOf("image/png")
                                                ) { _, _ -> }

                                                outFile.absolutePath
                                            } catch (e: Exception) { null }
                                        }

                                        withContext(Dispatchers.Main) {
                                            // 🔥 BARU 100% KETIKA BENER-BENER BERES
                                            progressPercent = 100
                                            resultBitmap = resBitmap
                                            pendingSavedPath = savedFilePath 
                                            delay(300) // Kasih lihat angka 100% bentar
                                            isLoading = false
                                            showPreview = true 
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            toastMessage = "Upscale Gagal, Cek Logcat!"
                                            isErrorToast = true
                                        }
                                    }

                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        toastMessage = "AI Error: ${e.message}"
                                        isErrorToast = true
                                    }
                                }
                            }
                        }
                    } else {
                        toastMessage = "Pilih foto terlebih dahulu!"
                        isErrorToast = true
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
        }

        // ==========================================
        // 2. LAYAR PREVIEW BEFORE/AFTER
        // ==========================================
        AnimatedVisibility(
            visible = showPreview, 
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            val cancelPreview = {
                scope.launch(Dispatchers.IO) {
                    pendingSavedPath?.let { path -> 
                        val file = File(path)
                        if (file.exists()) file.delete()
                        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                    }
                }
                showPreview = false 
            }

            BackHandler { cancelPreview() }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
                var sliderPosition by remember { mutableFloatStateOf(0.5f) }

                val currentInBmp = inputBitmap
                val currentResBmp = resultBitmap

                if (currentInBmp != null && currentResBmp != null) {
                    Box(
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                sliderPosition = (sliderPosition + dragAmount.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    ) {
                        Image(
                            bitmap = currentResBmp.asImageBitmap(), contentDescription = "After",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )

                        Image(
                            bitmap = currentInBmp.asImageBitmap(), contentDescription = "Before",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().drawWithContent {
                                clipRect(right = size.width * sliderPosition) { this@drawWithContent.drawContent() }
                            }
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val xPos = size.width * sliderPosition
                            drawLine(color = Color.White, start = Offset(xPos, 0f), end = Offset(xPos, size.height), strokeWidth = 6f)
                            drawCircle(color = Color.White, radius = 24f, center = Offset(xPos, size.height / 2))
                            drawCircle(color = AccentCyan, radius = 12f, center = Offset(xPos, size.height / 2))
                        }

                        Text("Before", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp, start = 20.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                        Text("After", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 40.dp, end = 20.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))).padding(horizontal = 12.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { cancelPreview() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Batal", tint = Color.White) }

                    Button(
                        onClick = {
                            toastMessage = "Selesai! Tersimpan di DCIM/kytheraImg"
                            isErrorToast = false
                            showPreview = false
                            selectedFileUri = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, tint = DashBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Foto", color = DashBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ==========================================
        // 3. OVERLAY LOADING & NOTIFIKASI
        // ==========================================
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(enabled = false) {}, 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progressPercent / 100f, color = AccentCyan, trackColor = CardSolidBg,
                            modifier = Modifier.size(80.dp), strokeWidth = 6.dp
                        )
                        Text("$progressPercent%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("AI RealSR sedang merender...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Menggunakan GPU Vulkan NCNN", color = TextDesc, fontSize = 11.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isErrorToast) Color(0xFFE74C3C).copy(alpha = 0.95f) else Color(0xFF27AE60).copy(alpha = 0.95f)).padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text(toastMessage ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }

        LaunchedEffect(toastMessage) {
            if (toastMessage != null) {
                delay(3500)
                toastMessage = null
            }
        }
    }
}
