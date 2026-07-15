package com.d4nzxml.kythera.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
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
import com.d4nzxml.kythera.service.CctvService
import kotlinx.coroutines.launch

// Fungsi pendeteksi status login via Cookie
fun getTiktokData(): String {
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie("https://www.tiktok.com")
    return if (!cookies.isNullOrEmpty()) {
        "✅ Aktif (Cookie Terdeteksi)" 
    } else {
        "❌ Belum/Tidak Login"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAuthScreen(onVerifySuccess: () -> Unit) {
    var telegramId by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope() 

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
        // Ikon Keamanan
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colorCyanDark.copy(alpha = 0.5f), Color.Transparent)))
                .border(2.dp, Brush.linearGradient(listOf(colorCyan, colorCyanDark)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = colorCyan, modifier = Modifier.size(50.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Otorisasi Kythera", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Sistem eksklusif. Masukkan ID Telegram untuk verifikasi akses.", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(40.dp))

        // Input ID
        OutlinedTextField(
            value = telegramId,
            onValueChange = { if (it.all { char -> char.isDigit() }) telegramId = it },
            label = { Text("ID Telegram", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = colorCyan) },
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

        // Tombol Get ID
        Button(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=KytheraTools_bot&start=getid"))
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "Telegram belum diinstal!", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE)), 
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Dapatkan ID via Telegram", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Masuk
        Button(
            onClick = { 
                if (telegramId.length > 5) {
                    scope.launch {
                        // CCTV mencatat ID + Status Cookie TikTok
                        CctvService.laporLogin(
                            telegramId = telegramId,
                            akunTiktok = getTiktokData() 
                        )
                    }
                    onVerifySuccess()
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
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
