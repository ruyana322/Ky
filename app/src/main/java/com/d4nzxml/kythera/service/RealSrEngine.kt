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
    private const val VERSION = "v4"

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

    private fun getBaseDir(context: Context) = context.codeCacheDir

    private fun binaryExists(context: Context): Boolean {
        val binary = File(getBaseDir(context), "realsr/realsr-ncnn")
        return binary.exists() && binary.length() > 0
    }

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val baseDir = getBaseDir(context)
        Log.d(TAG, "codeCacheDir = ${baseDir.absolutePath}")

        // Selalu copy ulang kalau binary tidak ada
        if (binaryExists(context)) {
            Log.d(TAG, "Binary sudah ada, skip copy.")
            return@withContext true
        }

        Log.d(TAG, "Binary belum ada, mulai copy dari assets...")

        try {
            for (assetPath in BINARIES) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "OK: ${outFile.absolutePath} (${outFile.length()} bytes)")
            }

            for (assetPath in MODELS) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "OK: ${outFile.absolutePath} (${outFile.length()} bytes)")
            }

            // chmod via shell
            val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
            Runtime.getRuntime().exec(arrayOf("chmod", "755", binaryPath)).waitFor()

            // Verifikasi binary ada setelah copy
            if (!binaryExists(context)) {
                Log.e(TAG, "Copy selesai tapi binary masih tidak ada!")
                return@withContext false
            }

            Log.d(TAG, "Setup sukses!")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Gagal setup: ${e.message}")
            false
        }
    }

    suspend fun upscaleWithLog(context: Context, input: Bitmap): Pair<Bitmap?, String?> =
        withContext(Dispatchers.IO) {
            try {
                val baseDir    = getBaseDir(context)
                val tmpDir     = context.cacheDir
                val inputFile  = File(tmpDir, "realsr_input.png")
                val outputFile = File(tmpDir, "realsr_output.png")

                if (outputFile.exists()) outputFile.delete()

                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input
                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
                val modelDir   = File(baseDir, "realsr/models").absolutePath
                val libDir     = File(baseDir, "realsr").absolutePath

                // Validasi semua file ada
                val missing = mutableListOf<String>()
                if (!File(binaryPath).exists()) missing.add("realsr-ncnn")
                if (!File(modelDir, "x4.bin").exists()) missing.add("x4.bin")
                if (!File(modelDir, "x4.param").exists()) missing.add("x4.param")
                if (missing.isNotEmpty()) {
                    return@withContext Pair(null,
                        "File hilang: ${missing.joinToString()}\n" +
                        "Base dir: ${baseDir.absolutePath}\n" +
                        "Coba clear app data & install ulang!")
                }

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
                    Pair(null, buildString {
                        appendLine("EXIT CODE: $exitCode")
                        appendLine("BINARY: $binaryPath (${File(binaryPath).length()} bytes)")
                        appendLine("MODEL DIR: $modelDir")
                        appendLine("LIB DIR: $libDir")
                        appendLine("---LOG---")
                        appendLine(log.ifEmpty { "(kosong)" })
                    })
                }

            } catch (e: Exception) {
                Pair(null, buildString {
                    appendLine("EXCEPTION: ${e.javaClass.simpleName}")
                    appendLine("MESSAGE: ${e.message}")
                    appendLine(e.stackTraceToString().take(600))
                })
            }
        }
}
