package com.d4nzxml.kythera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * OpenCvBridge — Video I/O via OpenCV
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
            Log.e(TAG, "Failed load kythera_cv: ${e.message}")
            false
        }
    }

    fun openVideo(videoPath: String): VideoMeta? {
        if (!loadLib()) {
            Log.e(TAG, "Library not loaded!")
            return null
        }
        return try {
            val arr = openVideoNative(videoPath) ?: return null
            if (arr.size < 5) return null
            VideoMeta(
                totalFrames = arr[0],
                fps         = arr[1] / 1000.0,
                width       = arr[2],
                height      = arr[3],
                rotation    = arr[4]
            )
        } catch (e: Exception) {
            Log.e(TAG, "openVideo error: ${e.message}")
            null
        }
    }

    fun openWriter(outputPath: String, width: Int, height: Int): Boolean {
        if (!loadLib()) return false
        return try {
            openWriterNative(outputPath, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "openWriter error: ${e.message}")
            false
        }
    }

    fun close() {
        if (!libLoaded) return
        try { closeAll() } catch (e: Exception) { Log.e(TAG, "close error: ${e.message}") }
    }

    // ─── JNI Declarations — nama HARUS match persis dengan C++ function name ──
    @JvmStatic external fun openVideoNative(path: String): IntArray?
    @JvmStatic external fun readFrame(): Bitmap?
    @JvmStatic external fun openWriterNative(path: String, w: Int, h: Int): Boolean
    @JvmStatic external fun writeFrame(bitmap: Bitmap)
    @JvmStatic external fun closeAll()
    @JvmStatic external fun getTotalFrames(): Int
    @JvmStatic external fun getFps(): Double

    init {
        loadLib()
    }
}
