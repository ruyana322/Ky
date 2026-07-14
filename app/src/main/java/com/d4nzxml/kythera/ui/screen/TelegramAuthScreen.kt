package com.d4nzxml.kythera.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAuthScreen(onVerifySuccess: () -> Unit) {
    var telegramId by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Warna Tema (Sesuaikan dengan warna Kythera lu jika perlu)
    val colorBg = Color(0xFF121212)
    val colorSurface = Color(0xFF1E1E1E)
    val colorCyan = Color(0xFF00E5FF)
    val colorCyanDark = Color(0xFF00838F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ikon Keamanan dengan Efek Glowing
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colorCyanDark.copy(alpha = 0.5f), Color.Transparent)))
                .border(2.dp, Brush.linearGradient(listOf(colorCyan, colorCyanDark)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = "Security Shield",
                tint = colorCyan,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Otorisasi Kythera",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sistem ini eksklusif. Masukkan ID Telegram Anda untuk memverifikasi akses ke dalam tools.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Kolom Input ID Telegram
        OutlinedTextField(
            value = telegramId,
            onValueChange = { input -> 
                // Cuma ngebolehin angka masuk
                if (input.all { it.isDigit() }) telegramId = input 
            },
            label = { Text("ID Telegram", color = Color.Gray) },
            leadingIcon = { 
                Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = colorCyan) 
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorCyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = colorCyan,
                focusedContainerColor = colorSurface,
                unfocusedContainerColor = colorSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Auto-Buka Bot Telegram (Warna Biru Telegram)
        Button(
            onClick = {
                try {
                    // 🔥 GANTI LINK T.ME JADI TG:// BIAR BYPASS BROWSER & DNS BLOKIR
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=KytheraTools_bot&start=getid"))
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // 🔥 Keluar notif kalau user belum install Telegram
                    Toast.makeText(context, "Aplikasi Telegram belum diinstal di HP Anda!", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE)), 
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Send, contentDescription = "Telegram", tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Dapatkan ID via Telegram", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Verifikasi/Lanjut
        Button(
            onClick = { 
                // Syarat minimal ID Telegram biasanya lebih dari 5 digit
                if (telegramId.length > 5) {
                    onVerifySuccess()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (telegramId.length > 5) colorCyan else colorSurface,
                contentColor = if (telegramId.length > 5) Color.Black else Color.Gray
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = telegramId.length > 5
        ) {
            Text("Masuk ke Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
