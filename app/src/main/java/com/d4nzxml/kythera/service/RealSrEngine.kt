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

                val safeBitmap = if (input.config != Bitmap.Config.ARGB_8888)
                    input.copy(Bitmap.Config.ARGB_8888, false) else input
                FileOutputStream(inputFile).use { out ->
                    safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val modelsDir = File(baseDir, "models")

                // 🔥 1. BUKA BRANKAS APLIKASI BUAT NGAMBIL RACIKAN AI DARI GIST
                val sharedPref = context.getSharedPreferences("KytheraPrefs", Context.MODE_PRIVATE)
                val aiScale = sharedPref.getString("ai_scale", "4") ?: "4"
                val cpuArgs = sharedPref.getString("ai_cpu_args", "-g -1") ?: "-g -1"

                // 🔥 2. RACIK PERINTAH UTAMA (GPU)
                // Angka 4 sekarang diganti pakai variabel aiScale dari internet
                val commandRun = "./realsr-ncnn -i '${inputFile.absolutePath}' -o '${outputFile.absolutePath}' -m '${modelsDir.absolutePath}' -s $aiScale"

                Log.d(TAG, "CMD: $commandRun")

                // 🔥 METODE EKSEKUSI TUMUYAN: Pakai ProcessBuilder dan Stdin
                val pb = ProcessBuilder("sh")
                pb.directory(baseDir)
                pb.redirectErrorStream(true) 

                val process = pb.start()
                val stdin = process.outputStream

                try {
                    stdin.write(("cd ${baseDir.absolutePath}; chmod +x *ncnn 2>/dev/null; export LD_LIBRARY_PATH=${baseDir.absolutePath};\n").toByteArray())
                    stdin.write(("$commandRun\n").toByteArray())
                    stdin.write("exit\n".toByteArray())
                    stdin.flush()
                } finally {
                    stdin.close()
                }

                val stdout = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "Exit=$exitCode stdout=$stdout")

                if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                    inputFile.delete()
                    outputFile.delete()
                    Pair(result, null)
                } else {
                    // 🔥 3. RACIK PERINTAH FALLBACK (CPU)
                    Log.d(TAG, "GPU gagal, coba CPU...")
                    outputFile.delete()

                    // Parameter -g -1 sekarang diganti pakai variabel cpuArgs dari internet
                    val cpuCommand = "$commandRun $cpuArgs" 

                    val pbCpu = ProcessBuilder("sh")
                    pbCpu.directory(baseDir)
                    pbCpu.redirectErrorStream(true)

                    val processCpu = pbCpu.start()
                    val stdinCpu = processCpu.outputStream

                    try {
                        stdinCpu.write(("cd ${baseDir.absolutePath}; chmod +x *ncnn 2>/dev/null; export LD_LIBRARY_PATH=${baseDir.absolutePath};\n").toByteArray())
                        stdinCpu.write(("$cpuCommand\n").toByteArray())
                        stdinCpu.write("exit\n".toByteArray())
                        stdinCpu.flush()
                    } finally {
                        stdinCpu.close()
                    }

                    val out2 = processCpu.inputStream.bufferedReader().readText()
                    val exit2 = processCpu.waitFor()

                    if (exit2 == 0 && outputFile.exists() && outputFile.length() > 0) {
                        val result = BitmapFactory.decodeFile(outputFile.absolutePath)
                        inputFile.delete()
                        outputFile.delete()
                        Pair(result, null)
                    } else {
                        Pair(null, buildString {
                            appendLine("EXIT GPU=$exitCode CPU=$exit2")
                            appendLine("FILES: ${baseDir.listFiles()?.map { "${it.name}(${it.length()})" }}")
                            appendLine("STDOUT/ERR GPU: ${stdout.ifEmpty { "(kosong)" }}")
                            appendLine("STDOUT/ERR CPU: ${out2.ifEmpty { "(kosong)" }}")
                        })
                    }
                }

            } catch (e: Exception) {
                Pair(null, "EXCEPTION: ${e.javaClass.simpleName}\n${e.message}\n${e.stackTraceToString().take(500)}")
            }
        }
}
