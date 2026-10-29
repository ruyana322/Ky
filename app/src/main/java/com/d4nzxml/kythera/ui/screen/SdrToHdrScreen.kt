package com.d4nzxml.kythera.ui.screen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.d4nzxml.kythera.service.FfmpegService
import com.d4nzxml.kythera.service.GalleryService
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import com.d4nzxml.kythera.utils.PathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SDR ke HDR (HLG atau HDR10), konversi warna beneran lewat zscale + encode ulang
 * HEVC 10-bit. Bukan sekadar nempel tag metadata.
 *
 * Settingan (npl, CRF, preset) dikunci ke kombinasi yang udah divalidasi manual
 * hasilnya bagus/nggak buram — nggak ada kontrolnya lagi di UI, cuma pilih HLG
 * atau HDR10. Ini inverse tone mapping, hasilnya selalu perkiraan — detail
 * highlight yang nggak ada di video asli nggak bisa dimunculkan balik.
 */
@Composable
fun SdrToHdrScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ffmpegService = remember { FfmpegService(context) }

    var inputUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    var capabilityChecked by remember { mutableStateOf(false) }
    var capabilityOk by remember { mutableStateOf(true) }
    var missingParts by remember { mutableStateOf(emptyList<String>()) }

    var alreadyHdr by remember { mutableStateOf(false) }
    var checkingSource by remember { mutableStateOf(false) }

    var target by remember { mutableStateOf("hlg") } // "hlg" atau "hdr10" — cuma ini pilihan user.

    // Settingan dikunci, hasil validasi manual: HLG persis command yang udah dites
    // dan terbukti bagus/nggak buram. HDR10 nurut konvensi standar (npl=203
    // reference white per BT.2408).
    val whiteNits = if (target == "hdr10") 203 else 1000
    val qualityCrf = 18
    val qualityPreset = "veryfast"

    var isProcessing by rememberSaveable { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var progressPct by remember { mutableStateOf(0f) }
    var elapsedSec by remember { mutableStateOf(0) }
    var startTimeMs by remember { mutableStateOf(0L) }

    var isSuccess by rememberSaveable { mutableStateOf(false) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var errorLog by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf<String?>(null) } // command FFmpeg beneran yang jalan

    val inputUri = inputUriStr?.let { Uri.parse(it) }
    val animatedProgress by animateFloatAsState(targetValue = progressPct, label = "progress")

    LaunchedEffect(target) {
        val (ok, missing) = ffmpegService.checkHdrCapability(target)
        capabilityOk = ok
        missingParts = missing
        capabilityChecked = true
    }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            startTimeMs = System.currentTimeMillis()
            elapsedSec = 0
            while (isProcessing) {
                delay(1000)
                elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            inputUriStr = it.toString()
            isSuccess = false; outputPath = null; errorLog = null; debugInfo = null
            thumbnail = null; alreadyHdr = false; statusMsg = ""

            scope.launch(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, it)
                    val bmp = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    withContext(Dispatchers.Main) { thumbnail = bmp }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) { checkingSource = true }
                val path = PathUtils.getPath(context, it)
                if (path != null) {
                    val info = ffmpegService.probeColorInfo(path)
                    withContext(Dispatchers.Main) {
                        alreadyHdr = info?.alreadyHdr == true
                        checkingSource = false
                    }
                } else {
                    withContext(Dispatchers.Main) { checkingSource = false }
                }
            }
        }
    }

    fun process() {
        val uri = inputUri ?: return
        scope.launch {
            isProcessing = true; isSuccess = false; outputPath = null; errorLog = null; debugInfo = null
            progressPct = 0f; statusMsg = "Menyiapkan..."

            try {
                val path = withContext(Dispatchers.IO) { PathUtils.getPath(context, uri) }
                if (path == null) {
                    errorLog = "Tidak bisa akses file sumber"; isProcessing = false; return@launch
                }

                statusMsg = "Konversi warna BT.709 \u2192 BT.2020 + encode..."
                val result = ffmpegService.convertSdrToHdr(
                    inputPath = path,
                    target = target,
                    whiteNits = whiteNits,
                    crf = qualityCrf,
                    preset = qualityPreset,
                    onProgress = { pct -> progressPct = pct / 100f },
                )

                debugInfo = result.debugInfo

                if (!result.success) {
                    errorLog = result.errorMessage ?: "Gagal konversi"
                    statusMsg = "Gagal"
                    isProcessing = false
                    return@launch
                }

                progressPct = 1f
                outputPath = result.outputPath
                isSuccess = true
                statusMsg = "Selesai \u00b7 ${elapsedSec}s \u2705"
            } catch (e: Exception) {
                errorLog = "${e.javaClass.simpleName}: ${e.message}"
                statusMsg = "Error"
            }
            isProcessing = false
        }
    }

    fun reset() {
        inputUriStr = null; thumbnail = null
        isSuccess = false; outputPath = null; errorLog = null; debugInfo = null
        progressPct = 0f; statusMsg = ""; alreadyHdr = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // ── Preview sumber / progress ───────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF0A0A0F))
                .clickable(enabled = !isProcessing && !isSuccess) { picker.launch("video/*") },
        ) {
            if (isSuccess && outputPath != null) {
                // ── Preview HASIL, langsung bisa diputer ──────────────────
                var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
                var isPlaying by remember { mutableStateOf(true) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(outputPath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                            videoViewRef = this
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            val v = videoViewRef ?: return@clickable
                            if (isPlaying) v.pause() else v.start()
                            isPlaying = !isPlaying
                        },
                )
                if (!isPlaying) {
                    Box(
                        modifier = Modifier.align(Alignment.Center).size(56.dp)
                            .clip(CircleShape).background(Color(0x99000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .clip(RoundedCornerShape(6.dp)).background(KColor.Accent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(target.uppercase(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            } else if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(20.dp),
                )
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color(0xCC000000),
                        ),
                    ),
                )
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Ganti \u270e", color = Color.White, fontSize = 11.sp)
                }
                if (checkingSource) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Membaca info warna...", color = Color.White.copy(0.8f), fontSize = 11.sp)
                    }
                } else if (alreadyHdr) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xCCFF9800))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("\u26a0\ufe0f Video ini sudah HDR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(KColor.Accent.copy(0.15f))
                            .border(1.dp, KColor.Accent.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.HdrOn, contentDescription = null, tint = KColor.Accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Tap untuk pilih video", color = KColor.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("MP4, MOV, AVI \u00b7 SDR saja", color = KColor.Text2, fontSize = 12.sp)
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xDD000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = KColor.Accent,
                                trackColor = Color.White.copy(0.1f),
                                strokeWidth = 6.dp,
                            )
                            Text(
                                "${(animatedProgress * 100).toInt()}%",
                                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Text(
                            "${elapsedSec}s berlangsung", color = KColor.Accent,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = KColor.Accent, trackColor = Color.White.copy(0.12f),
                            )
                            Text(
                                statusMsg, color = Color.White.copy(0.7f), fontSize = 11.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (capabilityChecked && !capabilityOk) {
                GlassCard(borderColor = Color(0xFFEF5350).copy(0.4f)) {
                    Text(
                        "Build FFmpeg belum punya: ${missingParts.joinToString(", ")}",
                        color = Color(0xFFEF5350), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }

            AnimatedVisibility(visible = !isProcessing && !isSuccess, enter = fadeIn(), exit = fadeOut()) {
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hlg" to "HLG", "hdr10" to "HDR10 (PQ)").forEach { (t, label) ->
                            val selected = target == t
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) KColor.Accent else Color.White.copy(0.05f))
                                    .clickable { target = t }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label, color = if (selected) Color.Black else Color.White.copy(0.7f),
                                    fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = errorLog != null && !isProcessing) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFEF5350).copy(0.5f), RoundedCornerShape(10.dp))
                        .background(Color(0x15EF5350)).padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Error Log", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorLog ?: "", color = Color(0xFFFFAB91),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
                    )
                }
            }

            AnimatedVisibility(visible = isSuccess && outputPath != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KPrimaryButton(
                        label = "Simpan ke Galeri", icon = Icons.Rounded.Save,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val p = outputPath ?: return@KPrimaryButton
                            scope.launch {
                                val saved = GalleryService.saveVideo(context, p)
                                statusMsg = if (saved) "Tersimpan di Movies/Kythera \u2705" else "Gagal simpan ke galeri"
                            }
                        },
                    )
                    KPrimaryButton(
                        label = "Reset", icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f),
                        startColor = KColor.Border2, endColor = KColor.Border2,
                        onClick = { reset() },
                    )
                }
            }

            AnimatedVisibility(visible = debugInfo != null && !isProcessing) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, KColor.Border2, RoundedCornerShape(10.dp))
                        .background(Color(0x15FFFFFF)).padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Debug \u00b7 screenshot ini kalau hasilnya aneh",
                        color = KColor.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        debugInfo ?: "", color = KColor.Text2,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
                    )
                }
            }

            AnimatedVisibility(visible = !isProcessing && !isSuccess) {
                KPrimaryButton(
                    label = when {
                        inputUri == null -> "Pilih Video Dulu"
                        alreadyHdr -> "Video Sudah HDR"
                        !capabilityOk -> "Engine Tidak Lengkap"
                        else -> "\u2728 Konversi ke HDR"
                    },
                    icon = Icons.Rounded.HdrOn,
                    enabled = inputUri != null && !alreadyHdr && capabilityOk && !checkingSource,
                    onClick = ::process,
                )
            }
        }
    }
}
