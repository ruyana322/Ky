package com.d4nzxml.kythera.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.engine.SceneRenderer
import com.d4nzxml.kythera.engine.SceneSpec
import com.d4nzxml.kythera.service.FfmpegResult
import com.d4nzxml.kythera.service.GalleryService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Ekspor scene ke MP4 (H.264, yuv420p).
 *
 * Alur: (1) tiap frame digambar oleh [SceneRenderer] pada waktu = nomor frame / fps (deterministik,
 * sama dengan preview), disimpan sebagai PNG di cache; (2) ffmpeg-kit meng-encode urutan PNG jadi MP4;
 * (3) MP4 disimpan ke galeri (Movies/Kythera). Folder frame sementara selalu dihapus.
 * Yang dihapus hanya file sementara buatan ekspor ini, tidak pernah file milik pengguna.
 */
class VideoExporter(private val context: Context) {

    data class Options(val width: Int, val height: Int, val fps: Int)

    suspend fun export(
        project: VideoProject,
        scene: SceneSpec,
        options: Options,
        onProgress: (fraction: Float, label: String) -> Unit
    ): FfmpegResult = withContext(Dispatchers.IO) {

        if (options.width <= 0 || options.height <= 0 || options.width % 2 != 0 || options.height % 2 != 0) {
            return@withContext fail("Ukuran video harus genap dan positif")
        }
        if (options.fps !in 1..60) return@withContext fail("FPS harus 1 sampai 60")

        val frames = ((scene.durationMs * options.fps + 999) / 1000).toInt().coerceAtLeast(1)
        if (frames > MAX_FRAMES) return@withContext fail("Terlalu panjang: maksimal $MAX_FRAMES frame")

        val needBytes = frames.toLong() * options.width * options.height / 20
        val freeBytes = context.cacheDir.usableSpace
        if (freeBytes < needBytes) {
            return@withContext fail(
                "Ruang penyimpanan kurang: butuh ±${needBytes / MB} MB, tersedia ${freeBytes / MB} MB"
            )
        }

        val stamp = System.currentTimeMillis()
        val frameDir = File(context.cacheDir, "render_$stamp")
        val outFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "Kythera_Anim_$stamp.mp4")
        var bitmap: Bitmap? = null

        try {
            if (!frameDir.mkdirs()) return@withContext fail("Gagal membuat folder sementara")

            val renderer = SceneRenderer().apply {
                images = AssetBitmapProvider(project.dir, maxOf(options.width, options.height))
            }
            val bmp = Bitmap.createBitmap(options.width, options.height, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            val canvas = Canvas(bmp)

            // Tahap 1: render frame
            for (i in 0 until frames) {
                ensureActive()
                val tMs = i * 1000L / options.fps
                canvas.drawColor(Color.BLACK) // ratakan agar tidak ada transparansi
                renderer.draw(canvas, options.width, options.height, scene, tMs)

                val frameFile = File(frameDir, String.format(Locale.US, "f_%06d.png", i))
                val written = frameFile.outputStream().buffered().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (!written) throw IOException("Gagal menulis frame $i")
                onProgress(RENDER_WEIGHT * (i + 1) / frames, "Merender frame ${i + 1}/$frames")
            }
            bmp.recycle()
            bitmap = null

            // Tahap 2: encode
            val cmd = "-y -framerate ${options.fps} " +
                "-i \"${frameDir.absolutePath}/f_%06d.png\" " +
                "-c:v libx264 -preset veryfast -crf 18 -pix_fmt yuv420p -movflags +faststart " +
                "\"${outFile.absolutePath}\""

            val (ok, error) = runFfmpeg(cmd, scene.durationMs) { p ->
                onProgress(
                    RENDER_WEIGHT + (1f - RENDER_WEIGHT) * p,
                    "Encode MP4 ${(p * 100).toInt()}%"
                )
            }
            if (!ok) {
                outFile.delete()
                return@withContext fail(error ?: "Encode gagal")
            }

            // Tahap 3: simpan ke galeri
            onProgress(1f, "Menyimpan ke galeri…")
            if (GalleryService.saveVideo(context, outFile.absolutePath)) {
                FfmpegResult(success = true, outputPath = "Movies/Kythera/${outFile.name}")
            } else {
                FfmpegResult(
                    success = false,
                    outputPath = outFile.absolutePath,
                    errorMessage = "Gagal menyimpan ke galeri. File ada di ${outFile.absolutePath}"
                )
            }
        } catch (e: CancellationException) {
            outFile.delete()
            throw e
        } catch (e: Exception) {
            outFile.delete()
            fail(e.message ?: "Gagal merender")
        } finally {
            bitmap?.recycle()
            frameDir.deleteRecursively()
        }
    }

    private suspend fun runFfmpeg(
        cmd: String,
        totalMs: Long,
        onProgress: (Float) -> Unit
    ): Pair<Boolean, String?> = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeAsync(
            cmd,
            { s ->
                val ok = ReturnCode.isSuccess(s.returnCode)
                val error = if (ok) null else (s.allLogsAsString ?: "").takeLast(300)
                if (cont.isActive) cont.resume(Pair(ok, error))
            },
            { _ -> },
            { stat ->
                if (totalMs > 0) {
                    onProgress((stat.time.toDouble() / totalMs).toFloat().coerceIn(0f, 1f))
                }
            }
        )
        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }

    private fun fail(message: String) = FfmpegResult(success = false, errorMessage = message)

    private companion object {
        const val MAX_FRAMES = 36_000
        const val RENDER_WEIGHT = 0.6f
        const val MB = 1024L * 1024L
    }
}
