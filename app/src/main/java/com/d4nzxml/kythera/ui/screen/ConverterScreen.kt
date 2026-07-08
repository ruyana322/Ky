package com.d4nzxml.kythera.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
fun ConverterScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val ffmpeg  = remember { FfmpegService(context) }
    val snackbar = remember { SnackbarHostState() }

    var inputPath   by remember { mutableStateOf<String?>(null) }
    var fileName    by remember { mutableStateOf<String?>(null) }
    var fileSize    by remember { mutableStateOf<String?>(null) }
    var selectedFmt by remember { mutableStateOf("MP4") }
    var selectedCodec by remember { mutableStateOf("libx264") }
    var selectedRes by remember { mutableStateOf("original") }
    var bitrateM    by remember { mutableStateOf(8f) }
    var isProcessing by remember { mutableStateOf(false) }

    val formats = listOf("MP4", "MKV", "AVI", "WEBM", "MOV", "GIF")
    val codecs  = linkedMapOf(
        "H.264 (AVC) — Compatible"  to "libx264",
        "H.265 (HEVC) — Efficient"  to "libx265",
        "AV1 — Next Gen"            to "libaom-av1",
        "VP9 — Web Optimized"       to "libvpx-vp9",
    )
    val resolutions = linkedMapOf(
        "Original"           to "original",
        "4K UHD (3840x2160)" to "3840:2160",
        "1440p (2560x1440)"  to "2560:1440",
        "1080p (1920x1080)"  to "1920:1080",
        "720p (1280x720)"    to "1280:720",
        "480p (854x480)"     to "854:480",
    )

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = getRealPathFromUri(context, it)
            fileName = it.lastPathSegment ?: "video"
            fileSize = context.contentResolver.openFileDescriptor(it, "r")?.use { fd ->
                FfmpegService.formatSize(fd.statSize)
            }
            inputPath = path
        }
    }

    fun convert() {
        if (inputPath == null) {
            scope.launch { snackbar.showSnackbar("Pilih video dulu Kang!") }
            return
        }
        scope.launch {
            isProcessing = true
            val result = ffmpeg.convertVideo(
                inputPath    = inputPath!!,
                targetFormat = selectedFmt.lowercase(),
                bitrateM     = bitrateM.toInt(),
                codec        = selectedCodec,
                resolution   = selectedRes,
            )
            isProcessing = false
            if (result.success) {
                val saved = GalleryService.saveVideo(context, result.outputPath)
                snackbar.showSnackbar(
                    if (saved) "SUKSES! Video tersimpan di Galeri/Kythera 🎉"
                    else "Selesai, tapi gagal simpan ke galeri."
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
            Text("Converter Video", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Text("Konversi video ke berbagai format dengan kontrol kualitas penuh.",
                color = KColor.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            // ── Input ──────────────────────────────────────────────────
            GlassCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    KStepBadge("1", KColor.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Input Video", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                KDropZone(
                    onTap = { picker.launch("video/*") },
                    title = "Drop video atau klik upload",
                    subtitle = "MP4, AVI, MKV, MOV, WEBM, FLV",
                    icon = Icons.Rounded.CloudUpload,
                    accentColor = KColor.Accent,
                    selectedFileName = fileName,
                    selectedFileSize = fileSize,
                )
                if (inputPath != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = KColor.Border)
                    Spacer(Modifier.height(10.dp))
                    KInfoRow("Format Asli", ".${inputPath!!.substringAfterLast('.')}")
                    KInfoRow("Size", fileSize ?: "-")
                }
            }
            Spacer(Modifier.height(14.dp))

            // ── Output Settings ────────────────────────────────────────
            GlassCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    KStepBadge("2", KColor.Accent2)
                    Spacer(Modifier.width(10.dp))
                    Text("Output Settings", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))

                KFieldLabel("Format Output")
                // 3-column grid format buttons
                val fmtRows = formats.chunked(3)
                fmtRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { f ->
                            Box(Modifier.weight(1f)) {
                                KFormatTabButton(f, selectedFmt == f) { selectedFmt = f }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                KFieldLabel("Codec")
                KDropdownMenu(
                    items = codecs.keys.toList(),
                    selected = codecs.entries.first { it.value == selectedCodec }.key,
                    onSelect = { selectedCodec = codecs[it]!! }
                )
                Spacer(Modifier.height(14.dp))
                KFieldLabel("Resolution")
                KDropdownMenu(
                    items = resolutions.keys.toList(),
                    selected = resolutions.entries.first { it.value == selectedRes }.key,
                    onSelect = { selectedRes = resolutions[it]!! }
                )
                Spacer(Modifier.height(14.dp))
                KFieldLabel("Bitrate")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Slider(
                        value = bitrateM, onValueChange = { bitrateM = it },
                        valueRange = 1f..50f, steps = 48,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = KColor.Accent,
                            thumbColor = KColor.Accent,
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${bitrateM.toInt()} Mbps", color = KColor.Text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))

            KPrimaryButton(
                label = "Konversi Sekarang",
                icon = Icons.Rounded.SwapHoriz,
                enabled = !isProcessing,
                onClick = ::convert
            )
            Spacer(Modifier.height(24.dp))
        }

        KLoadingOverlay(isProcessing)
        SnackbarHost(snackbar, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
}

// ─── Dropdown ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KDropdownMenu(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = KColor.Accent,
                unfocusedBorderColor = KColor.Border,
                focusedTextColor     = KColor.Text2,
                unfocusedTextColor   = KColor.Text2,
                focusedContainerColor   = KColor.Surface,
                unfocusedContainerColor = KColor.Surface,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = KColor.Text2)
        )
        ExposedDropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false }
            // Parameter containerColor udah dihapus dari sini
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = KColor.Text2, fontSize = 13.sp) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}
