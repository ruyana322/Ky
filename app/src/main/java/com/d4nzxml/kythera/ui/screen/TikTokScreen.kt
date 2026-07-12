package com.d4nzxml.kythera.ui.screen

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TikTokScreen() {
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // 🔥 TRIK FORCED DESKTOP MODE
                    // 1. Ubah User Agent seolah-olah ini adalah Google Chrome di PC Windows
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    
                    // 2. Paksa WebView buat ngerender halaman versi layar lebar (Desktop)
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    
                    // 3. Biar user bisa nge-zoom out/in layarnya layaknya buka PC di HP
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // 🔥 KUNCI PERBAIKAN LOGIN (Nangkap lemparan ke aplikasi asli)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        // Kalau URL-nya web biasa (http/https), biarin WebView yang muat halamannya
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            return false
                        }
                        
                        // Kalau web minta lompat ke Aplikasi TikTok asli atau aplikasi lain (Custom Intent)
                        return try {
                            val intent = if (url.startsWith("intent://")) {
                                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            } else {
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            }
                            context.startActivity(intent)
                            true
                        } catch (e: Exception) {
                            // Kalau aplikasi aslinya belum ke-install / error, tahan aja biar gak crash
                            true 
                        }
                    }
                }
                
                // 🔥 KUNCI BUAT BISA UPLOAD FILE
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = filePathCallback
                        filePickerLauncher.launch("video/*")
                        return true
                    }
                }
                
                loadUrl("https://www.tiktok.com/upload")
            }
        }
    )
}
