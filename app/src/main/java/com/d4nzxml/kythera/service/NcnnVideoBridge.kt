package com.d4nzxml.kythera.service

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.d4nzxml.kythera.superresolution.RealEsrganBridge

/**
 * Facade / compatibility wrapper around RealEsrganBridge.
 * Keeps existing VideoEnhanceScreen.kt code working without changes.
 * New code should use RealEsrganBridge directly.
 */
class NcnnVideoBridge {

    enum class Accelerator(val label: String, val desc: String) {
        CPU("CPU", "Aman tapi lambat"),
        GPU("Vulkan GPU", "Kencang (Poco X6 Pro pasti ngebut!)")
    }

    enum class VideoScale(val label: String) {
        X2("Scale 2x"),
        X4("Scale 4x")
    }

    companion object {
        private const val TAG = "NcnnBridgeKotlin"

        // ── Library is loaded by RealEsrganBridge.init{} ──
        // The new .so name is "realesrganappncnn"
        // We keep this companion for backward-compat references

        private var isInitialized = false

        /** Preload wrapper — safe to call multiple times (no-op after first). */
        fun setup(context: Context, scale: VideoScale): Boolean {
            if (isInitialized) return true
            isInitialized = RealEsrganBridge.loadModel(context.assets)
            Log.d(TAG, "setup(): engine ready = $isInitialized")
            return isInitialized
        }

        fun switchScale(context: Context, scale: VideoScale): Boolean = true

        fun enhance(frame: Bitmap, accelerator: Accelerator): Bitmap? {
            return RealEsrganBridge.processImage(frame, 2, "realesr-animevideov3", false)
        }

        fun isReady(): Boolean = RealEsrganBridge.isReady()

        // ── JNI stubs kept for any code still calling these directly ──
        @JvmStatic external fun initEngine(assetManager: AssetManager): Boolean
        @JvmStatic external fun destroyEngine()
        @JvmStatic external fun processFrame(bitmap: Bitmap, useGpu: Boolean): Bitmap?
    }
}
