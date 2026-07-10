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

    private fun getBaseDir(context: Context): File {
        return context.getDir("realsr", Context.MODE_PRIVATE)
    }

    private fun binaryExists(context: Context): Boolean {
        val f = File(getBaseDir(context), "realsr-ncnn")
        return f.exists() && f.length() > 100
    }

    private fun runShell(vararg cmd: String): Int {
        return try {
            val p = Runtime.getRuntime().exec(cmd)
            p.waitFor()
        } catch (e: Exception) { -1 }
    }

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val baseDir = getBaseDir(context)
        Log.d(TAG, "baseDir = ${baseDir.absolutePath}")

        if (binaryExists(context)) {
            Log.d(TAG, "Binary sudah ada, skip copy.")
            return@withContext true
        }

        try {
            // Copy binary + libs flat ke baseDir
            for (assetPath in BINARIES) {
                val fileName = assetPath.substringAfterLast("/")
                val outFile  = File(baseDir, fileName)
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Copied: $fileName (${outFile.length()} bytes)")
            }

            // Copy model files
            val modelsDir = File(baseDir, "models")
            modelsDir.mkdirs()
            for (assetPath in MODELS) {
                val fileName = assetPath.substringAfterLast("/")
                val outFile  = File(modelsDir, fileName)
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Copied model: $fileName")
            }

            // Copy libOpenCL.so dari system
            val openCLPaths = listOf(
                "/system/vendor/lib64/libOpenCL.so",
                "/system/lib64/libOpenCL.so",
                "/system/vendor/lib/libOpenCL.so",
                "/system/lib/libOpenCL.so"
            )
            for (path in openCLPaths) {
                val src = File(path)
                if (src.exists()) {
                    src.copyTo(File(baseDir, "libOpenCL.so"), overwrite = true)
                    Log.d(TAG, "Copied libOpenCL from $path")
                    break
                }
            }

            // chmod binary langsung via Runtime.exec (BUKAN sh -c)
            val binaryFile = File(baseDir, "realsr-ncnn")
            val chmodExit = runShell("chmod", "777", binaryFile.absolutePath)
            Log.d(TAG, "chmod 777 exit: $chmodExit")

            // Verifikasi
            Log.d(TAG, "canExecute: ${binaryFile.canExecute()}")
            Log.d(TAG, "Files: ${baseDir.listFiles()?.map { it.name }}")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Setup gagal: ${e.message}")
            false
        }
    }

    suspend fun upscaleWithLog(context: Context, input: Bitmap): Pair<Bitmap?, String?> =
        withContext(Dispatchers.IO) {
            try {
                val baseDir    = getBaseDir(context)
                val tmpDir     = context.cacheDir
                val inputFile  = File(tmpDir, "input.png")
                val outputFile = File(tmpDir, "output.png")

                if (outputFile.exists()) outputFile.delete()

                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input
                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val binaryFile = File(baseDir, "realsr-ncnn")
                val modelsDir  = File(baseDir, "models")

                // chmod lagi sebelum eksekusi
                runShell("chmod", "777", binaryFile.absolutePath)

                Log.d(TAG, "binary exists: ${binaryFile.exists()}, size: ${binaryFile.length()}")
                Log.d(TAG, "canExecute: ${binaryFile.canExecute()}")

                // Eksekusi langsung tanpa sh -c
                val cmd = arrayOf(
                    binaryFile.absolutePath,
                    "-i", inputFile.absolutePath,
                    "-o", outputFile.absolutePath,
                    "-m", modelsDir.absolutePath,
                    "-n", "x4",
                    "-s", "4",
                    "-g", "0"
                )

                val pb = ProcessBuilder(*cmd).apply {
                    directory(baseDir)
                    val env = environment()
                    env["LD_LIBRARY_PATH"] = "${baseDir.absolutePath}:${env["LD_LIBRARY_PATH"] ?: ""}"
                    redirectErrorStream(true)
                }

                val process  = pb.start()
                val log      = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "Exit: $exitCode | Log: $log")

                if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                    inputFile.delete()
                    outputFile.delete()
                    Pair(result, null)
                } else {
                    Pair(null, buildString {
                        appendLine("EXIT CODE: $exitCode")
                        appendLine("BINARY: ${binaryFile.absolutePath}")
                        appendLine("canExecute: ${binaryFile.canExecute()}")
                        appendLine("FILES: ${baseDir.listFiles()?.map { it.name }}")
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
