package com.d4nzxml.kythera.service

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─── Result ───────────────────────────────────────────────────────────────────
data class FfmpegResult(
    val success: Boolean,
    val outputPath: String = "",
    val errorMessage: String? = null,
)

// ─── Service ──────────────────────────────────────────────────────────────────
class FfmpegService(private val context: Context) {

    private fun tempPath(filename: String): String {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return "${dir.absolutePath}/$filename"
    }

    // ─── 1. CONVERTER ────────────────────────────────────────────────────────
    suspend fun convertVideo(
        inputPath: String,
        targetFormat: String,
        bitrateM: Int,
        codec: String,
        resolution: String,
        onProgress: ((Double) -> Unit)? = null,
    ): FfmpegResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val outputPath = tempPath("Kythera_Convert_$ts.$targetFormat")

        val scaleFilter = if (resolution == "original") ""
        else "-vf \"scale=$resolution:flags=lanczos\""

        val parts = mutableListOf(
            "-y", "-ignore_unknown",
            "-i \"$inputPath\"",
        )
        if (scaleFilter.isNotEmpty()) parts.add(scaleFilter)
        parts += listOf(
            "-c:v $codec",
            "-b:v ${bitrateM}M",
            "-c:a aac", "-b:a 192k",
            "-movflags +faststart",
            "\"$outputPath\""
        )

        execute(parts.joinToString(" "), outputPath, onProgress)
    }

    // ─── 2. COMPRESS ─────────────────────────────────────────────────────────
    suspend fun compressVideo(
        inputPath: String,
        compressPercent: Int,
        compressAudio: Boolean,
        removeMetadata: Boolean,
        twoPass: Boolean,
        onProgress: ((Double) -> Unit)? = null,
    ): FfmpegResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val outputPath = tempPath("Kythera_Compress_$ts.mp4")

        val crf = when (compressPercent) {
            30   -> 23
            60   -> 28
            else -> 35
        }

        val audioArgs = if (compressAudio) "-c:a aac -b:a 128k" else "-c:a copy"
        val metaArgs  = if (removeMetadata) "-map_metadata -1" else ""

        if (twoPass) {
            val pass1 = "-y -ignore_unknown -i \"$inputPath\" " +
                    "-c:v libx264 -crf $crf -b:v 0 -pass 1 -an $metaArgs -f mp4 /dev/null"
            val r1 = execute(pass1, "", onProgress)
            if (!r1.success) return@withContext r1

            val pass2 = "-y -ignore_unknown -i \"$inputPath\" " +
                    "-c:v libx264 -crf $crf -b:v 0 -pass 2 " +
                    "$audioArgs $metaArgs -movflags +faststart \"$outputPath\""
            execute(pass2, outputPath, onProgress)
        } else {
            val cmd = "-y -ignore_unknown -i \"$inputPath\" " +
                    "-c:v libx264 -crf $crf $audioArgs $metaArgs " +
                    "-movflags +faststart \"$outputPath\""
            execute(cmd, outputPath, onProgress)
        }
    }

    // ─── 3. PATCH METADATA ───────────────────────────────────────────────────
    suspend fun patchMetadata(
        inputPath: String,
        title: String,
        description: String,
        author: String,
        year: String,
        onProgress: ((Double) -> Unit)? = null,
    ): FfmpegResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val outputPath = tempPath("Kythera_Patch_$ts.mp4")

        val parts = mutableListOf("-y", "-ignore_unknown", "-i \"$inputPath\"", "-c copy")
        if (title.isNotEmpty())       parts.add("-metadata title=\"$title\"")
        if (description.isNotEmpty()) parts.add("-metadata description=\"$description\"")
        if (author.isNotEmpty())      parts.add("-metadata artist=\"$author\"")
        if (year.isNotEmpty())        parts.add("-metadata date=\"$year\"")
        parts += listOf("-movflags +faststart", "\"$outputPath\"")

        execute(parts.joinToString(" "), outputPath, onProgress)
    }

    // ─── 4. PATCH WATERMARK ──────────────────────────────────────────────────
    suspend fun patchWatermark(
        inputPath: String,
        watermarkText: String,
        onProgress: ((Double) -> Unit)? = null,
    ): FfmpegResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val outputPath = tempPath("Kythera_WM_$ts.mp4")

        val cmd = "-y -ignore_unknown -i \"$inputPath\" " +
                "-vf \"drawtext=text='$watermarkText':fontcolor=white@0.6:" +
                "fontsize=28:x=w-tw-20:y=h-th-20:shadowcolor=black:shadowx=1:shadowy=1\" " +
                "-c:a copy -movflags +faststart \"$outputPath\""

        execute(cmd, outputPath, onProgress)
    }

    // ─── Internal executor ───────────────────────────────────────────────────
    private fun execute(
        cmd: String,
        outputPath: String,
        onProgress: ((Double) -> Unit)?,
    ): FfmpegResult {
        FFmpegKitConfig.enableStatisticsCallback { _ ->
            onProgress?.invoke(-1.0) // indeterminate
        }

        val session    = FFmpegKit.execute(cmd)
        val returnCode = session.returnCode
        
        // Baris disableStatistics() udah dihapus sesuai saran Kimi

        return if (ReturnCode.isSuccess(returnCode)) {
            FfmpegResult(success = true, outputPath = outputPath)
        } else {
            val logs = session.allLogsAsString ?: "No logs"
            val tail = if (logs.length > 200) logs.takeLast(200) else logs
            FfmpegResult(success = false, errorMessage = tail)
        }
    }

    // ─── Utils ───────────────────────────────────────────────────────────────
    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 * 1024          -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024  -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else                          -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
