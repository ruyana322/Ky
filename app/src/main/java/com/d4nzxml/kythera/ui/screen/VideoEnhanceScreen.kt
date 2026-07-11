package com.d4nzxml.kythera.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }
    var outputPath by remember { mutableStateOf("") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUri = it
            statusText = "✅ Video berhasil dipilih! Siap diproses."
            isSuccess = false
        }
    }

    fun processVideo() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true
            isSuccess = false
            statusText = "Meningkatkan Resolusi & Detail..."

            val fileName = "Kythera_HD_${System.currentTimeMillis()}.mp4"
            val outPath = File(context.getExternalFilesDir(null), fileName).absolutePath
            
            // 🔥 JURUS RAHASIA: Ngubah Content URI jadi format khusus yang bisa dibaca FFmpeg
            val safUrl = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)

            // Command Lanczos + Sharpening + Color Grade
            val command = "-i \"$safUrl\" -vf \"scale=1920:-1:flags=lanczos,unsharp=5:5:1.0,eq=contrast=1.05:saturation=1.15\" -c:v libx264 -preset superfast -crf 18 -c:a copy \"$outPath\""

            withContext(Dispatchers.IO) {
                val session = FFmpegKit.execute(command)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    outputPath = outPath
                    isSuccess = true
                    statusText = "✅ Berhasil! File tersimpan di: $fileName"
                } else {
                    val logs = session.allLogsAsString
                    statusText = "❌ Proses Gagal! (Cek Log)"
                    println("FFMPEG ERROR: $logs")
                }
            }
            isProcessing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kythera Video Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        Text("Lanczos Algorithm Engine", color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle = "MP4, MOV, AVI — HD Quality",
                icon = Icons.Rounded.Movie,
                accentColor = KColor.Accent
            )
            
            if (statusText.isNotEmpty() && !isProcessing) {
                Spacer(Modifier.height(16.dp))
                Text(statusText, color = KColor.Text2, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
        }
        
        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KColor.Surface2).padding(16.dp)) {
                Column {
                    Text("⏳ $statusText", color = KColor.Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF3FB950))
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            KPrimaryButton(
                label = "Tingkatkan Resolusi Video",
                icon = Icons.Rounded.AutoAwesome,
                enabled = inputUri != null && !isProcessing,
                onClick = ::processVideo
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
