package com.d4nzxml.kythera.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
// COLORS — Dark futuristic glassmorphism (consistent dengan Kythera)
// ═══════════════════════════════════════════════════════════════════════════
private val BgDark = Color(0xFF0D0D1A)
private val CardBg = Color(0xFF13132A)
private val GlassBorder = Color(0xFF2A2A55)
private val AccentPurple = Color(0xFF7B61FF)
private val AccentMint = Color(0xFF00E5A0)
private val TextPrimary = Color(0xFFE8E8FF)
private val TextSecondary = Color(0xFF8888BB)
private val ToggleOff = Color(0xFF2A2A55)

@Composable
fun TikTokScreen() {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    if (selectedVideoUri != null) {
        UploadPrepareScreen(
            videoUri = selectedVideoUri!!,
            onBack = { selectedVideoUri = null }
        )
    } else {
        // Layar awal untuk memilih video
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgDark, Color(0xFF0A0A1F), BgDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                )
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp).padding(horizontal = 32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Pilih Video untuk Diunggah", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UPLOAD PREPARE SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPrepareScreen(
    videoUri: Uri, // URI video hasil proses Kythera
    videoName: String = "Video_Hasil_Kythera.mp4",
    videoSizeMb: String = "",
    videoResolution: String = "1080p",
    videoRatio: String = "9:16",
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ── State Form ──────────────────────────────────────────────────────
    var judulVideo by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var hashtag by remember { mutableStateOf("#Kythera #TipsVideo #KontenKreatif") }
    var siapPublik by remember { mutableStateOf(true) }
    var izinKomentar by remember { mutableStateOf(true) }
    var izinDuet by remember { mutableStateOf(false) }
    var tambahLokasi by remember { mutableStateOf(false) }

    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    // ── Background gradient ─────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDark, Color(0xFF0A0A1F), BgDark)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
        ) {

            // ── Top Bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Siapkan Unggahan",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentMint,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "File sudah siap lewat Kythera",
                            color = AccentMint,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── Video Info Card ─────────────────────────────────────────
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail placeholder
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentPurple.copy(alpha = 0.3f), AccentMint.copy(alpha = 0.15f))
                                )
                            )
                            .border(1.dp, AccentPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = videoName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoChip(text = videoResolution)
                            InfoChip(text = "Rasio $videoRatio")
                            if (videoSizeMb.isNotEmpty()) InfoChip(text = "~$videoSizeMb MB")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Form Section ────────────────────────────────────────────
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // Judul
                    KyFormLabel("Judul Video")
                    Spacer(modifier = Modifier.height(6.dp))
                    KyTextField(
                        value = judulVideo,
                        onValueChange = { judulVideo = it },
                        placeholder = "Contoh: Trik Edit Cepat Kythera!",
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Deskripsi
                    KyFormLabel("Deskripsi & Caption")
                    Spacer(modifier = Modifier.height(6.dp))
                    KyTextField(
                        value = deskripsi,
                        onValueChange = { deskripsi = it },
                        placeholder = "Ceritakan isi konten kamu...",
                        singleLine = false,
                        minLines = 4
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hashtag
                    KyFormLabel("Tagar Populer")
                    Spacer(modifier = Modifier.height(6.dp))
                    KyTextField(
                        value = hashtag,
                        onValueChange = { hashtag = it },
                        placeholder = "#Kythera #TipsVideo #KontenKreatif",
                        singleLine = false,
                        minLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Upload Settings ─────────────────────────────────────────
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PENGATURAN UNGGAHAN",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    KyToggleRow("Siap tayang publik", siapPublik) { siapPublik = it }
                    KyDivider()
                    KyToggleRow("Izinkan komentar", izinKomentar) { izinKomentar = it }
                    KyDivider()
                    KyToggleRow("Izinkan duet & jahit", izinDuet) { izinDuet = it }
                    KyDivider()
                    KyToggleRow("Tambah lokasi", tambahLokasi) { tambahLokasi = it }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Info hint ───────────────────────────────────────────────
            Text(
                text = "💡 Caption & hashtag otomatis disalin ke clipboard saat unggah",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        // ── Bottom Action Buttons ───────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BgDark, BgDark)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tombol utama: Unggah ke TikTok
            Button(
                onClick = {
                    val caption = buildCaption(judulVideo, deskripsi, hashtag)
                    copyToClipboard(context, caption)
                    shareVideoToTikTok(context, videoUri)
                    snackbarMessage = "Caption disalin! Paste di TikTok Studio ✓"
                    showSuccessSnackbar = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple
                )
            ) {
                Text(
                    text = "🚀 Unggah ke TikTok",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tombol sekunder: Salin Caption saja
            OutlinedButton(
                onClick = {
                    val caption = buildCaption(judulVideo, deskripsi, hashtag)
                    copyToClipboard(context, caption)
                    snackbarMessage = "Caption & hashtag disalin ke clipboard"
                    showSuccessSnackbar = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentMint.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentMint
                )
            ) {
                Text(
                    text = "📋 Salin Caption & Hashtag",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Snackbar ────────────────────────────────────────────────────
        if (showSuccessSnackbar) {
            LaunchedEffect(showSuccessSnackbar) {
                kotlinx.coroutines.delay(3000)
                showSuccessSnackbar = false
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentMint.copy(alpha = 0.15f))
                        .border(1.dp, AccentMint.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = snackbarMessage,
                        color = AccentMint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// REUSABLE COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

@Composable
private fun KyFormLabel(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = TextSecondary,
                fontSize = 13.sp
            )
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPurple,
            unfocusedBorderColor = GlassBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentPurple,
            focusedContainerColor = BgDark.copy(alpha = 0.5f),
            unfocusedContainerColor = BgDark.copy(alpha = 0.3f)
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
private fun KyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentPurple,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = ToggleOff
            )
        )
    }
}

@Composable
private fun KyDivider() {
    HorizontalDivider(
        color = GlassBorder,
        thickness = 0.5.dp
    )
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AccentPurple.copy(alpha = 0.15f))
            .border(0.5.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = AccentPurple,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

private fun buildCaption(judul: String, deskripsi: String, hashtag: String): String {
    val parts = mutableListOf<String>()
    if (judul.isNotBlank()) parts.add(judul.trim())
    if (deskripsi.isNotBlank()) parts.add(deskripsi.trim())
    if (hashtag.isNotBlank()) parts.add(hashtag.trim())
    return parts.joinToString("\n\n")
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kythera Caption", text))
}

private fun shareVideoToTikTok(context: Context, videoUri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, videoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.zhiliaoapp.musically") // TikTok package Global
    }

    try {
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        // TikTok tidak terinstall atau beda package (misal: TikTok Asia com.ss.android.ugc.trill)
        // Fallback ke chooser TANPA batasan package
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(fallbackIntent, "Bagikan ke TikTok")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }
}