package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: ""

                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false 
                                }

                                try {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                        if (fallbackUrl != null) {
                                            view?.loadUrl(fallbackUrl)
                                        }
                                    }
                                    return true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    return true
                                }
                            }

                            // 🔥 OPER VARIABEL VIEW KE FUNGSI CEK COOKIE
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                cekCookieDanLanjut(view, onCookieScraped)
                            }

                            // 🔥 OPER VARIABEL VIEW KE FUNGSI CEK COOKIE
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                cekCookieDanLanjut(view, onCookieScraped)
                            }
                        }

                        loadUrl("https://www.tiktok.com/login")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tombol Darurat Manual
        Surface(color = colorSurface, modifier = Modifier.fillMaxWidth()) {
            Button(
                // Tombol ini kita set default kalau gagal narik nama
                onClick = { onCookieScraped("✅ Login TikTok (Verifikasi Manual)") }, 
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorCyan),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Saya Sudah Login", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// 🔥 MESIN SCRAPER JAVASCRIPT
private fun cekCookieDanLanjut(view: WebView?, onCookieScraped: (String) -> Unit) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.flush() // Maksa sinkronisasi memori ke sistem
    val cookies = cookieManager.getCookie("https://www.tiktok.com")
    
    if (cookies != null && cookies.contains("sessionid=")) {
        
        // Kalau udah login, kita suntik JS buat nyolong username dari metadata halamannya!
        val jsScript = """
            (function() {
                var match = document.documentElement.innerHTML.match(/"uniqueId":"([^"]+)"/);
                return match ? '@' + match[1] : '✅ Login TikTok (Cookie Aktif)';
            })();
        """.trimIndent()

        view?.evaluateJavascript(jsScript) { result ->
            // Bersihin tanda kutip dan karakter aneh dari hasil JS
            val cleanResult = result?.replace("\"", "")?.replace("\\", "") ?: "✅ Login TikTok (Cookie Aktif)"
            
            // 🔥 Lempar hasil akhirnya (username TikTok) ke fungsi Callback!
            onCookieScraped(cleanResult)
        }
    }
}
