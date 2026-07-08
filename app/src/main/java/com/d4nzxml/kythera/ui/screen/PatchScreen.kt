package com.d4nzxml.kythera.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.service.FfmpegService
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import com.d4nzxml.kythera.util.getRealPathFromUri
import kotlinx.coroutines.launch

enum class PatchType { METADATA, WATERMARK, STREAM_PATCH, SUBTITLE }

@Composable
fun PatchScreen() {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()
    val ffmpeg   = remember { FfmpegService(context) }
    val snackbar = remember { SnackbarHostState() }

    var inputPath     by remember { mutableStateOf<String?>(null) }
    var fileName      by remember { mutableStateOf<String?>(null) }
    var fileSize      by remember { mutableStateOf<String?>(null) }
    var patchType     by remember { mutableStateOf(PatchType.METADATA) }
    var preserveOrig  by remember { mutableStateOf(true) }
    var backupBefore  by remember { mutableStateOf(true) }
    var verifyInteg   by remember { mutableStateOf(false) }
    var isProcessing  by remember { mutableStateOf(false) }

    // Metadata fields
    var titleText  by remember { mutableStateOf("") }
    var descText   by remember { mutableStateOf("") }
    var authorText by remember { mutableStateOf("") }
    var yearText   by remember { mutableStateOf("2026") }
    var wmText     by remember { mutableStateOf("D4nzxml") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputPath = getRealPathFromUri(context, it)
            fileName  = it.lastPathSegment ?: "video"
            fileSize  = context.contentResolver.openFileDescriptor(it, "r")?.use { fd ->
                FfmpegService.formatSize(fd.statSize)
            }
        }
    }

    fun applyPatch() {
        if (inputPath == null) {
            scope.launch { snackbar.showSnackbar("Pilih video dulu Kang!") }
            return
        }
        scope.launch {
            isProcessing = true
            val result = when (patchType) {
                PatchType.METADATA -> ffmpeg.patchMetadata(
                    inputPath   = inputPath!!,
                    title       = titleText,
                    description = descText,
                    author      = authorText,
                    year        = yearText,
                )
                PatchType.WATERMARK -> ffmpeg.patchWatermark(
                    inputPath     = inputPath!!,
                    watermarkText = wmText,
                )
                else -> {
                    isProcessing = false
                    snackbar.showSnackbar("Fitur ini belum diimplementasi.")
                    return@launch
                }
            }
            isProcessing = false
            if (result.success) {
                val saved = GalleryService.saveVideo(context, result.outputPath)
                snackbar.showSnackbar(
                    if (saved) "PATCH SUKSES! Tersimpan di Galeri/Kythera 🎉"
                    else "Selesai, gagal simpan galeri."
                )
            } else {
                snackbar.showSnackbar("ERROR FFmpeg: ${result.errorMessage}")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Patch Video", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
                Spacer(Modifier.width(10.dp))
                KBadge("BETA", KColor.Orange)
            }
            Text("Modifikasi metadata, inject watermark, atau patch stream video.",
                color = KColor.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            GlassCard {
                KDropZone(
                    onTap = { picker.launch("video/*") },
                    title = "Pilih video target",
                    subtitle = "MP4, MKV, AVI, MOV, WEBM",
                    icon = Icons.Rounded.CloudUpload,
                    accentColor = KColor.Orange,
                    selectedFileName = fileName,
                    selectedFileSize = fileSize,
                )
                Spacer(Modifier.height(20.dp))

                KFieldLabel("Patch Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatchTypeBtn("Metadata", Icons.Outlined.Label,
                        patchType == PatchType.METADATA, KColor.Orange) { patchType = PatchType.METADATA }
                    PatchTypeBtn("Watermark", Icons.Outlined.AddCircle,
                        patchType == PatchType.WATERMARK, KColor.Orange) { patchType = PatchType.WATERMARK }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatchTypeBtn("Stream Patch", Icons.Outlined.Link,
                        patchType == PatchType.STREAM_PATCH, KColor.Orange) { patchType = PatchType.STREAM_PATCH }
                    PatchTypeBtn("Subtitle", Icons.Outlined.Subtitles,
                        patchType == PatchType.SUBTITLE, KColor.Orange) { patchType = PatchType.SUBTITLE }
                }
                Spacer(Modifier.height(20.dp))

                AnimatedContent(targetState = patchType, label = "patchform") { pt ->
                    when (pt) {
                        PatchType.METADATA -> MetadataForm(titleText, descText, authorText, yearText,
                            { titleText = it }, { descText = it }, { authorText = it }, { yearText = it })
                        PatchType.WATERMARK -> WatermarkForm(wmText) { wmText = it }
                        else -> Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(KColor.Surface2)
                                .border(1.dp, KColor.Border, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text("Fitur ini sedang dalam pengembangan...",
                                color = KColor.Text3, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            GlassCard {
                Text("Advanced Options", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                KToggleRow("Preserve Original", "Simpan file asli",
                    preserveOrig) { preserveOrig = it }
                Spacer(Modifier.height(12.dp))
                KToggleRow("Backup Before Patch", "Buat backup otomatis",
                    backupBefore) { backupBefore = it }
                Spacer(Modifier.height(12.dp))
                KToggleRow("Verify Integrity", "Cek integritas setelah patch",
                    verifyInteg) { verifyInteg = it }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(KColor.Orange.copy(0.05f))
                        .border(1.dp, KColor.Orange.copy(0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null,
                            tint = KColor.Orange.copy(0.8f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pastikan backup sebelum melanjutkan. Patching memodifikasi container tanpa re-encode.",
                            color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            KPrimaryButton(
                label = "Apply Patch",
                icon = Icons.Rounded.Edit,
                enabled = !isProcessing,
                onClick = ::applyPatch,
                startColor = KColor.Orange,
                endColor = Color(0xFFD97706),
            )
            Spacer(Modifier.height(24.dp))
        }

        KLoadingOverlay(isProcessing, "Patching Video...",
            "Memodifikasi metadata container...\nProses cepat, hampir selesai!")
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RowScope.PatchTypeBtn(
    label: String, icon: ImageVector,
    isActive: Boolean, activeColor: Color, onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) activeColor.copy(0.1f) else Color.Transparent)
            .border(1.dp,
                if (isActive) activeColor.copy(0.5f) else KColor.Border,
                RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (isActive) activeColor else KColor.Text2, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label,
            color = if (isActive) activeColor else KColor.Text2,
            fontSize = 11.sp, fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400)
    }
}

@Composable
private fun MetadataForm(
    title: String, desc: String, author: String, year: String,
    onTitle: (String) -> Unit, onDesc: (String) -> Unit,
    onAuthor: (String) -> Unit, onYear: (String) -> Unit
) {
    Column {
        KFieldLabel("Title Metadata")
        OutlinedTextField(title, onTitle, Modifier.fillMaxWidth(),
            placeholder = { Text("Judul video", color = KColor.Text3, fontSize = 13.sp) },
            textStyle = LocalTextStyle.current.copy(color = KColor.Text, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KColor.Accent, unfocusedBorderColor = KColor.Border,
                focusedContainerColor = KColor.Surface, unfocusedContainerColor = KColor.Surface,
            ))
        Spacer(Modifier.height(14.dp))
        KFieldLabel("Description")
        OutlinedTextField(desc, onDesc, Modifier.fillMaxWidth(), minLines = 3,
            placeholder = { Text("Deskripsi video", color = KColor.Text3, fontSize = 13.sp) },
            textStyle = LocalTextStyle.current.copy(color = KColor.Text, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KColor.Accent, unfocusedBorderColor = KColor.Border,
                focusedContainerColor = KColor.Surface, unfocusedContainerColor = KColor.Surface,
            ))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                KFieldLabel("Author")
                OutlinedTextField(author, onAuthor, Modifier.fillMaxWidth(),
                    placeholder = { Text("Nama author", color = KColor.Text3, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(color = KColor.Text, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KColor.Accent, unfocusedBorderColor = KColor.Border,
                        focusedContainerColor = KColor.Surface, unfocusedContainerColor = KColor.Surface,
                    ))
            }
            Column(Modifier.weight(1f)) {
                KFieldLabel("Year")
                OutlinedTextField(year, onYear, Modifier.fillMaxWidth(),
                    placeholder = { Text("2026", color = KColor.Text3, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(color = KColor.Text, fontSize = 13.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KColor.Accent, unfocusedBorderColor = KColor.Border,
                        focusedContainerColor = KColor.Surface, unfocusedContainerColor = KColor.Surface,
                    ))
            }
        }
    }
}

@Composable
private fun WatermarkForm(wm: String, onWm: (String) -> Unit) {
    Column {
        KFieldLabel("Teks Watermark")
        OutlinedTextField(wm, onWm, Modifier.fillMaxWidth(),
            placeholder = { Text("Contoh: D4nzxml © 2026", color = KColor.Text3, fontSize = 13.sp) },
            textStyle = LocalTextStyle.current.copy(color = KColor.Text, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KColor.Accent, unfocusedBorderColor = KColor.Border,
                focusedContainerColor = KColor.Surface, unfocusedContainerColor = KColor.Surface,
            ))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KColor.Accent.copy(0.05f))
                .border(1.dp, KColor.Accent.copy(0.15f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("Watermark teks akan muncul di pojok kanan bawah video. Posisi & ukuran otomatis.",
                color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
