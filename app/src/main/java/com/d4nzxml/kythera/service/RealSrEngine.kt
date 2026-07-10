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

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val baseDir = getBaseDir(context)
        Log.d(TAG, "baseDir = ${baseDir.absolutePath}")

        if (binaryExists(context)) {
            Log.d(TAG, "Binary sudah ada!")
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

            // Copy libOpenCL.so dari system (cara tumuyan)
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

            // CARA TUMUYAN: cd ke folder dulu, baru chmod +x *
            val chmodCmd = "cd ${baseDir.absolutePath}; chmod +x *; echo done; ls"
            val chmodProc = Runtime.getRuntime().exec(arrayOf("sh", "-c", chmodCmd))
            val chmodLog = chmodProc.inputStream.bufferedReader().readText()
            chmodProc.waitFor()
            Log.d(TAG, "chmod result: $chmodLog")

            Log.d(TAG, "Setup sukses!")
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

                val modelsDir = File(baseDir, "models")

                // CARA TUMUYAN PERSIS:
                // cd ke folder, export LD_LIBRARY_PATH, chmod +x, lalu ./realsr-ncnn
                val shellCmd = buildString {
                    append("cd ${baseDir.absolutePath}; ")
                    append("export LD_LIBRARY_PATH=${baseDir.absolutePath}:\$LD_LIBRARY_PATH; ")
                    append("chmod +x *; ")
                    append("./realsr-ncnn ")
                    append("-i ${inputFile.absolutePath} ")
                    append("-o ${outputFile.absolutePath} ")
                    append("-m ${modelsDir.absolutePath} ")
                    append("-s 4 ")
                    append("-g 0")
                }

                Log.d(TAG, "Shell: $shellCmd")

                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", shellCmd))
                val stdout   = process.inputStream.bufferedReader().readText()
                val stderr   = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "Exit: $exitCode | stdout: $stdout | stderr: $stderr")

                if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                    inputFile.delete()
                    outputFile.delete()
                    Pair(result, null)
                } else {
                    Pair(null, buildString {
                        appendLine("EXIT CODE: $exitCode")
                        appendLine("DIR: ${baseDir.absolutePath}")
                        appendLine("FILES: ${baseDir.listFiles()?.map { "${it.name}(${it.length()})" }}")
                        appendLine("---STDOUT---")
                        appendLine(stdout.ifEmpty { "(kosong)" })
                        appendLine("---STDERR---")
                        appendLine(stderr.ifEmpty { "(kosong)" })
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
