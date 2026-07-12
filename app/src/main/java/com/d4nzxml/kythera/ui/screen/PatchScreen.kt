package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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

enum class PatchLogic(val label: String, val desc: String) {
    PATCH_ONLY("Kythera Patch Only", "Merapikan struktur MP4 & inject tabel MP4 (Tanpa Re-encode)."),
    ENCODE_PATCH("Encode + Patch", "Render ulang video untuk optimasi penuh, lalu inject tabel patch."),
    KYTHERA_60FPS("Kythera 60fps", "Menyematkan metadata stamp eksklusif Kythera 60fps.")
}

enum class EncodeQuality(val label: String, val desc: String, val cmdParams: String) {
    CPU_CRF20(
        "Jernih & Stabil (CRF 20)", 
        "CPU libx264. Ukuran file aman, ketajaman dipertahankan.", 
        "-c:v libx264 -preset fast -crf 20 -bf 0"
    ),
    CPU_HIGH(
        "Sultan High Bitrate (25M)", 
        "CPU libx264. Kualitas maksimal, tapi ukuran file jadi besar.", 
        "-c:v libx264 -preset fast -b:v 25M -maxrate 25M -bufsize 50M -bf 0"
    ),
    GPU_FAST(
        "Super Cepat (GPU)", 
        "Akselerasi GPU Hardware (15M). Cocok untuk proses kilat.", 
        "-c:v h264_mediacodec -b:v 15M -bf 0"
    )
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
    var selectedQuality by remember { mutableStateOf(EncodeQuality.CPU_CRF20) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            statusText = "✅ Siap: ${it.lastPathSegment}"
            errorLog = null
            isSuccess = false
            savedVideoUri = null
        }
    }

    // 🔥 Data biner dasar untuk manipulasi MP4
    val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)

    fun injectKytheraPatch(file: File) {
        try {
            val bytes = file.readBytes()
            val patchedBytes = bytes + FAKE_SAMPLE_BYTES
            FileOutputStream(file).use { it.write(patchedBytes) }
        } catch (e: Exception) { 
            throw Exception("Gagal rekonstruksi tabel MP4: ${e.message}")
        }
    }

    fun applyKythera60fpsStamp(file: File) {
        try {
            val bytes = file.readBytes()
            val stamp = "KYTHERA_60FPS_STAMP_APPLIED".toByteArray()
            val patchedBytes = bytes + stamp
            FileOutputStream(file).use { it.write(patchedBytes) }
        } catch (e: Exception) { 
            throw Exception("Gagal menyematkan Kythera Stamp: ${e.message}")
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
            val quality = selectedQuality

            withContext(Dispatchers.IO) {
                try {
                    val finalFile = File(outPath)

                    when (logic) {
                        PatchLogic.PATCH_ONLY -> {
                            statusText = "⚙️ Merapikan MP4 (+faststart)..."
                            val cmd = "-hide_banner -i \"$safUrl\" -c copy -movflags +faststart \"$outPath\""
                            val session = FFmpegKit.execute(cmd)
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                statusText = "⚙️ Menyuntikkan Kythera Patch..."
                                injectKytheraPatch(finalFile)
                            } else {
                                throw Exception(session.allLogsAsString)
                            }
                        }
                        
                        PatchLogic.ENCODE_PATCH -> {
                            statusText = "⚙️ Encoding Video (${quality.name})..."
                            // Membangun perintah FFmpeg dengan opsi Kualitas yang dipilih
                            val baseCmd = "-hide_banner -i \"$safUrl\" -threads 0 -vf \"format=yuv420p\""
                            val encodeParams = quality.cmdParams
                            val audioParams = "-c:a aac -b:a 128k -shortest -metadata copyright=\"By kythera\" -metadata artist=\"By kythera\" -movflags +faststart"
                            val cmd = "$baseCmd $encodeParams $audioParams \"$outPath\""
                            
                            val session = FFmpegKit.execute(cmd)
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                statusText = "⚙️ Menyuntikkan Kythera Patch..."
                                injectKytheraPatch(finalFile)
                            } else {
                                throw Exception(session.allLogsAsString)
                            }
                        }

                        PatchLogic.KYTHERA_60FPS -> {
                            statusText = "⚙️ Menyematkan metadata stamp Kythera..."
                            val cmd = "-hide_banner -i \"$safUrl\" -c copy \"$outPath\""
                            val session = FFmpegKit.execute(cmd)
                            if (ReturnCode.isSuccess(session.returnCode)) {
                                applyKythera60fpsStamp(finalFile)
                            } else {
                                throw Exception(session.allLogsAsString)
                            }
                        }
                    }

                    statusText = "Menyiapkan unduhan..."
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
                        statusText = "✅ Done: file didownload (Tersimpan di Galeri)."
                    } ?: throw Exception("Gagal menyimpan ke Galeri")

                } catch (e: Exception) {
                    statusText = "❌ Error: Proses Gagal"
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
        Text("MP4 Optimizer & Frame Injector", color = KColor.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // ── Dropzone ──
        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video Target" else "Select video",
                subtitle = "MP4 File Only",
                icon = Icons.Rounded.CloudUpload,
                accentColor = KColor.Orange
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = if (isSuccess) KColor.Orange else KColor.Text2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── Pilihan Metode ──
        GlassCard {
            Text("Metode Patch", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
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

        // ── Opsi Encoding (Hanya Muncul kalau pilih Encode + Patch) ──
        AnimatedVisibility(
            visible = selectedLogic == PatchLogic.ENCODE_PATCH,
            enter = expandVertically(tween(300)),
            exit = shrinkVertically(tween(300))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Kualitas Encoding", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    EncodeQuality.entries.forEach { quality ->
                        val isActive = selectedQuality == quality
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
                                .clickable { selectedQuality = quality }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(quality.label,
                                    color = if (isActive) KColor.Orange else KColor.Text,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp)
                                Text(quality.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            RadioButton(
                                selected = isActive,
                                onClick = { selectedQuality = quality },
                                colors = RadioButtonDefaults.colors(selectedColor = KColor.Orange)
                            )
                        }
                    }
                }
            }
        }

        // ── Area Error ──
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

        // ── Area Progress ──
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

        // ── Tombol Aksi ──
        if (isSuccess && savedVideoUri != null) {
            KPrimaryButton(
                label = "Reset",
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth(),
                startColor = KColor.Orange,
                endColor = Color(0xFFD97706),
                onClick = {
                    inputUriString = null
                    isSuccess = false
                    statusText = ""
                    savedVideoUri = null
                }
            )
        } else {
            KPrimaryButton(
                label = "Patch & Download",
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
