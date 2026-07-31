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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    val context = LocalContext.current
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Menyamarkan diri sebagai Desktop Chrome agar TikTok Studio Web terbuka maksimal
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    
                    // 🔥 Perbaikan Layar / Scroll
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false // Tetap di dalam WebView
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // Injeksi Javascript: Sembunyikan elemen header, sidebar, atau footer TikTok Studio yang mengganggu
                        val js = """
                            javascript:(function() {
                                try {
                                    var header = document.querySelector('header');
                                    if (header) header.style.display = 'none';
                                    
                                    var sideNav = document.querySelector('.side-nav, [class*="sidebar"]');
                                    if (sideNav) sideNav.style.display = 'none';
                                    
                                    var footer = document.querySelector('footer');
                                    if (footer) footer.style.display = 'none';
                                } catch(e) {}
                            })()
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        val uri = SharedUploadState.processedVideoUri
                        if (uri != null) {
                            // 🔥 KEUNGGULAN UTAMA: Langsung injeksi video yang sudah dipatch (Fakesample)
                            // ke dalam form upload TikTok Web tanpa membuka pemilih file Android!
                            filePathCallback?.onReceiveValue(arrayOf(uri))
                            
                            // Kosongkan agar tidak terkirim dua kali di sesi berikutnya
                            SharedUploadState.processedVideoUri = null 
                            return true
                        }
                        
                        // Jika tidak ada video (contoh: user buka menu ini manual), batalkan request
                        filePathCallback?.onReceiveValue(null)
                        return true 
                    }
                }

                loadUrl("https://www.tiktok.com/tiktokstudio/upload")
            }
        }
    )
}