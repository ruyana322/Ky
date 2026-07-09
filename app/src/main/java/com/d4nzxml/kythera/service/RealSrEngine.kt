package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RealSrEngine {

    private const val TAG = "Kythera_AI"
    private var isReady = false

    // ─── File-file yang perlu di-copy dari assets ─────────────────────────────
    private val BINARIES = listOf(
        "realsr/realsr-ncnn",
        "realsr/libncnn.so",
        "realsr/libc++_shared.so",
        "realsr/libomp.so"
    )
    private val MODELS = listOf(
        "realsr/models/x4.bin",
        "realsr/models/x4.param"
    )

    // ─── Setup: copy semua file dari assets ke filesDir ───────────────────────
    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true

        try {
            val baseDir = context.filesDir

            // Copy binary + lib
            for (assetPath in BINARIES) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                if (!outFile.exists()) {
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                }
                // Kasih permission execute ke binary utama
                if (assetPath.endsWith("realsr-ncnn")) {
                    outFile.setExecutable(true, false)
                }
            }

            // Copy model files
            for (assetPath in MODELS) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                if (!outFile.exists()) {
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                }
            }

            isReady = true
            Log.d(TAG, "Setup selesai, semua file siap!")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Gagal setup: ${e.message}")
            return@withContext false
        }
    }

    // ─── Main function: upscale bitmap ────────────────────────────────────────
    suspend fun upscale(context: Context, input: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val baseDir = context.filesDir
            val tmpDir  = context.cacheDir

            // 1. Simpen input bitmap ke file PNG sementara
            val inputFile  = File(tmpDir, "realsr_input.png")
            val outputFile = File(tmpDir, "realsr_output.png")

            // Pastiin format ARGB_8888
            val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888) {
                input.copy(Bitmap.Config.ARGB_8888, false)
            } else input

            FileOutputStream(inputFile).use { out ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 2. Siapkan path binary dan model
            val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
            val modelDir   = File(baseDir, "realsr/models").absolutePath
            val libDir     = File(baseDir, "realsr").absolutePath

            // 3. Jalankan binary via shell
            // -i input -o output -m model_dir -n x4 -s 4
            val cmd = arrayOf(
                binaryPath,
                "-i", inputFile.absolutePath,
                "-o", outputFile.absolutePath,
                "-m", modelDir,
                "-n", "x4",    // nama model (x4.param / x4.bin)
                "-s", "4",     // scale factor
                "-g", "0"      // GPU id 0 (Vulkan)
            )

            Log.d(TAG, "Menjalankan: ${cmd.joinToString(" ")}")

            val process = ProcessBuilder(*cmd)
                .apply {
                    // FIX FATAL: Gabungin lib lokal lu sama lib bawaan sistem HP!
                    val env = environment()
                    val systemLibs = env["LD_LIBRARY_PATH"] ?: ""
                    env["LD_LIBRARY_PATH"] = "$libDir:$systemLibs"
                    
                    redirectErrorStream(true)
                }
                .start()

            // Baca log output binary
            val log = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            Log.d(TAG, "Exit code: $exitCode")
            Log.d(TAG, "Log: $log")

            // 4. Baca hasil output
            if (exitCode == 0 && outputFile.exists()) {
                val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                // Bersihin file temp kalau sukses
                inputFile.delete()
                outputFile.delete()
                return@withContext result
            } else {
                // TARUH LOG DI FOLDER PICTURES/KYTHERA BIAR KELIATAN TANPA ROOT!
                val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kythera")
                if (!logDir.exists()) logDir.mkdirs()
                
                val errorLogFile = File(logDir, "kythera_error_log.txt")
                val errorMessage = "=== GAGAL EKSEKUSI BINARY ===\nEXIT CODE: $exitCode\n\nLOG TERMINAL:\n$log\n"
                errorLogFile.writeText(errorMessage)
                
                Log.e(TAG, "Binary gagal! Cek log di: ${errorLogFile.absolutePath}")
                return@withContext null
            }

        } catch (e: Exception) {
            // TARUH LOG CRASH DI FOLDER PICTURES/KYTHERA JUGA
            val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kythera")
            if (!logDir.exists()) logDir.mkdirs()
            
            val errorLogFile = File(logDir, "kythera_error_log.txt")
            val errorMessage = "=== APLIKASI CRASH ===\nPESAN ERROR:\n${e.message}\n\nSTACKTRACE:\n${e.stackTraceToString()}"
            errorLogFile.writeText(errorMessage)
            
            Log.e(TAG, "Error upscale fatal: ${e.message}", e)
            return@withContext null
        }
    }
}
