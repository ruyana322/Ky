package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.service.RealSrEngine
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EnhanceScreen() {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()

    var inputBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName     by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText   by remember { mutableStateOf("") }
    var errorLog     by remember { mutableStateOf<String?>(null) }
    var isSuccess    by remember { mutableStateOf(false) }
    
    // State untuk Stopwatch
    var elapsedTime  by remember { mutableStateOf(0L) }

    // Efek untuk menjalankan Stopwatch saat isProcessing true
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            val startTime = System.currentTimeMillis()
            while (true) {
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000L
                delay(100L) // Update teks setiap 100ms biar mulus
            }
        } else {
            elapsedTime = 0L
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                outputBitmap = null; errorLog = null; isSuccess = false
                fileName = it.lastPathSegment ?: "image"
                val bmp = withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } }
                    catch (e: Exception) { null }
                }
                inputBitmap = bmp
                statusText = if (bmp != null)
                    "Siap diproses: ${bmp.width}x${bmp.height} ➔ ~${bmp.width*4}x${bmp.height*4}"
                else "Gagal membaca gambar!"
            }
        }
    }

    fun processImage() {
        if (inputBitmap == null) return
        scope.launch {
            isProcessing = true; errorLog = null; isSuccess = false
            val inW = inputBitmap!!.width
            val inH = inputBitmap!!.height
            statusText = "Menyiapkan Kanvas $inW x $inH..."
            
            // Catat waktu asli mulai memproses
            val timeStart = System.currentTimeMillis()

            val ready = RealSrEngine.setup(context)
            if (!ready) {
                statusText = "Gagal memuat engine Kythera!"; errorLog = "Initialization failed."
                isProcessing = false; return@launch
            }
            
            statusText = "Enhancing Resolution & Detail..."
            val (result, log) = RealSrEngine.upscaleWithLog(context, inputBitmap!!)
            
            val timeEnd = System.currentTimeMillis()
            val totalSeconds = (timeEnd - timeStart) / 1000L

            if (result != null) {
                outputBitmap = result; isSuccess = true
                statusText = """
                    ✅ Selesai dalam $totalSeconds detik 🔥
                    Resolusi: ${inW}x${inH} ➔ ${result.width}x${result.height}
                """.trimIndent()
                GalleryService.saveBitmap(context, result, "Kythera_HD_${System.currentTimeMillis()}.png")
            } else {
                statusText = "❌ Proses Gagal!"
                errorLog = log ?: "Unknown Engine Error"
            }
            isProcessing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            // 🔥 BRANDING BARU
            Text("Kythera Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
            Text("powered By Ai Ncn", color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            GlassCard {
                KDropZone(
                    onTap = { picker.launch("image/*") },
                    title = "Upload Foto",
                    subtitle = "JPG, PNG, WEBP — Kualitas Lossless",
                    icon = Icons.Rounded.Image,
                    accentColor = KColor.Accent,
                    selectedFileName = fileName
                )
                if (statusText.isNotEmpty() && !isProcessing) {
                    Spacer(Modifier.height(10.dp))
                    Text(statusText, color = if (isSuccess) KColor.Accent else KColor.Text2, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (inputBitmap != null && outputBitmap == null) {
                    Spacer(Modifier.height(12.dp))
                    Image(bitmap = inputBitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
                }
            }

            if (inputBitmap != null && outputBitmap != null) {
                Spacer(Modifier.height(16.dp))
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Before / After", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("← geser →", color = KColor.Text2, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    BeforeAfterSlider(
                        before = inputBitmap!!,
                        after  = outputBitmap!!,
                        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            if (errorLog != null) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                        .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text("⚠️ SYSTEM LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            KPrimaryButton(
                label = if (outputBitmap != null) "Proses Ulang Gambar" else "Tingkatkan Resolusi",
                icon = Icons.Rounded.AutoAwesome,
                enabled = !isProcessing && inputBitmap != null,
                onClick = ::processImage
            )
            Spacer(Modifier.height(24.dp))
        }

        // 🔥 OVERLAY LOADING BARU (GAYA STOPWATCH)
        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    // Angka Stopwatch Raksasa
                    Text(
                        text = "$elapsedTime",
                        color = KColor.Accent,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("Detik", color = KColor.Accent, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Text("Kythera Upscale", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("powered By Ai Ncn", color = KColor.Text2, fontSize = 12.sp)
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Status text (misal: "Meningkatkan Resolusi...")
                    Text(statusText, color = KColor.Accent, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun BeforeAfterSlider(before: Bitmap, after: Bitmap, modifier: Modifier = Modifier) {
    var sliderPos by remember { mutableStateOf(0.5f) }
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg  = remember(after)  { after.asImageBitmap() }

    BoxWithConstraints(modifier = modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    sliderPos = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                }
            }
        ) {
            val w = size.width
            val h = size.height

            val afterScaleX = w / afterImg.width.toFloat()
            val afterScaleY = h / afterImg.height.toFloat()
            val afterScale  = minOf(afterScaleX, afterScaleY)
            val afterW = afterImg.width * afterScale
            val afterH = afterImg.height * afterScale
            val afterOx = (w - afterW) / 2f
            val afterOy = (h - afterH) / 2f

            val beforeScaleX = w / beforeImg.width.toFloat()
            val beforeScaleY = h / beforeImg.height.toFloat()
            val beforeScale  = minOf(beforeScaleX, beforeScaleY)
            val beforeW = beforeImg.width * beforeScale
            val beforeH = beforeImg.height * beforeScale
            val beforeOx = (w - beforeW) / 2f
            val beforeOy = (h - beforeH) / 2f

            withTransform({ translate(afterOx, afterOy); scale(afterScale, afterScale, pivot = Offset.Zero) }) {
                drawImage(afterImg)
            }

            clipRect(right = w * sliderPos) {
                withTransform({ translate(beforeOx, beforeOy); scale(beforeScale, beforeScale, pivot = Offset.Zero) }) {
                    drawImage(beforeImg)
                }
            }

            drawLine(Color.White, Offset(w * sliderPos, 0f), Offset(w * sliderPos, h), strokeWidth = 4f)

            drawCircle(Color.White, radius = 26f, center = Offset(w * sliderPos, h / 2))
            drawCircle(KColor.Accent, radius = 18f, center = Offset(w * sliderPos, h / 2))
        }

        Box(Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(Color.Black.copy(0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("BEFORE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
            .background(KColor.Accent.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("AFTER", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
