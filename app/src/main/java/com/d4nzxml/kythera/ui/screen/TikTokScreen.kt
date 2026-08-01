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

                    // ✅ Focus & touch
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

                        // ✅ WebGL & media untuk frame picker sampul
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

                                    // ── 1. Fungsi viewport ──
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

                                    // ── 2. Detect halaman ──
                                    var currentUrl = window.location.href;
                                    var isCoverPage = currentUrl.indexOf('cover') !== -1 
                                        || currentUrl.indexOf('sampul') !== -1
                                        || document.title.toLowerCase().indexOf('cover') !== -1;

                                    if (isCoverPage) {
                                        setViewportFull();
                                    } else {
                                        setViewportNormal();
                                    }

                                    document.body.style.width = '';
                                    document.body.style.minWidth = isCoverPage ? '' : '1200px';
                                    document.body.style.overflow = 'auto';
                                    document.documentElement.style.overflow = 'auto';
                                    document.documentElement.style.height = 'auto';
                                    document.body.style.height = 'auto';
                                    document.body.style.touchAction = 'pan-x pan-y pinch-zoom';

                                    // ── 3. Pantau URL change (modal = navigasi baru) ──
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

                                    // ── 4. MutationObserver untuk modal DOM ──
                                    var modalOpen = false;
                                    var observer = new MutationObserver(function(mutations) {
                                        mutations.forEach(function(m) {
                                            m.addedNodes.forEach(function(node) {
                                                if (node.nodeType !== 1) return;
                                                var role = node.getAttribute('role');
                                                var cls = (node.className || '').toString();
                                                var isModal = role === 'dialog'
                                                    || cls.indexOf('modal') !== -1
                                                    || cls.indexOf('drawer') !== -1
                                                    || cls.indexOf('sheet') !== -1
                                                    || cls.indexOf('overlay') !== -1;

                                                if (!isModal && node.querySelectorAll) {
                                                    var found = node.querySelectorAll('[role="dialog"],[class*="modal"],[class*="drawer"],[class*="sheet"]');
                                                    if (found.length > 0) isModal = true;
                                                }

                                                if (isModal && !modalOpen) {
                                                    modalOpen = true;
                                                    setViewportFull();
                                                    document.body.style.minWidth = '';
                                                    setTimeout(function() {
                                                        window.dispatchEvent(new Event('resize'));
                                                        if (node.scrollTop !== undefined) node.scrollTop = 0;
                                                    }, 100);
                                                }
                                            });

                                            m.removedNodes.forEach(function(node) {
                                                if (node.nodeType !== 1) return;
                                                var role = node.getAttribute('role');
                                                var cls = (node.className || '').toString();
                                                var wasModal = role === 'dialog'
                                                    || cls.indexOf('modal') !== -1
                                                    || cls.indexOf('drawer') !== -1
                                                    || cls.indexOf('sheet') !== -1
                                                    || cls.indexOf('overlay') !== -1;

                                                if (wasModal && modalOpen) {
                                                    modalOpen = false;
                                                    setTimeout(function() {
                                                        setViewportNormal();
                                                        document.body.style.minWidth = '1200px';
                                                        window.dispatchEvent(new Event('resize'));
                                                    }, 50);
                                                }
                                            });
                                        });
                                    });

                                    observer.observe(document.body, {
                                        childList: true,
                                        subtree: true
                                    });

                                    // ── 5. Auto-click file input ──
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