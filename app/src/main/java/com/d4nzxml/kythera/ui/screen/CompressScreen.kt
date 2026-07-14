package com.d4nzxml.kythera.ui.screen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Palette Warna ---
private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val InputBg = Color(0xFF1D1A31)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentGreen = Color(0xFF1DD1A1)
private val AccentOrange = Color(0xFFF39C12)


@Composable
fun CompressScreen() {// Buat nyimpen data file (URI) yang udah dipilih
var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

// Ini mesin buat ngebuka Galeri / File Manager
val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
) { uri ->
    // Pas user milih file, URI-nya disimpen ke sini
    selectedFileUri = uri
}

    var selectedTarget by remember { mutableStateOf("60%") }
    var isAudioCompression by remember { mutableStateOf(true) }
    var isRemoveMetadata by remember { mutableStateOf(false) }
    var isTwoPass by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashBg)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // Teks Deskripsi Atas
        Text("Compress Video", color = TextTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Kurangi ukuran file video dengan algoritma kompresi cerdas.",
            color = TextDesc,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // --- KOTAK UPLOAD DASHED ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSolidBg.copy(alpha = 0.5f))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF2B4752), // Warna dashed ijo gelap
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                    )
                }
                .clickable { 
    filePickerLauncher.launch("video/*") 
},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AccentGreen)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Drop video untuk compress", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Maksimal file 2GB per proses", color = TextDesc, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- TARGET KOMPRESI CARD ---
        Text("Target Kompresi", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompressionTargetCard(
                percentage = "30%", title = "Light", desc = "Kualitas hampir sama",
                accentColor = AccentGreen, isSelected = selectedTarget == "30%",
                modifier = Modifier.weight(1f), onClick = { selectedTarget = "30%" }
            )
            CompressionTargetCard(
                percentage = "60%", title = "Balanced", desc = "Recommended",
                accentColor = AccentGreen, isSelected = selectedTarget == "60%",
                modifier = Modifier.weight(1f), onClick = { selectedTarget = "60%" }
            )
            CompressionTargetCard(
                percentage = "85%", title = "Aggressive", desc = "Ukuran minimal",
                accentColor = AccentOrange, isSelected = selectedTarget == "85%",
                modifier = Modifier.weight(1f), onClick = { selectedTarget = "85%" }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SWITCH TOGGLES ---
        SwitchSettingRow(
            title = "Audio Compression",
            desc = "Kompres juga track audio",
            checked = isAudioCompression,
            onCheckedChange = { isAudioCompression = it }
        )
        SwitchSettingRow(
            title = "Remove Metadata",
            desc = "Hapus data EXIF dan metadata",
            checked = isRemoveMetadata,
            onCheckedChange = { isRemoveMetadata = it }
        )
        SwitchSettingRow(
            title = "Two-Pass Encoding",
            desc = "Kualitas lebih baik, proses lebih lama",
            checked = isTwoPass,
            onCheckedChange = { isTwoPass = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- ESTIMASI OUTPUT CARD ---
        val reductionFraction = when (selectedTarget) {
            "30%" -> 0.3f
            "60%" -> 0.6f
            "85%" -> 0.85f
            else -> 0.5f
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSolidBg)
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estimasi Output", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Pengurangan $selectedTarget", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(InputBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(reductionFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(AccentGreen)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 MB", color = TextDesc, fontSize = 10.sp)
                Text("$selectedTarget size reduction", color = AccentGreen.copy(alpha = 0.7f), fontSize = 10.sp)
                Text("Original", color = TextDesc, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- TOMBOL COMPRESS ---
        Button(
            onClick = { /* Eksekusi FFmpeg */ },
            modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(Color(0xFF00CEC9), AccentGreen)))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = DashBg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compress Video", color = DashBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// --- Komponen Pelengkap UI ---

@Composable
fun CompressionTargetCard(
    percentage: String, title: String, desc: String, 
    accentColor: Color, isSelected: Boolean, 
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.1f) else CardSolidBg)
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        if (isSelected) {
            Icon(
                Icons.Rounded.CheckCircle, 
                contentDescription = null, 
                tint = accentColor, 
                modifier = Modifier.align(Alignment.TopEnd).size(14.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(percentage, color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = TextDesc, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun SwitchSettingRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = TextDesc, fontSize = 11.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3498DB), // Warna biru pas nyala
                uncheckedThumbColor = TextDesc,
                uncheckedTrackColor = InputBg,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}