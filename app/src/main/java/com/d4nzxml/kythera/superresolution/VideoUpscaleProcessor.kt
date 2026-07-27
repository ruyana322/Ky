package com.d4nzxml.kythera.superresolution

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Video upscale processor using PixelHD's Mutex-per-instance pattern.
 *
 * Key behaviors:
 *  - Mutex is per-instance, not global
 *  - Input bitmap is recycled after each frame to free memory
 *  - FPS tracking via System.nanoTime()
 *  - Progress emitted via StateFlow<Float> for UI
 *  - All heavy work on Dispatchers.IO
 */
class VideoUpscaleProcessor(
    val scale: Int = 2,
    val modelName: String = "realesr-animevideov3",
    val useFaceRestore: Boolean = false
) {
    private val TAG = "VideoUpscaleProcessor"

    // Per-instance Mutex — prevents concurrent NCNN inference from same processor
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps

    private var isRunning = false

    /**
     * Process a single frame through Real-ESRGAN.
     * Acquires mutex → calls bridge → recycles input bitmap → releases mutex.
     *
     * @param bitmap Input frame. WILL be recycled after this call.
     * @param frameIndex Current frame index (for logging)
     * @param totalFrames Total frames (for progress calculation)
     * @return Enhanced bitmap, or null if inference failed
     */
    suspend fun processFrame(
        bitmap: Bitmap,
        frameIndex: Int = 0,
        totalFrames: Int = 1
    ): Bitmap? = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()

        val result = mutex.withLock {
            if (!RealEsrganBridge.isReady()) {
                Log.e(TAG, "processFrame[$frameIndex]: engine not ready!")
                return@withLock null
            }

            // PRE-SCALING: cap input at 540px on longest side
            // This is the key PixelHD trick — prevent VRAM OOM on large frames
            val maxSide = 540f
            val longestSide = maxOf(bitmap.width, bitmap.height).toFloat()
            val downScale = if (longestSide > maxSide) maxSide / longestSide else 1f
            val inputBitmap = if (downScale < 1f) {
                val w = (bitmap.width * downScale).toInt().coerceAtLeast(8)
                val h = (bitmap.height * downScale).toInt().coerceAtLeast(8)
                val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                bitmap.recycle() // original no longer needed
                scaled
            } else {
                bitmap
            }

            // NCNN inference
            val enhanced = try {
                RealEsrganBridge.processImage(inputBitmap, scale, modelName, useFaceRestore)
            } catch (e: Exception) {
                Log.e(TAG, "processFrame[$frameIndex] inference error: ${e.message}")
                null
            } finally {
                // Recycle the (possibly scaled) input
                if (!inputBitmap.isRecycled) inputBitmap.recycle()
            }

            enhanced
        }

        // Track FPS
        val elapsed = (System.nanoTime() - t0) / 1_000_000_000.0
        if (elapsed > 0 && frameIndex > 0) {
            _fps.value = (1.0 / elapsed).toFloat()
        }

        // Update progress
        if (totalFrames > 0) {
            _progress.value = (frameIndex + 1).toFloat() / totalFrames
        }

        Log.d(TAG, "Frame $frameIndex/$totalFrames done in ${(elapsed * 1000).toLong()}ms | fps=${_fps.value}")
        result
    }

    fun resetProgress() {
        _progress.value = 0f
        _fps.value = 0f
    }

    fun setProgress(value: Float) {
        _progress.value = value.coerceIn(0f, 1f)
    }
}
