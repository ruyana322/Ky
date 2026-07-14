package com.d4nzxml.kythera.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Palette Warna ---
// Pakai 'private' biar aman dari error Conflicting Declarations!
private val DashBg = Color(0xFF18152B)
private val CardSolidBg = Color(0xFF26233E)
private val TextTitle = Color(0xFFF1F1F1)
private val TextDesc = Color(0xFFAAA8C2)
private val AccentCyan = Color(0xFF00CEC9)
private val ButtonDarkBg = Color(0xFF2D284B)

@Composable
fun EnhanceScreen() {// Buat nyimpen data file (URI) yang udah dipilih
var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

// Ini mesin buat ngebuka Galeri / File Manager
val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
) { uri ->
    // Pas user milih file, URI-nya disimpen ke sini
    selectedFileUri = uri
}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashBg)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // --- HEADER ---
        Text("Kythera Upscale", color = TextTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text("powered By AI", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(32.dp))

        // --- KOTAK UPLOAD DASHED ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSolidBg.copy(alpha = 0.5f))
                .drawBehind {
                    drawRoundRect(
                        color = AccentCyan.copy(alpha = 0.4f), // Warna dashed cyan transparan
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                    )
                }
                .clickable { filePickerLauncher.launch("image/*") }
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Upload Foto", color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("JPG, PNG, WEBP", color = TextDesc, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- TOMBOL TINGKATKAN RESOLUSI ---
        Button(
            onClick = { /* Eksekusi AI RealSR */ },
            modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonDarkBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = TextDesc, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tingkatkan Resolusi", color = TextDesc, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}