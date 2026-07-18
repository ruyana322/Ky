package com.d4nzxml.kythera.service

import android.content.Context
import android.util.Log
import java.io.File

/**
 * OpenCvBridge — Video I/O via OpenCV
 * Baca/tulis video frame-by-frame langsung in-memory
 * D4nzxml Studio © 2026
 */
object OpenCvBridge {

    private const val TAG = "KytheraOpenCV"
    private var libLoaded = false

    data class VideoMeta(
        val totalFrames: Int,
        val fps: Double,
        val width: Int,
        val height: Int
    ) {
        val isPortrait: Boolean get() = height > width
        val displayFps: String  get() = "%.2ffps".format(fps)
        val durationSec: Double get() = if (fps > 0) totalFrames / fps else 0.0
        val displayDur: String  get() {
            val s = durationSec.toInt()
            return "${s / 60}m ${s % 60}s"
        }
    }

    fun loadLib(): Boolean {
        if (libLoaded) return true
        return try {
            System.loadLibrary("kythera_cv")
            libLoaded = true
            Log.d(TAG, "OpenCV native lib loaded ✅")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed: ${e.message}")
            false
        }
    }

    /**
     * Buka video dan langsung dapat metadata (totalFrames, fps, size)
     * TANPA extract ke disk — instan!
     */
    fun openVideo(videoPath: String): VideoMeta? {
        if (!loadLib()) return null
        val arr = openVideoNative(videoPath) ?: return null
        return VideoMeta(
            totalFrames = arr[0],
            fps         = arr[1] / 1000.0,
            width       = arr[2],
            height      = arr[3]
        )
    }

    /**
     * Buka writer untuk output video
     * Width/height = ukuran frame SETELAH enhance (bisa 2x atau 4x dari input)
     */
    fun openWriter(outputPath: String, width: Int, height: Int): Boolean {
        if (!loadLib()) return false
        return openWriterNative(outputPath, width, height)
    }

    /**
     * Tutup semua resource
     */
    fun close() {
        if (libLoaded) closeAll()
    }

    // ─── JNI Declarations ────────────────────────────────────────────────────
    private external fun openVideoNative(path: String): IntArray?
    external fun readFrame(): android.graphics.Bitmap?
    private external fun openWriterNative(path: String, w: Int, h: Int): Boolean
    external fun writeFrame(bitmap: android.graphics.Bitmap)
    external fun closeAll()
    external fun getTotalFrames(): Int
    external fun getFps(): Double
}
