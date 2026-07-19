package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * MnnVideoBridge — Video Enhancement via MNN (libkythera_mnn.so)
 * Pipeline khusus video, terpisah dari photo upscale
 */
object MnnVideoBridge {

    private const val TAG = "KytheraVideoMNN"

    enum class VideoScale(val label: String, val fileName: String) {
        X2("2x Enhance", "resrgan_srvggx1_d2u2_1024.mnn"),
        X4("4x Enhance", "resrgan_srvggx1_d4u4_1024.mnn")
    }

    enum class Accelerator(val label: String, val desc: String, val flag: Int) {
        GPU("GPU OpenCL", "Cepat, butuh GPU OpenCL", 0),
        CPU("CPU",        "Kompatibel semua HP",     1)
    }

    private var isLibLoaded  = false
    private var isModelReady = false
    private var currentModel = ""
    
    // 🔥 Variabel CCTV buat nangkep pesan error biar nggak Force Close
    var lastErrorMsg: String = ""

    // ─── Load native library asli lu ───────────────────────────────────────────
    private fun loadLib(): Boolean {
        if (isLibLoaded) return true
        return try {
            // 🔥 WAJIB: Panggil induknya dulu (MNN), baru panggil kythera_mnn
            System.loadLibrary("MNN")
            System.loadLibrary("kythera_mnn")
            
            isLibLoaded = true
            lastErrorMsg = ""
            Log.d(TAG, "Native lib MNN & Kythera loaded ✅")
            true
        } catch (e: Throwable) { // 🔥 Ganti ke Throwable biar C++ crash ketangkep
            lastErrorMsg = "GAGAL LOAD LIB: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, lastErrorMsg)
            false
        }
    }

    // ─── Setup: ekstrak model ke internal storage ─────────────────────────────
    suspend fun setup(context: Context, scale: VideoScale = VideoScale.X4): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!loadLib()) return@withContext false

                val dir       = File(context.filesDir, "mnn_video").also { it.mkdirs() }
                val modelFile = File(dir, scale.fileName)

                if (!modelFile.exists() || modelFile.length() < 1000) {
                    try {
                        context.assets.open("realsr/models/${scale.fileName}").use { input ->
                            FileOutputStream(modelFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Model extracted: ${scale.fileName}")
                    } catch (e: Exception) {
                        lastErrorMsg = "GAGAL EKSTRAK MODEL: ${e.message}"
                        Log.e(TAG, lastErrorMsg)
                        return@withContext false
                    }
                }

                // 🔥 Pasang pelindung Throwable pas inisialisasi mesin C++
                val loaded = try {
                    loadModel(modelFile.absolutePath, Accelerator.GPU.flag)
                } catch (e: Throwable) {
                    lastErrorMsg = "CRASH LOAD MODEL GPU: ${e.message}"
                    Log.e(TAG, lastErrorMsg)
                    false
                }

                if (!loaded) {
                    Log.w(TAG, "GPU load gagal, coba CPU...")
                    val cpuLoaded = try {
                        loadModel(modelFile.absolutePath, Accelerator.CPU.flag)
                    } catch (e: Throwable) {
                        lastErrorMsg = "CRASH LOAD MODEL CPU: ${e.message}"
                        Log.e(TAG, lastErrorMsg)
                        false
                    }
                    
                    if (!cpuLoaded) {
                        if (lastErrorMsg.isEmpty()) lastErrorMsg = "Model load ditolak (Return False)"
                        Log.e(TAG, "Model load gagal total")
                        return@withContext false
                    }
                }

                isModelReady = true
                currentModel = scale.fileName
                lastErrorMsg = ""
                Log.d(TAG, "MNN Video Engine ready ✅ [${scale.label}]")
                true
            } catch (e: Throwable) {
                lastErrorMsg = "FATAL ERROR SETUP: ${e.message}"
                Log.e(TAG, lastErrorMsg)
                false
            }
        }

    suspend fun switchScale(context: Context, scale: VideoScale): Boolean {
        if (currentModel == scale.fileName && isModelReady) return true
        isModelReady = false
        return setup(context, scale)
    }

    suspend fun enhance(
        bitmap: Bitmap,
        accelerator: Accelerator = Accelerator.GPU
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isLibLoaded || !isModelReady) {
            lastErrorMsg = "Engine belum siap!"
            Log.e(TAG, lastErrorMsg)
            return@withContext null
        }
        try {
            // 🔥 PASTIKAN FORMAT WARNA BENAR BIAR GAK KOTAK-KOTAK / TV RUSAK
            val safe = if (bitmap.config != Bitmap.Config.ARGB_8888)
                bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

            val result = enhanceFrame(safe)

            if (safe != bitmap) safe.recycle()
            result
        } catch (e: Throwable) { // 🔥 Ganti ke Throwable biar aman
            lastErrorMsg = "CRASH ENHANCE FRAME: ${e.message}"
            Log.e(TAG, lastErrorMsg)
            null
        }
    }

    val ready: Boolean get() = isLibLoaded && isModelReady

    private external fun loadModel(modelPath: String, gpuMode: Int): Boolean
    private external fun enhanceFrame(bitmap: Bitmap): Bitmap?
    external fun release()
}
