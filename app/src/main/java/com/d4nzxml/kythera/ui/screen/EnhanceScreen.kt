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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.service.RealSrEngine
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
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
                    "Foto siap: ${bmp.width}x${bmp.height}px → output ~${bmp.width*4}x${bmp.height*4}px"
                else "Gagal baca foto!"
            }
        }
    }

    fun processImage() {
        if (inputBitmap == null) return
        scope.launch {
            isProcessing = true; errorLog = null; isSuccess = false
            statusText = "Manasin mesin AI..."
            val ready = RealSrEngine.setup(context)
            if (!ready) {
                statusText = "Gagal setup!"; errorLog = "Gagal copy file dari assets."
                isProcessing = false; return@launch
            }
            statusText = "Proses AI... sabar ya Kang 🙏"
            val (result, log) = RealSrEngine.upscaleWithLog(context, inputBitmap!!)
            if (result != null) {
                outputBitmap = result; isSuccess = true
                statusText = "✅ Selesai! Output ${result.width}x${result.height}px"
                GalleryService.saveBitmap(context, result, "Kythera_HD_${System.currentTimeMillis()}.png")
            } else {
                statusText = "❌ Gagal!"; errorLog = log ?: "Error tidak diketahui"
            }
            isProcessing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("AI Enhance Poto", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Text("Real-ESRGANv3 Anime x4 — GPU Vulkan", color = KColor.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            GlassCard {
                KDropZone(
                    onTap = { picker.launch("image/*") },
                    title = "Upload Poto Burik",
                    subtitle = "JPG, PNG, WEBP — Full Resolution",
                    icon = Icons.Rounded.Image,
                    accentColor = KColor.Accent,
                    selectedFileName = fileName
                )
                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(statusText, color = if (isSuccess) KColor.Accent else KColor.Text2, fontSize = 12.sp)
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
                    Text("⚠️ ERROR LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            KPrimaryButton(
                label = if (outputBitmap != null) "Proses Ulang" else "Bikin HD Sekarang!",
                icon = Icons.Rounded.AutoAwesome,
                enabled = !isProcessing && inputBitmap != null,
                onClick = ::processImage
            )
            Spacer(Modifier.height(24.dp))
        }

        if (isProcessing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KColor.Accent, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Memproses...", color = KColor.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(statusText, color = KColor.Text2, fontSize = 13.sp)
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

            // Scale factor untuk fit gambar ke canvas
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

            // Gambar AFTER penuh
            withTransform({ translate(afterOx, afterOy); scale(afterScale, afterScale, pivot = Offset.Zero) }) {
                drawImage(afterImg)
            }

            // Gambar BEFORE di-clip kiri
            clipRect(right = w * sliderPos) {
                withTransform({ translate(beforeOx, beforeOy); scale(beforeScale, beforeScale, pivot = Offset.Zero) }) {
                    drawImage(beforeImg)
                }
            }

            // Garis putih
            drawLine(Color.White, Offset(w * sliderPos, 0f), Offset(w * sliderPos, h), strokeWidth = 3f)

            // Handle lingkaran
            drawCircle(Color.White, radius = 26f, center = Offset(w * sliderPos, h / 2))
            drawCircle(KColor.Accent, radius = 18f, center = Offset(w * sliderPos, h / 2))
        }

        // Label BEFORE
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(Color.Black.copy(0.65f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("BEFORE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Label AFTER
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
            .background(KColor.Accent.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("AFTER", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
