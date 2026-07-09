package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.service.RealSrEngine
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.launch

@Composable
fun EnhanceScreen() {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val snackbar    = remember { SnackbarHostState() }

    var inputBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName     by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText   by remember { mutableStateOf("Lagi motong & HD-in Poto nih Kang!") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            outputBitmap = null
            fileName = it.lastPathSegment ?: "image"
            val stream = context.contentResolver.openInputStream(it)
            inputBitmap = BitmapFactory.decodeStream(stream)
        }
    }

    fun processImage() {
        if (inputBitmap == null) {
            scope.launch { snackbar.showSnackbar("Pilih poto burik dulu Kang!") }
            return
        }
        scope.launch {
            isProcessing = true
            statusText   = "Manasin mesin AI..."

            // 1. Setup binary + model dari assets
            val ready = RealSrEngine.setup(context)
            if (!ready) {
                snackbar.showSnackbar("Gagal setup mesin AI!")
                isProcessing = false
                return@launch
            }

            statusText = "Lagi HD-in poto... (butuh beberapa detik)"

            // 2. Jalankan upscale
            val result = RealSrEngine.upscale(context, inputBitmap!!)

            if (result != null) {
                outputBitmap = result
                val saved = GalleryService.saveBitmap(
                    context, result,
                    "Kythera_HD_${System.currentTimeMillis()}.png"
                )
                snackbar.showSnackbar(
                    if (saved) "SUKSES! Poto HD tersimpan di Galeri/Kythera 🎉"
                    else "Selesai, tapi gagal nyimpen ke galeri."
                )
            } else {
                snackbar.showSnackbar("ERROR! Gagal nge-HD-in poto. Cek log.")
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
            Text(
                "AI Enhance Poto",
                color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800
            )
            Text(
                "Bikin poto burik jadi HD pakai Real-ESRGANv3 Anime (Vulkan GPU).",
                color = KColor.Text2, fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))

            GlassCard {
                KDropZone(
                    onTap = { picker.launch("image/*") },
                    title = "Upload Poto Burik",
                    subtitle = "JPG, PNG, WEBP",
                    icon = Icons.Rounded.Image,
                    accentColor = KColor.Accent,
                    selectedFileName = fileName
                )

                if (inputBitmap != null && outputBitmap == null) {
                    Spacer(Modifier.height(16.dp))
                    Text("Poto Asli:", color = KColor.Text2, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = inputBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                } else if (outputBitmap != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Hasil HD x4 (Real-ESRGANv3 Anime):",
                        color = KColor.Accent, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = outputBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(250.dp)
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
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = KColor.Accent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Memproses...",
                        color = KColor.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Text(statusText, color = KColor.Text2, fontSize = 14.sp)
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
