package com.d4nzxml.kythera.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.system.exitProcess

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    // 1. Panggil lagi brankas memori yang udah kita buat tadi
    val sharedPref = context.getSharedPreferences("KytheraPrefs", Context.MODE_PRIVATE)

    // 2. Baca isi brankasnya
    val isTiktokLinked = sharedPref.getBoolean("is_tiktok_verified", false)
    val tiktokCookie = sharedPref.getString("tiktok_cookie", "Belum ada cookie") ?: ""

    // Tema Warna Kythera
    val colorBg = Color(0xFF121212)
    val colorSurface = Color(0xFF1E1E1E)
    val colorCyan = Color(0xFF00E5FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // 🔥 Lingkaran Foto Profil (Sementara pakai Icon, nanti bisa diganti gambar dari URL)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, colorCyan, CircleShape)
                .background(colorSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = "Profile Picture",
                tint = Color.Gray,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 🔥 Nama User
        Text(
            text = "Kythera Modder",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Status Koneksi
        Text(
            text = if (isTiktokLinked) "Status: TikTok Terhubung 🟢" else "Status: Belum Terhubung 🔴",
            color = if (isTiktokLinked) Color.Green else Color.Red,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 🔥 Card Info Data Rahasia
        Card(
            colors = CardDefaults.cardColors(containerColor = colorSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Info Sistem", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Session ID TikTok:", color = Color.Gray, fontSize = 12.sp)
                Text(
                    // Potong teks cookie biar layarnya nggak penuh
                    text = if (tiktokCookie.length > 30) tiktokCookie.take(30) + "..." else tiktokCookie,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 🔥 Tombol Logout (Reset Brankas)
        Button(
            onClick = {
                // Hapus semua isi brankas!
                sharedPref.edit().clear().apply()
                // Tutup paksa aplikasi biar pas user buka lagi, diminta login dari awal
                exitProcess(0)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Warna Merah
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.ExitToApp, contentDescription = "Logout", tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Logout & Hapus Sesi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
