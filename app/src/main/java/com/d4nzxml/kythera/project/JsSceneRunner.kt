package com.d4nzxml.kythera.project

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Menjalankan skrip JS pengguna untuk menghasilkan JSON scene.
 *
 * Skrip dijalankan di WebView tersembunyi yang diisolasi:
 * - tanpa jembatan ke kode app (tidak ada addJavascriptInterface),
 * - jaringan diblokir, akses file dan content dimatikan,
 * - ada batas waktu, dan WebView dihancurkan setelah selesai.
 * Skrip tidak menggambar apa pun: ia hanya mendeskripsikan scene lewat API [JsScripts.PRELUDE].
 * Penggambaran tetap dikerjakan SceneRenderer, sehingga preview dan hasil ekspor identik.
 */
class JsSceneRunner(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Mengembalikan JSON scene (format SceneJson). Melempar IllegalArgumentException dengan pesan yang bisa ditampilkan. */
    suspend fun run(code: String): String {
        require(code.isNotBlank()) { "Skrip masih kosong" }
        require(code.length <= MAX_CODE_CHARS) { "Skrip terlalu panjang (maks $MAX_CODE_CHARS karakter)" }

        val raw: String? = try {
            withTimeout(TIMEOUT_MS) { evaluate(buildScript(code)) }
        } catch (e: TimeoutCancellationException) {
            throw IllegalArgumentException("Skrip berjalan terlalu lama. Cek loop yang tidak berujung.")
        }
        if (raw == null || raw == "null") throw IllegalArgumentException("Skrip tidak menghasilkan scene")

        val envelope: String = try {
            JSONTokener(raw).nextValue() as? String
        } catch (e: JSONException) {
            null
        } ?: throw IllegalArgumentException("Hasil skrip tidak valid")
        require(envelope.length <= MAX_RESULT_CHARS) { "Hasil scene terlalu besar" }

        val result = try {
            JSONObject(envelope)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Hasil skrip tidak valid")
        }
        if (!result.optBoolean("ok")) {
            throw IllegalArgumentException("Error di skrip: " + result.optString("error", "tidak diketahui"))
        }
        val data = result.optJSONObject("data")
            ?: throw IllegalArgumentException("Skrip tidak menghasilkan scene")
        return data.toString()
    }

    private fun buildScript(code: String): String =
        "(function(){try{" + JsScripts.PRELUDE + ";" +
            "(0,eval)(" + JSONObject.quote(code) + ");" +
            "return JSON.stringify({ok:true,data:__jsvs.build()});" +
            "}catch(e){return JSON.stringify({ok:false,error:String(e&&e.message?e.message:e)});}})()"

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<String?> { cont ->
            val webView = WebView(appContext)
            val main = Handler(Looper.getMainLooper())
            fun dispose() {
                main.post {
                    runCatching {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                blockNetworkLoads = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
            }

            var started = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (started || view == null) return
                    started = true
                    view.evaluateJavascript(script) { value ->
                        dispose()
                        if (cont.isActive) cont.resume(value)
                    }
                }
            }
            cont.invokeOnCancellation { dispose() }
            webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "UTF-8", null)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
        const val MAX_CODE_CHARS = 200_000
        const val MAX_RESULT_CHARS = 3_000_000
    }
}
