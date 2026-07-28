package com.d4nzxml.kythera.superresolution

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log

/**
 * JNI bridge to librealesrganappncnn.so
 * Mirrors PixelHD's RealEsrganBridge architecture:
 *   - loadModel() called at app launch (background, Dispatchers.IO)
 *   - processImage() called per-frame, protected by Mutex in VideoUpscaleProcessor
 *   - Singleton isLoaded flag to prevent double-init (PixelHD H0.smali pattern)
 */
object RealEsrganBridge {

    private const val TAG = "RealEsrganBridge"

    init {
        try {
            System.loadLibrary("realesrganappncnn")
            Log.d(TAG, "Library realesrganappncnn loaded OK")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load library: ${e.message}")
        }
    }

    @Volatile private var isLoaded = false

    /**
     * Load both x2 and x4 models from AssetManager.
     * Safe to call multiple times — subsequent calls are no-ops.
     * Call this on Dispatchers.IO at app launch.
     */
    fun loadModel(assets: AssetManager, useGpu: Boolean = true): Boolean {
        if (isLoaded) { Log.d(TAG, "Already loaded"); return true }
        val ok = loadModelNative(assets, useGpu)
        isLoaded = ok
        Log.i(TAG, if (ok) "Model loaded OK!" else "Model load FAILED!")
        return ok
    }

    fun release() {
        releaseNative()
        isLoaded = false
        Log.d(TAG, "Engine released")
    }

    fun isReady(): Boolean = isLoaded

    // ─── Native JNI — names match C++ function signatures exactly ──────────────
    @JvmStatic private external fun loadModelNative(assets: AssetManager, useGpu: Boolean): Boolean

    /**
     * Process one frame through Real-ESRGAN.
     * @param bitmap  Input bitmap (caller recycles it after this call)
     * @param scale   2 or 4
     * @param modelName  Informational label e.g. "realesr-animevideov3"
     * @param useFaceRestore  Enable GFPGAN face restoration (stub for now)
     */
    @JvmStatic external fun processImage(
        bitmap: Bitmap,
        scale: Int,
        modelName: String,
        useFaceRestore: Boolean
    ): Bitmap?

    @JvmStatic private external fun releaseNative()
}
