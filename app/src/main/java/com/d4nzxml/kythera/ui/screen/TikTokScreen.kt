package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {

                // ─── Rendering ──────────────────────────────────────────────
                // Hardware acceleration wajib ON agar WebView tidak blank putih
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                // ─── Settings ────────────────────────────────────────────────
                settings.apply {
                    javaScriptEnabled              = true
                    domStorageEnabled              = true
                    databaseEnabled                = true
                    allowFileAccess                = true
                    allowContentAccess             = true
                    mixedContentMode               = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    javaScriptCanOpenWindowsAutomatically = true
                    mediaPlaybackRequiresUserGesture = false

                    // Viewport / zoom
                    loadWithOverviewMode           = true
                    useWideViewPort                = true
                    setSupportZoom(true)
                    builtInZoomControls            = true
                    displayZoomControls            = false
                    layoutAlgorithm                = WebSettings.LayoutAlgorithm.NORMAL

                    // ─── KUNCI: Spoof UA sebelum halaman dimuat ───────────────
                    // Harus set SEBELUM loadUrl, bukan di onPageFinished
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/126.0.0.0 Safari/537.36"

                    // Nonaktifkan safe browsing agar tidak redirect ke error page
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }

                    // Cache policy: load fresh, tapi tetap manfaatkan cache disk
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                // ─── WebViewClient ───────────────────────────────────────────
                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false // Semua navigasi tetap di dalam WebView

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // Spoof navigator properties via JS ─ sembunyikan fingerprint WebView
                        val spoofJs = """
                            (function() {
                                // 1. Spoof navigator agar tidak ketahuan WebView
                                try {
                                    Object.defineProperty(navigator, 'userAgent', {
                                        get: () => 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
                                    });
                                    Object.defineProperty(navigator, 'vendor', {
                                        get: () => 'Google Inc.'
                                    });
                                    Object.defineProperty(navigator, 'platform', {
                                        get: () => 'Win32'
                                    });
                                    Object.defineProperty(navigator, 'maxTouchPoints', {
                                        get: () => 0
                                    });
                                    Object.defineProperty(navigator, 'webdriver', {
                                        get: () => false
                                    });
                                } catch(e) {}

                                // 2. Hapus tanda-tanda Android/WebView
                                try {
                                    if (window.Android) delete window.Android;
                                } catch(e) {}

                                // 3. Fix viewport ─ hapus pembatasan zoom TikTok
                                try {
                                    var meta = document.querySelector('meta[name="viewport"]');
                                    if (meta) meta.remove();
                                    var newMeta = document.createElement('meta');
                                    newMeta.name    = 'viewport';
                                    newMeta.content = 'width=1024, initial-scale=0.3, maximum-scale=5.0, user-scalable=yes';
                                    document.head.appendChild(newMeta);
                                } catch(e) {}

                                // 4. CSS: paksa min-height 100vh agar layout tidak terpotong
                                try {
                                    var style = document.createElement('style');
                                    style.textContent = 'html, body, #root, #app { min-height: 100vh !important; }';
                                    document.head.appendChild(style);
                                } catch(e) {}

                                // 5. Dark mode hint
                                try {
                                    document.documentElement.setAttribute('data-theme', 'dark');
                                } catch(e) {}

                                // 6. Sembunyikan header & sidebar TikTok Studio
                                try {
                                    var header = document.querySelector('header');
                                    if (header) header.style.display = 'none';
                                    var sidebar = document.querySelector('.side-nav, [class*="sidebar"]');
                                    if (sidebar) sidebar.style.display = 'none';
                                } catch(e) {}
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(spoofJs, null)

                        // Auto-click file input hanya kalau ada video yang sudah dipatch
                        if (SharedUploadState.processedVideoUri != null) {
                            val autoClickJs = """
                                (function() {
                                    var maxTry = 0;
                                    var interval = setInterval(function() {
                                        maxTry++;
                                        var fileInput = document.querySelector('input[type="file"]');
                                        if (fileInput || maxTry > 15) {
                                            clearInterval(interval);
                                            if (fileInput) fileInput.click();
                                        }
                                    }, 800);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(autoClickJs, null)
                        }
                    }

                    // Debug: tangkap error load halaman
                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        android.util.Log.e("KytheraWebView", "Error $errorCode: $description | url=$failingUrl")
                    }
                }

                // ─── WebChromeClient ─────────────────────────────────────────
                webChromeClient = object : WebChromeClient() {

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        val uri = SharedUploadState.processedVideoUri
                        return if (uri != null) {
                            // Langsung injeksi URI video yang sudah dipatch ke form upload TikTok
                            filePathCallback?.onReceiveValue(arrayOf(uri))
                            SharedUploadState.processedVideoUri = null
                            true
                        } else {
                            // Tidak ada video — batalkan agar tidak stuck
                            filePathCallback?.onReceiveValue(null)
                            true
                        }
                    }
                }

                // ─── Load URL ─────────────────────────────────────────────────
                loadUrl("https://www.tiktok.com/tiktokstudio/upload")
            }
        }
    )
}
