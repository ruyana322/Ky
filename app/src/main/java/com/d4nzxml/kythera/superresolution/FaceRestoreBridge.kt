package com.d4nzxml.kythera.superresolution

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log

/**
 * JNI bridge for face restoration (GFPGAN-NCNN).
 * Uses same librealesrganappncnn.so — no extra loadLibrary needed.
 *
 * GFPGAN not bundled in APK (too large). Stub returns passthrough.
 * Future: download-on-demand from HuggingFace on first face restore use.
 */
object FaceRestoreBridge {

    private const val TAG = "FaceRestoreBridge"
    // Library already loaded by RealEsrganBridge.init{}

    @Volatile private var isLoaded = false

    fun loadModel(assets: AssetManager): Boolean {
        val ok = loadModelNative(assets)
        isLoaded = ok
        Log.d(TAG, if (ok) "FaceRestore loaded" else "FaceRestore unavailable (stub)")
        return ok
    }

    fun processImage(bitmap: Bitmap, enhance: Boolean): Bitmap {
        return if (isLoaded) processImageNative(bitmap, enhance) ?: bitmap else bitmap
    }

    fun release() {
        if (isLoaded) { releaseNative(); isLoaded = false }
    }

    fun isReady(): Boolean = isLoaded

    @JvmStatic private external fun loadModelNative(assets: AssetManager): Boolean
    @JvmStatic private external fun processImageNative(bitmap: Bitmap, enhance: Boolean): Bitmap?
    @JvmStatic private external fun releaseNative()
}
