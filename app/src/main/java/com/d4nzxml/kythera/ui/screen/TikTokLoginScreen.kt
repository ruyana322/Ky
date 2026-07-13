package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokLoginScreen(onCookieScraped: (String) -> Unit) {
    var isLoading by remember { mutableStateOf(true) }

    // Warna Tema Kythera
    val colorBg = Color(0xFF121212)
    val colorSurface = Color(0xFF1E1E1E)
    val colorCyan = Color(0xFF00E5FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
    ) {
        // 🔥 Header Bar
        Surface(
            color = colorSurface,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tautkan Akun TikTok",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Login untuk mengaktifkan fitur Auto-Upload Kythera",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // 🔥 Loading Bar Animasi (Muncul pas web lagi loading)
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colorCyan,
                trackColor = colorBg
            )
        }

        // 🔥 WebView TikTok (Jantung Utamanya)
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // Aktifin JS & Storage biar TikTok bisa jalan normal
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        // Sedikit trik User-Agent biar gak gampang dicurigai bot sama TikTok
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                
                                // Cek cookie tiap kali page beres loading
                                cekCookieDanLanjut(onCookieScraped)
                            }

                            // Karena TikTok itu SPA (Single Page Application), kadang URL ganti tanpa loading ulang
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                cekCookieDanLanjut(onCookieScraped)
                            }
                        }
                        
                        // Langsung tembak ke halaman login
                        loadUrl("https://www.tiktok.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 🔥 Tombol Manual (Buat jaga-jaga kalau auto-detect gagal)
        Surface(
            color = colorSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { cekCookieDanLanjut(onCookieScraped) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorCyan),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Saya Sudah Login", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// Fungsi buat nyedot cookie dari browser
private fun cekCookieDanLanjut(onCookieScraped: (String) -> Unit) {
    val cookies = CookieManager.getInstance().getCookie("https://www.tiktok.com")
    // Kalau cookie udah dapet dan ada 'sessionid' (tanda udah login)
    if (cookies != null && cookies.contains("sessionid=")) {
        onCookieScraped(cookies)
    }
}
