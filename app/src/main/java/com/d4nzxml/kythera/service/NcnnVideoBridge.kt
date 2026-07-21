package com.d4nzxml.kythera.service

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log

class NcnnVideoBridge {
    // 1. Bikin ulang Enum yang dibutuhin sama UI
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

        init {
            try {
                System.loadLibrary("ncnn_bridge") 
                Log.d(TAG, "Library NCNN sukses di-load!")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Waduh, gagal nge-load library C++: ${e.message}")
            }
        }

        // 2. Bikin fungsi pembantu (wrapper) biar UI nggak error pas manggil
        fun setup(context: Context, scale: VideoScale): Boolean {
            return initEngine(context.assets)
        }

        fun switchScale(context: Context, scale: VideoScale): Boolean {
            // Placeholder: Kalau engine NCNN lu butuh muat ulang model saat ganti scale
            return true
        }

        fun enhance(frame: Bitmap, accelerator: Accelerator): Bitmap? {
            val useGpu = accelerator == Accelerator.GPU
            return processFrame(frame, useGpu)
        }

        // 3. Deklarasi fungsi JNI asli yang nyambung ke C++
        @JvmStatic external fun initEngine(assetManager: AssetManager): Boolean
        
        @JvmStatic external fun destroyEngine()
        
        // Ini jembatan paling krusial buat nge-proses gambar ke C++
        @JvmStatic external fun processFrame(bitmap: Bitmap, useGpu: Boolean): Bitmap?
    }
}
