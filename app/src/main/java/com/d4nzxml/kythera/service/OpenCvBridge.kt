package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File

/**
 * OpenCvBridge — Video I/O via OpenCV
 * Handle rotation metadata otomatis
 * D4nzxml Studio © 2026
 */
object OpenCvBridge {

    private const val TAG = "KytheraOpenCV"
    private var libLoaded = false

    data class VideoMeta(
        val totalFrames: Int,
        val fps: Double,
        val width: Int,
        val height: Int,
        val rotation: Int
    ) {
        val isPortrait:  Boolean get() = height > width
        val displayFps:  String  get() = "%.2ffps".format(fps)
        val durationSec: Double  get() = if (fps > 0) totalFrames / fps else 0.0
        val displayDur:  String  get() {
            val s = durationSec.toInt()
            return "${s / 60}m ${s % 60}s"
        }
        val displayRes: String get() = "${width}×${height}"
    }

    fun loadLib(): Boolean {
        if (libLoaded) return true
        return try {
            System.loadLibrary("kythera_cv")
            libLoaded = true
            Log.d(TAG, "OpenCV lib loaded ✅")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed: ${e.message}")
            false
        }
    }

    /**
     * Buka video → dapat metadata instan + rotation info
     */
    fun openVideo(videoPath: String): VideoMeta? {
        if (!loadLib()) return null
        val arr = openVideoNative(videoPath) ?: return null
        if (arr.size < 5) return null
        return VideoMeta(
            totalFrames = arr[0],
            fps         = arr[1] / 1000.0,
            width       = arr[2],
            height      = arr[3],
            rotation    = arr[4]
        )
    }

    /**
     * Buka writer untuk output video
     */
    fun openWriter(outputPath: String, width: Int, height: Int): Boolean {
        if (!loadLib()) return false
        return openWriterNative(outputPath, width, height)
    }

    fun close() {
        if (libLoaded) closeAll()
    }

    // ─── JNI Declarations ─────────────────────────────────────────────────────
    private external fun openVideoNative(path: String): IntArray?
    external fun readFrame(): Bitmap?
    private external fun openWriterNative(path: String, w: Int, h: Int): Boolean
    external fun writeFrame(bitmap: Bitmap)
    external fun closeAll()
    external fun getTotalFrames(): Int
    external fun getFps(): Double
}
