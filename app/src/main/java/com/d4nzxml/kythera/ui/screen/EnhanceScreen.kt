package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
    
    var elapsedTime  by remember { mutableStateOf(0L) }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            val startTime = System.currentTimeMillis()
            while (true) {
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000L
                delay(100L) 
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
            statusText = "Memuat AI Engine..."
            
            // Pindah ke background biar gak freeze
            val ready = withContext(Dispatchers.IO) { RealSrEngine.setup(context) }
            if (!ready) {
                statusText = "Gagal memuat engine Kythera!"; errorLog = "Initialization failed."
                isProcessing = false; return@launch
            }
            
            statusText = "Meningkatkan Resolusi & Detail..."
            
            // Proses upscale di background
            val (result, log) = withContext(Dispatchers.IO) { 
                RealSrEngine.upscaleWithLog(context, inputBitmap!!) 
            }
            
            if (result != null) {
                // Auto-save dihapus, gambar langsung tampil
                outputBitmap = result
                isSuccess = true
            } else {
                statusText = "❌ Proses Gagal!"
                errorLog = log ?: "Unknown Engine Error"
            }
            
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (outputBitmap == null) {
            Text("Kythera Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
            Text("powered By Ai ", color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

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
                    Text(statusText, color = KColor.Text2, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (inputBitmap != null) {
                    Spacer(Modifier.height(12.dp))
                    Image(bitmap = inputBitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f)) 
            
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bandingkan Hasil (HD)", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("← geser →", color = KColor.Text2, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            
            BeforeAfterSlider(
                before = inputBitmap!!,
                after  = outputBitmap!!,
                modifier = Modifier
                    .weight(1f) 
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, KColor.Border, RoundedCornerShape(16.dp))
            )
        }

        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp)).background(Color(0x22FF0000), RoundedCornerShape(10.dp)).padding(14.dp)
            ) {
                Text("⚠️ SYSTEM LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }
        }

        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KColor.Surface2).border(1.dp, KColor.Border, RoundedCornerShape(14.dp)).padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏳", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(statusText, color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)), color = Color(0xFF3FB950), trackColor = KColor.Border
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Writing output...", color = KColor.Text3, fontSize = 11.sp)
                        Text("$elapsedTime Detik", color = KColor.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            if (outputBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KPrimaryButton(
                        label = "Simpan",
                        icon = Icons.Rounded.Save,
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    GalleryService.saveBitmap(context, outputBitmap!!, "Kythera_HD_${System.currentTimeMillis()}.png")
                                }
                                Toast.makeText(context, "Tersimpan di Galeri! 🔥", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    
                    KPrimaryButton(
                        label = "Ulangi",
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            outputBitmap = null
                            inputBitmap = null 
                            statusText = ""
                        }
                    )
                }
            } else {
                KPrimaryButton(
                    label = "Tingkatkan Resolusi",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = !isProcessing && inputBitmap != null,
                    onClick = ::processImage
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BeforeAfterSlider(before: Bitmap, after: Bitmap, modifier: Modifier = Modifier) {
    var sliderPos by remember { mutableStateOf(0.5f) }
    val beforeImg = remember(before) { before.asImageBitmap() }
    val afterImg  = remember(after)  { after.asImageBitmap() }

    BoxWithConstraints(modifier = modifier.background(KColor.Surface)) {
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

            drawLine(Color.White, Offset(w * sliderPos, 0f), Offset(w * sliderPos, h), strokeWidth = 5f)
            drawCircle(Color.White, radius = 28f, center = Offset(w * sliderPos, h / 2))
            drawCircle(KColor.Accent, radius = 20f, center = Offset(w * sliderPos, h / 2))
        }

        Box(Modifier.align(Alignment.TopStart).padding(12.dp)
            .background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("BEFORE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)
            .background(KColor.Accent.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("AFTER", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
