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
                    
                    // User Agent Desktop (Windows 11 Chrome)
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    
                    // Wajib nyala biar web nge-zoom out ke ukuran layar
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    
                    // Zoom Controls
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            return false
                        }
                        
                        return try {
                            val intent = if (url.startsWith("intent://")) {
                                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            } else {
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            }
                            context.startActivity(intent)
                            true
                        } catch (e: Exception) {
                            true 
                        }
                    }

                    // 🔥 INI OBATNYA KANG! Dieksekusi otomatis tiap web kelar loading
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val jsInjection = """
                            // 1. Paksa lebar web jadi ukuran laptop (1280px) biar muat fitur upload
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (meta) {
                                meta.setAttribute('content', 'width=1280');
                            } else {
                                var newMeta = document.createElement('meta');
                                newMeta.name = 'viewport';
                                newMeta.content = 'width=1280';
                                document.head.appendChild(newMeta);
                            }
                            
                            // 2. Paksa jebol kunci CSS TikTok biar bisa di-scroll & digeser!
                            document.body.style.overflow = 'auto';
                            document.documentElement.style.overflow = 'auto';
                            
                            // (Opsional) Hapus elemen footer atau iklan nutupin kalau ada
                            // var footer = document.querySelector('footer'); if(footer) footer.style.display = 'none';
                        """.trimIndent()
                        
                        // Suntik JS-nya ke WebView
                        view?.evaluateJavascript(jsInjection, null)
                    }
                }
                
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
