package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

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
    
    // Fungsi dasar C++ (Ngeproses 1 potongan / tile)
    external fun processBitmap(bitmap: Bitmap): Bitmap?

    // =================================================================
    // JURUS TILING: Potong gambar jadi 400x400, proses, lalu jahit lagi
    // =================================================================
    suspend fun processBitmapTiled(input: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val tileSize = 400
        val scale = 4 // Karena kita pakai model x4plus / anime-x4
        
        val outWidth = input.width * scale
        val outHeight = input.height * scale
        
        // Bikin kanvas kosong sebesar ukuran gambar akhir
        val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // Loop buat motong-motong gambar dari atas ke bawah, kiri ke kanan
        for (y in 0 until input.height step tileSize) {
            for (x in 0 until input.width step tileSize) {
                // Cari tau sisa ukuran biar potongannya pas di ujung gambar
                val w = min(tileSize, input.width - x)
                val h = min(tileSize, input.height - y)
                
                // Gunting Poto
                val tile = Bitmap.createBitmap(input, x, y, w, h)

                // Lempar potongan ke C++
                val processedTile = processBitmap(tile)

                // Kalau sukses, tempel di kanvas, sesuaikan koordinatnya (dikali 4)
                if (processedTile != null) {
                    canvas.drawBitmap(processedTile, (x * scale).toFloat(), (y * scale).toFloat(), null)
                    processedTile.recycle() // Bersihin RAM Tile HD
                }
                tile.recycle() // Bersihin RAM Tile Ori
            }
        }
        return@withContext outputBitmap
    }

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
