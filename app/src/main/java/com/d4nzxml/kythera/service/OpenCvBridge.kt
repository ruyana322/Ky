package com.d4nzxml.kythera.service

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

class OpenCvBridge {
    data class VideoMeta(
        val totalFrames: Int,
        val fps: Double,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val durationMs: Long
    ) {
        val displayRes get() = "${width}x${height}"
        val displayFps get() = "%.2f fps".format(fps)
        val displayDur get() = "${durationMs / 1000}s"
        val isPortrait get() = height > width
    }

    companion object {
        init {
            try {
                // Pastikan urutannya bener: library asli dulu, baru bridge-nya
                System.loadLibrary("opencv_java4")
                System.loadLibrary("opencv_bridge")
            } catch (e: Exception) {
                Log.e("KytheraOpenCV", "Gagal load library: ${e.message}")
            }
        }

        @JvmStatic external fun openVideoNative(videoPath: String, rotation: Int): IntArray?
        @JvmStatic external fun readFrame(): Bitmap?
        @JvmStatic external fun openWriterNative(outputPath: String, width: Int, height: Int): Boolean
        @JvmStatic external fun writeFrame(bitmap: Bitmap)
        @JvmStatic external fun closeAll()
        @JvmStatic external fun getTotalFrames(): Int
        @JvmStatic external fun getFps(): Double

        fun openVideo(path: String): VideoMeta? {
            val file = File(path)
            if (!file.exists()) {
                throw Exception("File video nggak ada di cache: $path")
            }

            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(path)
                val rotStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                
                val rotation = rotStr?.toIntOrNull() ?: 0
                val duration = durStr?.toLongOrNull() ?: 0L

                // PANGGIL C++ DI SINI. 
                // Kalau C++ error, dia bakal otomatis melempar Exception dan ditangkap sama UI.
                val result = openVideoNative(path, rotation) 
                
                if (result == null) {
                    throw Exception("C++ return null! OpenCV VideoCapture mentok nggak bisa buka video ini (Codec tidak didukung).")
                }

                return VideoMeta(
                    totalFrames = result[0],
                    fps = result[1] / 1000.0,
                    width = result[2],
                    height = result[3],
                    rotation = rotation,
                    durationMs = duration
                )
            } finally {
                mmr.release()
            }
            // KITA SENGAJA NGGAK PAKAI 'CATCH' DI SINI BIAR ERROR-NYA NGGAK DITELAN!
        }
        
        fun close() {
            closeAll()
        }
    }
}
