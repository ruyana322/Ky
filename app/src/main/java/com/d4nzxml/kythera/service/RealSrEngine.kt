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

    private fun getBaseDir(context: Context): File =
        context.getDir("realsr", Context.MODE_PRIVATE)

    private fun binaryExists(context: Context): Boolean {
        val f = File(getBaseDir(context), "realsr-ncnn")
        return f.exists() && f.length() > 100
    }

    suspend fun setup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val baseDir = getBaseDir(context)
        if (binaryExists(context)) return@withContext true

        try {
            for (assetPath in BINARIES) {
                val outFile = File(baseDir, assetPath.substringAfterLast("/"))
                context.assets.open(assetPath).use { i ->
                    FileOutputStream(outFile).use { o -> i.copyTo(o) }
                }
            }
            val modelsDir = File(baseDir, "models").also { it.mkdirs() }
            for (assetPath in MODELS) {
                val outFile = File(modelsDir, assetPath.substringAfterLast("/"))
                context.assets.open(assetPath).use { i ->
                    FileOutputStream(outFile).use { o -> i.copyTo(o) }
                }
            }

            listOf(
                "/system/vendor/lib64/libOpenCL.so",
                "/system/lib64/libOpenCL.so",
                "/system/vendor/lib/libOpenCL.so",
                "/system/lib/libOpenCL.so"
            ).firstOrNull { File(it).exists() }?.let {
                File(it).copyTo(File(baseDir, "libOpenCL.so"), overwrite = true)
            }

            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "cd ${baseDir.absolutePath}; chmod +x *"))
                .waitFor()

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
                val inputFile  = File(context.cacheDir, "input.png")
                val outputFile = File(context.cacheDir, "output.png")

                if (outputFile.exists()) outputFile.delete()

                // Full resolution — tidak ada resize sama sekali (cara tumuyan)
                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input
                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val modelsDir = File(baseDir, "models")

                // Cara tumuyan persis: -c 46 (tilesize) + full path model
                val threads = Runtime.getRuntime().availableProcessors()

val shellCmd = buildString {
    append("cd ${baseDir.absolutePath}; ")
    append("export LD_LIBRARY_PATH=${baseDir.absolutePath}:\$LD_LIBRARY_PATH; ")
    append("chmod +x *; ")
    append("./realsr-ncnn ")
    append("-c 46 ")
append("-j $threads:$threads:$threads ")
      // load:proc:save threads
    append("-i ${inputFile.absolutePath} ")
    append("-o ${outputFile.absolutePath} ")
    append("-m ${modelsDir.absolutePath} ")
    append("-s 4 ")
    append("-g 0")
}
                Log.d(TAG, "CMD: $shellCmd")

                val process  = Runtime.getRuntime().exec(arrayOf("sh", "-c", shellCmd))
                val stdout   = process.inputStream.bufferedReader().readText()
                val stderr   = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "Exit=$exitCode stdout=$stdout stderr=$stderr")

                if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                    inputFile.delete()
                    outputFile.delete()
                    Pair(result, null)
                } else {
                    // Fallback CPU kalau GPU gagal
                    Log.d(TAG, "GPU gagal, coba CPU...")
                    outputFile.delete()
                    val cpuCmd = shellCmd.replace("-g 0", "-g -1")
                    val p2     = Runtime.getRuntime().exec(arrayOf("sh", "-c", cpuCmd))
                    val out2   = p2.inputStream.bufferedReader().readText()
                    val err2   = p2.errorStream.bufferedReader().readText()
                    val exit2  = p2.waitFor()

                    if (exit2 == 0 && outputFile.exists() && outputFile.length() > 0) {
                        val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                        inputFile.delete()
                        outputFile.delete()
                        Pair(result, null)
                    } else {
                        Pair(null, buildString {
                            appendLine("EXIT GPU=$exitCode CPU=$exit2")
                            appendLine("FILES: ${baseDir.listFiles()?.map { "${it.name}(${it.length()})" }}")
                            appendLine("STDOUT GPU: ${stdout.ifEmpty { "(kosong)" }}")
                            appendLine("STDERR GPU: ${stderr.ifEmpty { "(kosong)" }}")
                            appendLine("STDOUT CPU: ${out2.ifEmpty { "(kosong)" }}")
                            appendLine("STDERR CPU: ${err2.ifEmpty { "(kosong)" }}")
                        })
                    }
                }

            } catch (e: Exception) {
                Pair(null, "EXCEPTION: ${e.javaClass.simpleName}\n${e.message}\n${e.stackTraceToString().take(500)}")
            }
        }
}
