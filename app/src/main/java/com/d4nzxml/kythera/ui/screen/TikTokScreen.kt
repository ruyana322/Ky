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
import androidx.compose.foundation.background
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

private const val UA_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/126.0.0.0 Safari/537.36"

private const val URL_STUDIO = "https://www.tiktok.com/tiktokstudio/upload"
private const val URL_LOGIN  = "https://www.tiktok.com/login"
private const val URL_HOME   = "https://www.tiktok.com"

// JS: spoof navigator agar TikTok tidak detect WebView
private val JS_SPOOF = """
(function() {
    try {
        var def = function(prop, val) {
            Object.defineProperty(navigator, prop, { get: function() { return val; }, configurable: true });
        };
        def('userAgent',     '$UA_DESKTOP');
        def('vendor',        'Google Inc.');
        def('platform',      'Win32');
        def('maxTouchPoints', 0);
        def('webdriver',     false);
        def('appVersion',    '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36');
    } catch(e) {}

    // Hapus tanda Android/WebView
    try { if (window.Android) delete window.Android; } catch(e) {}

    // Fix viewport TikTok
    try {
        var m = document.querySelector('meta[name="viewport"]');
        if (m) m.remove();
        var nm = document.createElement('meta');
        nm.name    = 'viewport';
        nm.content = 'width=1024, initial-scale=0.3, maximum-scale=5.0, user-scalable=yes';
        document.head.appendChild(nm);
    } catch(e) {}

    // Min-height fix
    try {
        var s = document.createElement('style');
        s.textContent = 'html,body,#root,#app{min-height:100vh!important}';
        document.head.appendChild(s);
    } catch(e) {}

    // Dark mode hint
    try { document.documentElement.setAttribute('data-theme','dark'); } catch(e) {}

    // Sembunyikan header & sidebar TikTok Studio
    try {
        ['header','[class*="sidebar"]','.side-nav'].forEach(function(sel) {
            var el = document.querySelector(sel);
            if (el) el.style.display = 'none';
        });
    } catch(e) {}
})();
""".trimIndent()

// JS: cek cookie login lalu redirect ke studio
private val JS_CHECK_LOGIN = """
(function() {
    var cookies = document.cookie;
    var loggedIn = cookies.indexOf('sid_guard') !== -1 ||
                   cookies.indexOf('uid_tt')    !== -1 ||
                   cookies.indexOf('sessionid') !== -1;
    if (loggedIn) {
        window.location.href = '$URL_STUDIO';
    }
})();
""".trimIndent()

// JS: auto-click file input
private val JS_AUTO_CLICK = """
(function() {
    var tries = 0;
    var t = setInterval(function() {
        tries++;
        var fi = document.querySelector('input[type="file"]');
        if (fi || tries > 20) {
            clearInterval(t);
            if (fi) fi.click();
        }
    }, 800);
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {

                    // ── Rendering ──────────────────────────────────────────
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.WHITE)

                    // ── Cookie: WAJIB aktif sebelum loadUrl ────────────────
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true) // this = WebView ✓

                    // ── Settings ───────────────────────────────────────────
                    settings.apply {
                        javaScriptEnabled                    = true
                        domStorageEnabled                    = true
                        databaseEnabled                      = true
                        allowFileAccess                      = true
                        allowContentAccess                   = true
                        mixedContentMode                     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        javaScriptCanOpenWindowsAutomatically = true
                        mediaPlaybackRequiresUserGesture     = false
                        userAgentString                      = UA_DESKTOP
                        loadWithOverviewMode                 = true
                        useWideViewPort                      = true
                        setSupportZoom(true)
                        builtInZoomControls                  = true
                        displayZoomControls                  = false
                        layoutAlgorithm                      = WebSettings.LayoutAlgorithm.NORMAL
                        cacheMode                            = WebSettings.LOAD_DEFAULT

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    // ── WebViewClient ──────────────────────────────────────
                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false // Semua URL tetap di WebView

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            // Selalu inject spoof dulu di setiap halaman
                            view?.evaluateJavascript(JS_SPOOF, null)

                            when {
                                // Sudah di Studio — inject auto-click kalau ada video
                                url?.contains("tiktokstudio") == true -> {
                                    if (SharedUploadState.processedVideoUri != null) {
                                        view?.evaluateJavascript(JS_AUTO_CLICK, null)
                                    }
                                }

                                // Di halaman TikTok biasa (home/explore) — cek login lalu redirect
                                url?.contains("tiktok.com") == true &&
                                url.contains("tiktokstudio").not() &&
                                url.contains("login").not() -> {
                                    view?.evaluateJavascript(JS_CHECK_LOGIN, null)
                                }

                                // Di halaman login — biarkan user login, setelah itu
                                // onPageFinished akan fire lagi dengan URL baru
                                url?.contains("login") == true -> { /* tunggu user login */ }
                            }

                            android.util.Log.d("KytheraWebView", "onPageFinished: $url")
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            android.util.Log.e("KytheraWebView", "Error $errorCode: $description | $failingUrl")
                        }
                    }

                    // ── WebChromeClient ────────────────────────────────────
                    webChromeClient = object : WebChromeClient() {

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            // Sembunyikan loading indicator setelah 80%+
                            if (newProgress >= 80) isLoading = false
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            val uri = SharedUploadState.processedVideoUri
                            return if (uri != null) {
                                // Injeksi URI video patch langsung ke form upload TikTok
                                filePathCallback?.onReceiveValue(arrayOf(uri))
                                SharedUploadState.processedVideoUri = null
                                true
                            } else {
                                // Batalkan — tidak ada video patch
                                filePathCallback?.onReceiveValue(null)
                                true
                            }
                        }
                    }

                    // ── Load: mulai dari home TikTok bukan langsung studio ──
                    // Kenapa? Agar cookie ter-set dulu oleh tiktok.com domain
                    // sebelum redirect ke tiktokstudio/upload.
                    // onPageFinished akan handle redirect otomatis kalau sudah login.
                    // Kalau belum login → user akan lihat halaman TikTok dan bisa login manual.
                    loadUrl(URL_HOME)
                }
            }
        )

        // Loading indicator sementara halaman belum siap
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF00E5A0) // Kythera green mint
            )
        }
    }
}
