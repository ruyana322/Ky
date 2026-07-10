package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object RealSrEngine {

    private const val TAG = "Kythera_AI"
    private var isReady = false

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

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        try {
            val baseDir = context.filesDir

            for (assetPath in BINARIES) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                if (!outFile.exists()) {
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                }
            }
            for (assetPath in MODELS) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                if (!outFile.exists()) {
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                }
            }

            // FIX: chmod via shell, bukan Java setExecutable
            val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
            val chmodResult = Runtime.getRuntime().exec(arrayOf("chmod", "755", binaryPath)).waitFor()
            Log.d(TAG, "chmod result: $chmodResult")

            isReady = true
            Log.d(TAG, "Setup selesai!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal setup: ${e.message}")
            false
        }
    }

    suspend fun upscaleWithLog(context: Context, input: Bitmap): Pair<Bitmap?, String?> =
        withContext(Dispatchers.IO) {
            try {
                val baseDir    = context.filesDir
                val tmpDir     = context.cacheDir
                val inputFile  = File(tmpDir, "realsr_input.png")
                val outputFile = File(tmpDir, "realsr_output.png")

                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input

                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
                val modelDir   = File(baseDir, "realsr/models").absolutePath
                val libDir     = File(baseDir, "realsr").absolutePath

                // chmod lagi setiap kali jalan, pastiin executable
                Runtime.getRuntime().exec(arrayOf("chmod", "755", binaryPath)).waitFor()

                val cmd = arrayOf(
                    binaryPath,
                    "-i", inputFile.absolutePath,
                    "-o", outputFile.absolutePath,
                    "-m", modelDir,
                    "-n", "x4",
                    "-s", "4",
                    "-g", "0"
                )

                Log.d(TAG, "CMD: ${cmd.joinToString(" ")}")

                val process = ProcessBuilder(*cmd)
                    .apply {
                        val env = environment()
                        val sysLibs = env["LD_LIBRARY_PATH"] ?: ""
                        env["LD_LIBRARY_PATH"] = "$libDir:$sysLibs"
                        redirectErrorStream(true)
                    }
                    .start()

                val log      = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "Exit: $exitCode | Log: $log")

                if (exitCode == 0 && outputFile.exists()) {
                    val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                    inputFile.delete()
                    outputFile.delete()
                    Pair(result, null)
                } else {
                    val errMsg = buildString {
                        appendLine("EXIT CODE: $exitCode")
                        appendLine("BINARY: $binaryPath")
                        appendLine("MODEL DIR: $modelDir")
                        appendLine("---OUTPUT LOG---")
                        appendLine(log.ifEmpty { "(kosong)" })
                    }
                    Pair(null, errMsg)
                }

            } catch (e: Exception) {
                val errMsg = buildString {
                    appendLine("EXCEPTION: ${e.javaClass.simpleName}")
                    appendLine("MESSAGE: ${e.message}")
                    appendLine(e.stackTraceToString().take(800))
                }
                Pair(null, errMsg)
            }
        }
}
