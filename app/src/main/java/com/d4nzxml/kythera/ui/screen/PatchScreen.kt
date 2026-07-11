package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import java.io.FileOutputStream

// 🔥 Sesuai request: Cukup Patch Only dan Encode + Patch
enum class PatchLogic(val label: String, val desc: String) {
    PATCH_ONLY("Patch Only", "Merapikan struktur MP4 (+faststart) & inject fake sample tanpa render ulang."),
    ENCODE_PATCH("Encode + Patch", "Render ulang video (GPU Accelerated) untuk optimasi penuh, lalu inject fake sample.")
}

@Composable
fun PatchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var statusText by rememberSaveable { mutableStateOf("") }
    var errorLog by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedLogic by remember { mutableStateOf(PatchLogic.PATCH_ONLY) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            statusText = "✅ Video siap dipatch!"
            errorLog = null
            isSuccess = false
            savedVideoUri = null
        }
    }

    // ─── Simulasi Suntik Fake Sample ───
    fun injectFakeSample(file: File) {
        /* 
           CATATAN KANG DADAN:
           Di sini tempat lu masukin algoritma bongkar byte array MP4.
           Lu bisa panggil class Kotlin/JNI C++ lu yang bertugas ngebaca tabel 'stbl', 'stsz', 
           dan nambahin dummy frame size.
           
           Contoh kerangkanya:
           val videoBytes = file.readBytes()
           val patchedBytes = MyMp4Injector.applyFakeSample(videoBytes) 
           FileOutputStream(file).use { it.write(patchedBytes) }
        */
        
        // Placeholder sementara biar proses tetap jalan tanpa error
        try {
            val bytes = file.readBytes()
            val dummyTag = "FAKE_SAMPLE_INJECTED".toByteArray()
            val patchedBytes = bytes + dummyTag
            FileOutputStream(file).use { it.write(patchedBytes) }
        } catch (e: Exception) { 
            e.printStackTrace() 
            throw Exception("Gagal menyuntikkan fake sample: ${e.message}")
        }
    }

    fun executePatch() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true
            isSuccess = false
            errorLog = null
            savedVideoUri = null

            val fileName = "Kythera_Patched_${System.currentTimeMillis()}.mp4"
            val outPath = File(context.getExternalFilesDir(null), fileName).absolutePath
            val safUrl = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
            val logic = selectedLogic

            statusText = "Mengeksekusi: ${logic.label}..."

            withContext(Dispatchers.IO) {
                try {
                    val finalFile = File(outPath)

                    when (logic) {
                        PatchLogic.PATCH_ONLY -> {
                            statusText = "⚙️ Merapikan struktur MP4 (+faststart)..."
                            val cmd = "-hide_banner -i \"$safUrl\" -c copy -movflags +faststart \"$outPath\""
                            val session = FFmpegKit.execute(cmd)
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                statusText = "⚙️ Menyuntikkan tabel fake sample..."
                                injectFakeSample(finalFile)
                            } else {
                                throw Exception(session.allLogsAsString)
                            }
                        }
                        
                        PatchLogic.ENCODE_PATCH -> {
                            statusText = "⚙️ Rendering Ulang & Optimasi Video..."
                            val cmd = "-hide_banner -i \"$safUrl\" -threads 0 -vf \"format=yuv420p\" -c:v h264_mediacodec -b:v 10M -bf 0 -c:a aac -b:a 128k -shortest -metadata copyright=\"By kythera\" -metadata artist=\"By kythera\" -movflags +faststart \"$outPath\""
                            val session = FFmpegKit.execute(cmd)
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                statusText = "⚙️ Menyuntikkan tabel fake sample..."
                                injectFakeSample(finalFile)
                            } else {
                                throw Exception(session.allLogsAsString)
                            }
                        }
                    }

                    statusText = "Menyiapkan penyimpanan..."
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            FileInputStream(finalFile).use { it.copyTo(out) }
                        }
                        finalFile.delete() 
                        savedVideoUri = destUri
                        isSuccess = true
                        statusText = "✅ Patch Selesai! Tersimpan di Galeri."
                    } ?: throw Exception("Gagal menyimpan ke Galeri")

                } catch (e: Exception) {
                    statusText = "❌ Proses Gagal!"
                    errorLog = e.message ?: "Unknown Error"
                }
            }
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Kythera Patcher", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        Text("MP4 Optimizer & Sample Injector", color = KColor.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video Target" else "Pilih Video Target",
                subtitle = "MP4, MOV, AVI — Siap di-patch",
                icon = Icons.Rounded.CloudUpload,
                accentColor = KColor.Orange
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = if (isSuccess) KColor.Orange else KColor.Text2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        GlassCard {
            Text("Pilih Metode", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PatchLogic.entries.forEach { logic ->
                val isActive = selectedLogic == logic
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) KColor.Orange.copy(0.12f) else Color.Transparent)
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) KColor.Orange.copy(0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedLogic = logic }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(logic.label,
                            color = if (isActive) KColor.Orange else KColor.Text,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp)
                        Text(logic.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    RadioButton(
                        selected = isActive,
                        onClick = { selectedLogic = logic },
                        colors = RadioButtonDefaults.colors(selectedColor = KColor.Orange)
                    )
                }
            }
        }

        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text("⚠️ ERROR", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KColor.Surface)
                    .padding(16.dp)
            ) {
                Column {
                    Text("⏳ $statusText", color = KColor.Orange, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = KColor.Orange)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (isSuccess && savedVideoUri != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KPrimaryButton(
                    label = "Tonton Hasil",
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    startColor = KColor.Orange,
                    endColor = Color(0xFFD97706),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(savedVideoUri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                )
                KPrimaryButton(
                    label = "Reset",
                    icon = Icons.Rounded.Refresh,
                    modifier = Modifier.weight(1f),
                    startColor = KColor.Orange,
                    endColor = Color(0xFFD97706),
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
                label = "Jalankan Patch",
                icon = Icons.Rounded.AutoFixHigh,
                enabled = inputUri != null && !isProcessing,
                startColor = KColor.Orange,
                endColor = Color(0xFFD97706),
                onClick = ::executePatch
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
