package com.d4nzxml.kythera.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokScreen() {
    val isLoading = remember { mutableStateOf(true) }

    // ✅ State untuk pre-select sebelum buka WebView
    val videoUri = remember { mutableStateOf<Uri?>(SharedUploadState.processedVideoUri) }
    val thumbUri = remember { mutableStateOf<Uri?>(SharedUploadState.thumbnailUri) }
    val webViewReady = remember { mutableStateOf(SharedUploadState.processedVideoUri != null) }

    // Launcher pilih sampul dari galeri
    val thumbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            thumbUri.value = uri
            SharedUploadState.thumbnailUri = uri
        }
    }

    if (!webViewReady.value) {
        // ── Screen pilih file sebelum buka TikTok Studio ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Persiapan Upload TikTok",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── Pilih Video ──
                Text("📹 Video", color = Color(0xFF00E5A0), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (videoUri.value != null) {
                    Text(
                        text = "✅ Video dipilih",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Belum ada video",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── Pilih Sampul ──
                Text("🖼️ Sampul (opsional)", color = Color(0xFF00E5A0), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                if (thumbUri.value != null) {
                    AsyncImage(
                        model = thumbUri.value,
                        contentDescription = "Sampul",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("✅ Sampul dipilih", color = Color.White, fontSize = 13.sp)
                } else {
                    Text("Belum ada sampul", color = Color.Gray, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_PICK).apply {
                            type = "image/*"
                        }
                        thumbPicker.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Pilih Sampul dari Galeri", color = Color.White)
                }

                Spacer(modifier = Modifier.height(40.dp))

                // ── Tombol Lanjut ──
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Skip sampul, langsung upload
                    if (videoUri.value != null) {
                        Button(
                            onClick = { webViewReady.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Skip Sampul", color = Color.White)
                        }
                    }

                    // Lanjut dengan sampul
                    Button(
                        onClick = { webViewReady.value = true },
                        enabled = videoUri.value != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5A0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (thumbUri.value != null) "Lanjut ke TikTok ✅" else "Lanjut Tanpa Sampul",
                            color = Color(0xFF0D0D0D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

    } else {
        // ── WebView TikTok Studio ──
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

                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true

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
                            loadWithOverviewMode = false
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                            mediaPlaybackRequiresUserGesture = false
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
                                        function setViewportNormal() {
                                            var meta = document.querySelector('meta[name="viewport"]');
                                            if (meta) {
                                                meta.setAttribute('content', 'width=1200, initial-scale=0.35, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes');
                                            } else {
                                                meta = document.createElement('meta');
                                                meta.name = 'viewport';
                                                meta.content = 'width=1200, initial-scale=0.35, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes';
                                                document.head.appendChild(meta);
                                            }
                                        }

                                        function setViewportFull() {
                                            var meta = document.querySelector('meta[name="viewport"]');
                                            if (meta) {
                                                meta.setAttribute('content', 'width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=3.0, user-scalable=yes');
                                            }
                                        }

                                        var currentUrl = window.location.href;
                                        var isCoverPage = currentUrl.indexOf('cover') !== -1;
                                        if (isCoverPage) { setViewportFull(); } else { setViewportNormal(); }

                                        document.body.style.width = '';
                                        document.body.style.minWidth = isCoverPage ? '' : '1200px';
                                        document.body.style.overflow = 'auto';
                                        document.documentElement.style.overflow = 'auto';
                                        document.documentElement.style.height = 'auto';
                                        document.body.style.height = 'auto';
                                        document.body.style.touchAction = 'pan-x pan-y pinch-zoom';

                                        var lastUrl = window.location.href;
                                        setInterval(function() {
                                            if (window.location.href !== lastUrl) {
                                                lastUrl = window.location.href;
                                                var isNowCover = lastUrl.indexOf('cover') !== -1;
                                                if (isNowCover) {
                                                    setViewportFull();
                                                    document.body.style.minWidth = '';
                                                } else {
                                                    setViewportNormal();
                                                    document.body.style.minWidth = '1200px';
                                                }
                                                window.dispatchEvent(new Event('resize'));
                                            }
                                        }, 300);

                                        if (window.location.href.indexOf('tiktokstudio') !== -1 && !isCoverPage) {
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
                                val acceptTypes = fileChooserParams?.acceptTypes?.joinToString(",") ?: ""
                                val isImageRequest = acceptTypes.contains("image")

                                return if (isImageRequest && SharedUploadState.thumbnailUri != null) {
                                    // ✅ TikTok minta sampul → inject thumbnail yang sudah dipilih
                                    filePathCallback?.onReceiveValue(arrayOf(SharedUploadState.thumbnailUri!!))
                                    SharedUploadState.thumbnailUri = null
                                    true
                                } else if (!isImageRequest && SharedUploadState.processedVideoUri != null) {
                                    // ✅ TikTok minta video → inject video
                                    filePathCallback?.onReceiveValue(arrayOf(SharedUploadState.processedVideoUri!!))
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
}