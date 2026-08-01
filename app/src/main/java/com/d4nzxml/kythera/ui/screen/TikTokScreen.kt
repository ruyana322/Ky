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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    val isLoading = remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {

                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = true
                    scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY
                    overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))

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

                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                        // ✅ FIX: jangan auto zoom-out, biar WebView tidak collapse konten
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

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading.value = false

                            view?.evaluateJavascript("""
                                (function(){
                                    // ✅ FIX: viewport dengan initial-scale kecil supaya
                                    // desktop layout (1200px) muat di layar HP tanpa area kosong
                                    var meta = document.querySelector('meta[name="viewport"]');
                                    if (meta) {
                                        meta.setAttribute('content', 'width=1200, initial-scale=0.35, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes');
                                    } else {
                                        meta = document.createElement('meta');
                                        meta.name = 'viewport';
                                        meta.content = 'width=1200, initial-scale=0.35, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes';
                                        document.head.appendChild(meta);
                                    }

                                    // ✅ FIX: hapus force width di body, biar layout TikTok
                                    // render natural sesuai 1200px container-nya sendiri
                                    document.body.style.width = '';
                                    document.body.style.minWidth = '1200px';
                                    document.body.style.overflow = 'auto';
                                    document.documentElement.style.overflow = 'auto';
                                    document.body.style.touchAction = 'pan-x pan-y pinch-zoom';

                                    // ✅ FIX: pastikan tidak ada elemen yang bikin blank space
                                    document.documentElement.style.height = 'auto';
                                    document.body.style.height = 'auto';

                                    // Auto-click file input kalau di halaman upload
                                    if (window.location.href.indexOf('tiktokstudio') !== -1) {
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
                            isLoading.value = newProgress < 90
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

        if (isLoading.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF00E5A0)
            )
        }
    }
}