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

                    // ✅ Scroll penuh segala arah
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = true
                    scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY
                    overScrollMode = WebView.OVER_SCROLL_ALWAYS
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                    // ✅ Cookie — DIPERBAIKI: tidak salah parameter lagi
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // ✅ Versi desktop + lebar tetap 1280px
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        loadWithOverviewMode = false
                        useWideViewPort = true
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

                        // ✅ onPageStarted — tanda fungsi DIPERBAIKI
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            // ✅ Paksa lebar 1280px & scroll berfungsi
                            view?.evaluateJavascript("""
                                (function(){
                                    document.documentElement.style.width = '1280px';
                                    document.documentElement.style.minWidth = '1280px';
                                    document.body.style.width = '1280px';
                                    document.body.style.minWidth = '1280px';
                                    document.body.style.overflowX = 'scroll';
                                    document.body.style.overflowY = 'auto';
                                    document.documentElement.style.overflowX = 'scroll';
                                    document.documentElement.style.overflowY = 'auto';
                                    document.body.style.touchAction = 'pan-x pan-y';
                                    document.body.style.position = 'static';
                                    
                                    var style = document.createElement('style');
                                    style.innerHTML = `
                                        html, body {
                                            overflow: auto !important;
                                            width: 1280px !important;
                                            max-width: none !important;
                                        }
                                        [style*="overflow: hidden"], [style*="overflow: hidden"] * {
                                            overflow: auto !important;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                    
                                    if (url.indexOf('tiktokstudio') !== -1) {
                                        var t = setInterval(function() {
                                            var fi = document.querySelector('input[type="file"]');
                                            if (fi) { clearInterval(t); fi.click(); }
                                        }, 800);
                                        setTimeout(function(){ clearInterval(t); }, 15000);
                                    }
                                })();
                            """.trimIndent(), null)
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
