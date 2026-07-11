package com.d4nzxml.kythera.ui.screen

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Siap memproses video...") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { inputUri ->
            scope.launch {
                isProcessing = true
                statusText = "Kythera sedang memproses HD..."
                
                // Command FFmpeg Rahasia: Lanczos + Unsharp + Color
                // Upscale ke 1080p, Lanczos flag, 1.0 unsharp, saturasi 1.15
                val outputPath = context.externalCacheDir.toString() + "/output_hd.mp4"
                val inputPath = inputUri.toString() // Disarankan pakai helper untuk dapet real path

                val command = "-i $inputPath -vf \"scale=1920:-1:flags=lanczos,unsharp=5:5:1.0,eq=contrast=1.05:saturation=1.15\" -c:v libx264 -preset superfast -crf 20 -c:a copy $outputPath"

                withContext(Dispatchers.IO) {
                    val session = FFmpegKit.execute(command)
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        statusText = "✅ Berhasil! Video tersimpan di cache."
                    } else {
                        statusText = "❌ Proses Gagal!"
                    }
                }
                isProcessing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kythera Video Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        
        Spacer(Modifier.height(20.dp))
        
        KDropZone(
            onTap = { videoPicker.launch("video/*") },
            title = "Pilih Video",
            subtitle = "HD Quality Enhancement",
            icon = Icons.Rounded.PlayArrow,
            accentColor = KColor.Accent
        )
        
        if (isProcessing) {
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(statusText, color = KColor.Accent)
        }
    }
}
