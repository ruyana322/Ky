package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private const val MAX_INPUT_SIZE = 720

@Composable
fun EnhanceScreen() {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()

    var inputBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName     by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText   by remember { mutableStateOf("") }
    var errorLog     by remember { mutableStateOf<String?>(null) } // ← tampil di layar
    var isSuccess    by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                outputBitmap = null
                errorLog = null
                isSuccess = false
                fileName = it.lastPathSegment ?: "image"
                val bmp = withContext(Dispatchers.IO) { safeDecodeBitmap(context, it) }
                inputBitmap = bmp
                statusText = if (bmp != null)
                    "Foto siap: ${bmp.width}x${bmp.height}px → output ~${bmp.width * 4}x${bmp.height * 4}px"
                else "Gagal baca foto!"
            }
        }
    }

    fun processImage() {
        if (inputBitmap == null) return
        scope.launch {
            isProcessing = true
            errorLog     = null
            isSuccess    = false
            statusText   = "Manasin mesin AI..."

            val ready = RealSrEngine.setup(context)
            if (!ready) {
                statusText = "Gagal setup!"
                errorLog   = "Binary atau model tidak bisa di-copy dari assets.\nPastiin file realsr-ncnn, libncnn.so, libomp.so, libc++_shared.so, x4.bin, x4.param ada di assets/realsr/"
                isProcessing = false
                return@launch
            }

            statusText = "Proses AI... sabar ya Kang 🙏"

            // Jalankan upscale, tangkap error log
            val (result, log) = RealSrEngine.upscaleWithLog(context, inputBitmap!!)

            if (result != null) {
                outputBitmap = result
                isSuccess    = true
                statusText   = "✅ Selesai! Output ${result.width}x${result.height}px"
                GalleryService.saveBitmap(context, result, "Kythera_HD_${System.currentTimeMillis()}.png")
            } else {
                statusText = "❌ Gagal!"
                errorLog   = log ?: "Tidak ada log — binary mungkin crash sebelum output"
            }

            isProcessing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("AI Enhance Poto", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Text("Real-ESRGANv3 Anime x4 — GPU Vulkan", color = KColor.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            // ── Input ──
            GlassCard {
                KDropZone(
                    onTap = { picker.launch("image/*") },
                    title = "Upload Poto Burik",
                    subtitle = "JPG, PNG, WEBP",
                    icon = Icons.Rounded.Image,
                    accentColor = KColor.Accent,
                    selectedFileName = fileName
                )
                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(statusText,
                        color = if (isSuccess) KColor.Accent else KColor.Text2,
                        fontSize = 12.sp)
                }
                if (inputBitmap != null && outputBitmap == null) {
                    Spacer(Modifier.height(12.dp))
                    Image(
                        bitmap = inputBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            }

            // ── Error Box — tampil kalau ada error ──
            if (errorLog != null) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                        .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text("⚠️ ERROR LOG", color = Color(0xFFFF4444),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorLog!!,
                        color = Color(0xFFFFAAAA),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }

            // ── Output ──
            if (outputBitmap != null) {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Hasil HD x4:", color = KColor.Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = outputBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            KPrimaryButton(
                label = "Bikin HD Sekarang!",
                icon = Icons.Rounded.AutoAwesome,
                enabled = !isProcessing && inputBitmap != null,
                onClick = ::processImage
            )
            Spacer(Modifier.height(24.dp))
        }

        // Loading overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
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

// ─── Safe decode + resize ─────────────────────────────────────────────────────
private fun safeDecodeBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        var sampleSize = 1
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        while (maxDim / sampleSize > MAX_INPUT_SIZE * 2) sampleSize *= 2

        val loadOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, loadOpts)
        } ?: return null

        val w = raw.width; val h = raw.height
        if (maxOf(w, h) <= MAX_INPUT_SIZE) return raw
        val scale = MAX_INPUT_SIZE.toFloat() / maxOf(w, h)
        val resized = Bitmap.createScaledBitmap(raw, (w * scale).toInt(), (h * scale).toInt(), true)
        if (resized != raw) raw.recycle()
        resized
    } catch (e: Exception) { null }
}
