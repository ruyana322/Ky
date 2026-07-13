package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    val colorBg = Color(0xFF121212)
    val colorSurface = Color(0xFF1E1E1E)
    val colorCyan = Color(0xFF00E5FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
    ) {
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

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colorCyan,
                trackColor = colorBg
            )
        }

        // 🔥 WebView yang udah di-Upgrade
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // Paksa ukuran WebView ngisi full layar
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // 🔥 Obat biar layarnya nggak melorot (Fix Viewport)
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            
                            // 🔥 Nyamar jadi Chrome Mobile asli biar gak di-block TikTok
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                        }

                        // 🔥 Wajib buat login: Izinkan penyimpanan cookie dari TikTok
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Biar render Javascript lebih sempurna
                        webChromeClient = WebChromeClient()

                                                webViewClient = object : WebViewClient() {
                            
                            // 🔥 LOGIKA BARU: Cegah TikTok kabur ke aplikasi asli
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: ""
                                
                                // Kalau URL-nya BUKAN http/https (misal tiktok://), BLOCK!
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    return true // True = WebView batalin proses loading
                                }
                                return false // False = Lanjut load halaman normal
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                cekCookieDanLanjut(onCookieScraped)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                cekCookieDanLanjut(onCookieScraped)
                            }
                        }

                        
                        loadUrl("https://www.tiktok.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

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

private fun cekCookieDanLanjut(onCookieScraped: (String) -> Unit) {
    val cookies = CookieManager.getInstance().getCookie("https://www.tiktok.com")
    if (cookies != null && cookies.contains("sessionid=")) {
        onCookieScraped(cookies)
    }
}
