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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {

                    // Hardware rendering
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#121212")) // Warna gelap agar ruang kosong tidak putih

                    // Cookie
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Desktop UA — TikTok Studio hanya ada di versi desktop
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/126.0.0.0 Safari/537.36"

                        // Wide viewport agar seluruh halaman desktop ter-render
                        loadWithOverviewMode = true
                        useWideViewPort = true

                        // Zoom support agar user bisa pinch-to-zoom
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
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            // Auto-click file input hanya saat di Studio dan ada video
                            if (url?.contains("tiktokstudio") == true &&
                                SharedUploadState.processedVideoUri != null) {
                                view?.evaluateJavascript("""
                                    (function() {
                                        var t = setInterval(function() {
                                            var fi = document.querySelector('input[type="file"]');
                                            if (fi) { clearInterval(t); fi.click(); }
                                        }, 800);
                                        setTimeout(function(){ clearInterval(t); }, 15000);
                                    })();
                                """.trimIndent(), null)
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

                    // Langsung ke Studio. Jika belum login, TikTok redirect ke Login otomatis.
                    loadUrl("https://www.tiktok.com/tiktokstudio/upload")
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
