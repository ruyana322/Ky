package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * MnnVideoBridge — Video Enhancement via MNN (libMNN.so)
 * Pipeline khusus video, terpisah dari photo upscale
 * D4nzxml Studio © 2026
 */
object MnnVideoBridge {

    private const val TAG = "KytheraVideoMNN"

    // Model scale pilihan
    enum class VideoScale(val label: String, val fileName: String) {
        X2("2x Enhance", "resrgan_srvggx1_d2u2_1024.mnn"),
        X4("4x Enhance", "resrgan_srvggx1_d4u4_1024.mnn")
    }

    // Akselerasi
    enum class Accelerator(val label: String, val desc: String, val flag: Int) {
        GPU("GPU OpenCL", "Cepat, butuh GPU OpenCL", 0),
        CPU("CPU",        "Kompatibel semua HP",     1)
    }

    private var isLibLoaded  = false
    private var isModelReady = false
    private var currentModel = ""

    // ─── Load native library ───────────────────────────────────────────────────
    private fun loadLib(): Boolean {
        if (isLibLoaded) return true
        return try {
            System.loadLibrary("kythera_mnn")
            isLibLoaded = true
            Log.d(TAG, "Native lib loaded ✅")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed load lib: ${e.message}")
            false
        }
    }

    // ─── Setup: ekstrak libMNN.so + model ke internal storage ─────────────────
    suspend fun setup(context: Context, scale: VideoScale = VideoScale.X4): Boolean =
        withContext(Dispatchers.IO) {
            if (!loadLib()) return@withContext false

            val dir       = File(context.filesDir, "mnn_video").also { it.mkdirs() }
            val modelFile = File(dir, scale.fileName)

            // Ekstrak model dari assets kalau belum ada
            if (!modelFile.exists() || modelFile.length() < 1000) {
                try {
                    context.assets.open("realsr/models/${scale.fileName}").use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Model extracted: ${scale.fileName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed extract model: ${e.message}")
                    return@withContext false
                }
            }

            // Load model ke MNN
            val loaded = loadModel(modelFile.absolutePath, Accelerator.GPU.flag)
            if (!loaded) {
                Log.w(TAG, "GPU load gagal, coba CPU...")
                val cpuLoaded = loadModel(modelFile.absolutePath, Accelerator.CPU.flag)
                if (!cpuLoaded) {
                    Log.e(TAG, "Model load gagal total")
                    return@withContext false
                }
            }

            isModelReady = true
            currentModel = scale.fileName
            Log.d(TAG, "MNN Video Engine ready ✅ [${scale.label}]")
            true
        }

    // ─── Switch model (2x ↔ 4x) ───────────────────────────────────────────────
    suspend fun switchScale(context: Context, scale: VideoScale): Boolean {
        if (currentModel == scale.fileName && isModelReady) return true
        isModelReady = false
        return setup(context, scale)
    }

    // ─── Enhance satu frame ────────────────────────────────────────────────────
    suspend fun enhance(
        bitmap: Bitmap,
        accelerator: Accelerator = Accelerator.GPU
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isLibLoaded || !isModelReady) {
            Log.e(TAG, "Engine belum siap!")
            return@withContext null
        }
        try {
            // Pastikan ARGB_8888
            val safe = if (bitmap.config != Bitmap.Config.ARGB_8888)
                bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

            val result = enhanceFrame(safe)

            if (safe != bitmap) safe.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Enhance error: ${e.message}")
            null
        }
    }

    val ready: Boolean get() = isLibLoaded && isModelReady

    // ─── JNI Native Declarations ───────────────────────────────────────────────
    private external fun loadModel(modelPath: String, gpuMode: Int): Boolean
    private external fun enhanceFrame(bitmap: Bitmap): Bitmap?
    external fun release()
}
