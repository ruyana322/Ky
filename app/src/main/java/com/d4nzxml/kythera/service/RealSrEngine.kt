package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RealSrEngine {
    
    private var isInitialized = false

    init {
        try {
            System.loadLibrary("kythera_ai")
            Log.d("Kythera_AI", "Mesin C++ NCNN sukses terpasang!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("Kythera_AI", "Gagal manggil mesin C++: ${e.message}")
        }
    }

    private external fun initModel(paramPath: String, binPath: String): Boolean
    
    // Ini fungsi pamungkas buat nge-HD-in potonya
    external fun processBitmap(bitmap: Bitmap): Bitmap?

    // Fungsi buat manasin mesin dan nyiapin model dari Assets
    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            val paramFile = File(context.cacheDir, "realesrgan.param")
            val binFile = File(context.cacheDir, "realesrgan.bin")

            // Sedot file dari Assets ke Cache (kalau belum ada)
            if (!paramFile.exists() || !binFile.exists()) {
                context.assets.open("models/realesrgan.param").use { input ->
                    FileOutputStream(paramFile).use { output -> input.copyTo(output) }
                }
                context.assets.open("models/realesrgan.bin").use { input ->
                    FileOutputStream(binFile).use { output -> input.copyTo(output) }
                }
            }

            // Oper jalurnya ke C++
            isInitialized = initModel(paramFile.absolutePath, binFile.absolutePath)
            return@withContext isInitialized
        } catch (e: Exception) {
            Log.e("Kythera_AI", "Gagal nyiapin model: ${e.message}")
            return@withContext false
        }
    }
}
