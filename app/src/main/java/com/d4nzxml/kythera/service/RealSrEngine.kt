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

    // Cek versi — kalau assets berubah, copy ulang
    private const val VERSION = "v3"

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

    // Pakai codeCacheDir — satu2nya folder Android yang allow write + execute
    private fun getBaseDir(context: Context) = context.codeCacheDir

    private fun isSetupDone(context: Context): Boolean {
        val versionFile = File(getBaseDir(context), "realsr/.version")
        return versionFile.exists() && versionFile.readText().trim() == VERSION
    }

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isSetupDone(context)) {
            Log.d(TAG, "Setup sudah ada, skip copy.")
            return@withContext true
        }

        try {
            val baseDir = getBaseDir(context)
            Log.d(TAG, "Base dir: ${baseDir.absolutePath}")

            // Copy semua binary
            for (assetPath in BINARIES) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Copied: $assetPath → ${outFile.absolutePath}")
            }

            // Copy model files
            for (assetPath in MODELS) {
                val outFile = File(baseDir, assetPath)
                outFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Copied: $assetPath → ${outFile.absolutePath}")
            }

            // chmod binary via shell
            val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
            val chmodProc = Runtime.getRuntime().exec(arrayOf("chmod", "755", binaryPath))
            val chmodExit = chmodProc.waitFor()
            Log.d(TAG, "chmod exit: $chmodExit")

            // Tulis version file sebagai marker
            File(baseDir, "realsr/.version").writeText(VERSION)

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
                val baseDir    = getBaseDir(context)
                val tmpDir     = context.cacheDir
                val inputFile  = File(tmpDir, "realsr_input.png")
                val outputFile = File(tmpDir, "realsr_output.png")

                // Hapus output lama kalau ada
                if (outputFile.exists()) outputFile.delete()

                // Simpan input ke PNG
                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input
                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val binaryPath = File(baseDir, "realsr/realsr-ncnn").absolutePath
                val modelDir   = File(baseDir, "realsr/models").absolutePath
                val libDir     = File(baseDir, "realsr").absolutePath

                // Pastiin binary ada
                if (!File(binaryPath).exists()) {
                    return@withContext Pair(null,
                        "Binary tidak ada di: $binaryPath\nCoba clear app data dan coba lagi!")
                }

                // chmod lagi setiap kali
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
                        appendLine("LIB DIR: $libDir")
                        appendLine("---OUTPUT LOG---")
                        appendLine(log.ifEmpty { "(kosong — binary mungkin crash sebelum output)" })
                    }
                    Pair(null, errMsg)
                }

            } catch (e: Exception) {
                val errMsg = buildString {
                    appendLine("EXCEPTION: ${e.javaClass.simpleName}")
                    appendLine("MESSAGE: ${e.message}")
                    appendLine("---STACKTRACE---")
                    appendLine(e.stackTraceToString().take(800))
                }
                Pair(null, errMsg)
            }
        }
}
