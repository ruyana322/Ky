package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import java.io.FileInputStream

@Composable
fun VideoEnhanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var statusText by rememberSaveable { mutableStateOf("") }
    var errorLog by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri by remember { mutableStateOf<Uri?>(null) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            statusText = "✅ Video berhasil dipilih! Siap diproses."
            errorLog = null
            isSuccess = false
            savedVideoUri = null
        }
    }

    fun processVideo() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true
            isSuccess = false
            errorLog = null
            savedVideoUri = null
            statusText = "Menyiapkan mesin FFmpeg & GPU..."

            val fileName = "Kythera_HD_${System.currentTimeMillis()}.mp4"
            val outPath = File(context.getExternalFilesDir(null), fileName).absolutePath
            
            val safUrl = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)

            statusText = "Rendering Video via GPU (Hardware Acceleration)..."
            
            // 🔥 FFmpeg Command: Pakai h264_mediacodec (GPU) dan bitrate 15Mbps buat jaga kualitas HD
            val command = "-hide_banner -i \"$safUrl\" -threads 8 -vf \"scale=1920:-2:flags=lanczos,unsharp=5:5:1.0,eq=contrast=1.05:saturation=1.15\" -c:v h264_mediacodec -b:v 15M -c:a copy \"$outPath\""

            withContext(Dispatchers.IO) {
                val session = FFmpegKit.execute(command)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                    }

                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                            FileInputStream(File(outPath)).use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        File(outPath).delete()
                        savedVideoUri = destUri // Simpan URI buat tombol "Tonton Hasil"
                    }

                    isSuccess = true
                    statusText = "✅ Berhasil! File diekspor ke Galeri."
                } else {
                    statusText = "❌ Proses Gagal!"
                    errorLog = session.allLogsAsString 
                }
            }
            isProcessing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kythera Video Upscale", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        Text("Lanczos GPU Accelerated", color = KColor.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video" else "Pilih Video",
                subtitle = "MP4, MOV, AVI — HD Quality",
                icon = Icons.Rounded.Movie,
                accentColor = KColor.Accent
            )
            
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(16.dp))
                Text(statusText, color = KColor.Text2, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
        }
        
        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("⚠️ FFMPEG ERROR LOG", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
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
            
            // 🔥 Tampilan Tombol kalau proses sukses
            if (isSuccess && savedVideoUri != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KPrimaryButton(
                        label = "Tonton Hasil",
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(savedVideoUri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    )
                    KPrimaryButton(
                        label = "Ulangi",
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            inputUriString = null
                            isSuccess = false
                            statusText = ""
                            savedVideoUri = null
                        }
                    )
                }
            } else {
                KPrimaryButton(
                    label = "Tingkatkan Resolusi Video",
                    icon = Icons.Rounded.AutoAwesome,
                    enabled = inputUri != null && !isProcessing,
                    onClick = ::processVideo
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
