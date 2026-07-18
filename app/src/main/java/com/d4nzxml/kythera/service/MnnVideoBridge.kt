package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * MnnVideoBridge — Video Enhancement via MNN (libenhance.so)
 * Pipeline khusus video, direct to native byte array!
 * D4nzxml Studio © 2026
 */
object MnnVideoBridge {

    private const val TAG = "KytheraVideoMNN"

    // Model scale pilihan (Pastikan file .mnn ini beneran ada di folder assets/realsr/models/)
    enum class VideoScale(val label: String, val fileName: String, val scaleFactor: Int) {
        X2("2x Enhance", "resrgan_srvggx1_d2u2_1024.mnn", 2),
        X4("4x Enhance", "resrgan_srvggx1_d4u4_1024.mnn", 4)
    }

    // Akselerasi (Catatan: libenhance.so mungkin udah ngatur GPU/CPU otomatis di dalamnya)
    enum class Accelerator(val label: String, val desc: String, val flag: Int) {
        GPU("GPU OpenCL", "Cepat, butuh GPU OpenCL", 0),
        CPU("CPU",        "Kompatibel semua HP",     1)
    }

    private var isModelReady = false
    private var currentModel = ""
    private var engineId: Long = 0L

    // 🔥 Panggil jembatan Java murni yang lu bikin tadi
    private val enhanceNative = EnhanceNative()

    // ─── Setup: Baca model dari assets jadi ByteArray ──────────────────────────
    suspend fun setup(context: Context, scale: VideoScale = VideoScale.X4): Boolean =
        withContext(Dispatchers.IO) {
            // Kalau udah di-load dengan model yang sama, skip aja biar ngebut
            if (isModelReady && currentModel == scale.fileName) return@withContext true

            // Release model lama kalau lagi ganti resolusi
            if (engineId != 0L) {
                enhanceNative.nativeRelease(engineId)
                engineId = 0L
            }

            try {
                // Baca file .mnn langsung dari assets (NO EKSTRAK KE INTERNAL!)
                val assetPath = "realsr/models/${scale.fileName}"
                val modelBytes = context.assets.open(assetPath).readBytes()
                
                // Init ke mesin libenhance.so
                engineId = enhanceNative.nativeInit(modelBytes, modelBytes.size)
                
                if (engineId != 0L) {
                    isModelReady = true
                    currentModel = scale.fileName
                    Log.d(TAG, "MNN Video Engine ready ✅ [${scale.label}], ID: $engineId")
                    return@withContext true
                } else {
                    Log.e(TAG, "Gagal init model ke libenhance.so")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Setup gagal: ${e.message}")
                return@withContext false
            }
        }

    // ─── Switch model (2x ↔ 4x) ───────────────────────────────────────────────
    suspend fun switchScale(context: Context, scale: VideoScale): Boolean {
        if (currentModel == scale.fileName && isModelReady) return true
        isModelReady = false
        return setup(context, scale)
    }

    // ─── Enhance satu frame (No Float, Pure Byte Array!) ───────────────────────
    suspend fun enhance(
        bitmap: Bitmap,
        accelerator: Accelerator = Accelerator.GPU
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isModelReady || engineId == 0L) {
            Log.e(TAG, "Engine belum siap!")
            return@withContext null
        }
        
        try {
            val width = bitmap.width
            val height = bitmap.height

            // Pastikan format gambarnya ARGB_8888 biar ukurannya pas 4 byte per piksel
            val safe = if (bitmap.config != Bitmap.Config.ARGB_8888)
                bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

            // 🔥 A. Convert Bitmap ke ByteArray
            val byteBuffer = ByteBuffer.allocate(width * height * 4)
            safe.copyPixelsToBuffer(byteBuffer)
            val inputByteArray = byteBuffer.array()

            // 🔥 B. Hajar pakai AI libenhance.so (Color format = 1 untuk RGBA)
            val outByteArray = enhanceNative.nativeEnhance(engineId, inputByteArray, 1, width, height)

            if (outByteArray == null || outByteArray.isEmpty()) {
                Log.e(TAG, "Waduh, output dari C++ kosong!")
                return@withContext null
            }

            // 🔥 C. Hitung otomatis ukuran output (Dinamis mau itu 2x atau 4x)
            val totalOutputPixels = outByteArray.size / 4
            val scaleSq = totalOutputPixels / (width * height)
            val scale = Math.sqrt(scaleSq.toDouble()).toInt()
            
            val outWidth = width * scale
            val outHeight = height * scale

            // 🔥 D. Balikin ByteArray hasil AI ke wujud Bitmap bening
            val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            outputBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(outByteArray))

            if (safe != bitmap) safe.recycle()
            
            return@withContext outputBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Enhance error: ${e.message}")
            null
        }
    }

    val ready: Boolean get() = isModelReady && engineId != 0L

    // ─── Bersih-bersih RAM ─────────────────────────────────────────────────────
    fun release() {
        if (engineId != 0L) {
            enhanceNative.nativeRelease(engineId)
            engineId = 0L
            isModelReady = false
            currentModel = ""
            Log.d(TAG, "MNN Video Engine dirilis, RAM aman!")
        }
    }
}
