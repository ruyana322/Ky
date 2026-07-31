package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

// Gunakan Mobile UA agar TikTok Studio tampil versi mobile — pas di HP, tidak terpotong
private const val UA_MOBILE =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/126.0.0.0 Mobile Safari/537.36"

// Langsung ke Studio. Jika belum login, TikTok otomatis redirect ke Login.
private const val URL_STUDIO = "https://www.tiktok.com/tiktokstudio/upload"

// JS: auto-click file input setelah halaman Studio selesai dimuat
private const val JS_AUTO_CLICK = """
(function() {
    var tries = 0;
    var t = setInterval(function() {
        tries++;
        var fi = document.querySelector('input[type="file"]');
        if (fi) { clearInterval(t); fi.click(); return; }
        if (tries > 15) clearInterval(t);
    }, 800);
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {

                    setBackgroundColor(android.graphics.Color.WHITE)

                    // Cookie — wajib diaktifkan sebelum loadUrl
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true) // this = WebView

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = UA_MOBILE
                        // Mobile mode: tidak perlu zoom / wideViewPort
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean = false // Tetap di dalam WebView

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            // Hanya inject auto-click saat sudah di halaman Studio
                            if (url?.contains("tiktokstudio") == true &&
                                SharedUploadState.processedVideoUri != null) {
                                view?.evaluateJavascript(JS_AUTO_CLICK, null)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 90) isLoading = false
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            val uri = SharedUploadState.processedVideoUri
                            return if (uri != null) {
                                filePathCallback?.onReceiveValue(arrayOf(uri))
                                SharedUploadState.processedVideoUri = null
                                true
                            } else {
                                filePathCallback?.onReceiveValue(null)
                                true
                            }
                        }
                    }

                    loadUrl(URL_STUDIO)
                }
            }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF00E5A0)
            )
        }
    }
}
