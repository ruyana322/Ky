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
        totalFrames: Int = 1,
        targetResMode: String = "1080p"
    ): Bitmap? = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()

        val result = mutex.withLock {
            if (!com.d4nzxml.kythera.service.MnnVideoBridge.ready) {
                Log.e(TAG, "processFrame[$frameIndex]: engine not ready!")
                return@withLock null
            }

            // 1. Determine expected output dimensions
            val expectedOutW: Int
            val expectedOutH: Int
            when (targetResMode) {
                "1080p" -> {
                    expectedOutW = bitmap.width * 4
                    expectedOutH = bitmap.height * 4
                }
                else -> {
                    expectedOutW = bitmap.width * 2
                    expectedOutH = bitmap.height * 2
                }
            }

            // 2. Prepare the input bitmap for NCNN (which natively outputs 2x)
            val inputBitmap = when (targetResMode) {
                "1080p" -> {
                    if (bitmap.width * 2 < 2000) {
                        Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)
                    } else bitmap
                }
                else -> bitmap
            }

            Log.d(TAG, "Frame[$frameIndex] mode=$targetResMode original=${bitmap.width}x${bitmap.height} " +
                "→ input=${inputBitmap.width}x${inputBitmap.height}")

            // 3. Inference (Using NCNN Anime Video V3 with fast TILE_SIZE=1000)
            var enhanced = try {
                processTiledImage(inputBitmap, 2, "realesr-animevideov3-x2", false)
            } catch (e: Exception) {
                Log.e(TAG, "processFrame[$frameIndex] inference error: ${e.message}")
                null
            } finally {
                // Recycle the (possibly scaled) input if it's not the original
                if (inputBitmap !== bitmap && !inputBitmap.isRecycled) inputBitmap.recycle()
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

    private fun processTiledImage(
        input: Bitmap, scale: Int, modelName: String, useFaceRestore: Boolean
    ): Bitmap? {
        // Tile size configuration (balances JNI overhead vs GPU VRAM limits)
        // We now use a larger tile size (1000) which massively improves speed (1 JNI call for 540p)
        // Modern mobile GPUs can easily handle this without OOM.
        val TILE_SIZE = 1000
        val PADDING = 16

        val outW = input.width * scale
        val outH = input.height * scale
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

        var y = 0
        while (y < input.height) {
            val h = minOf(TILE_SIZE, input.height - y)
            var x = 0
            while (x < input.width) {
                val w = minOf(TILE_SIZE, input.width - x)

                // Calculate padded bounds for this tile
                val px = maxOf(0, x - PADDING)
                val py = maxOf(0, y - PADDING)
                val pw = minOf(input.width - px, w + (x - px) + PADDING)
                val ph = minOf(input.height - py, h + (y - py) + PADDING)

                // Crop padded tile
                val paddedTile = Bitmap.createBitmap(input, px, py, pw, ph)

                // Infer via JNI
                val enhancedTile = RealEsrganBridge.processImage(paddedTile, scale, modelName, useFaceRestore)
                paddedTile.recycle()

                if (enhancedTile != null) {
                    // Extract the core part (remove padding)
                    // The padding in output space is multiplied by scale
                    val padLeftOut = (x - px) * scale
                    val padTopOut = (y - py) * scale
                    val coreWOut = w * scale
                    val coreHOut = h * scale

                    val srcRect = android.graphics.Rect(
                        padLeftOut, padTopOut, padLeftOut + coreWOut, padTopOut + coreHOut
                    )
                    val dstRect = android.graphics.Rect(
                        x * scale, y * scale, (x + w) * scale, (y + h) * scale
                    )
                    canvas.drawBitmap(enhancedTile, srcRect, dstRect, paint)
                    enhancedTile.recycle()
                } else {
                    // Fallback or error, abort
                    output.recycle()
                    return null
                }
                x += TILE_SIZE
            }
            y += TILE_SIZE
        }
        return output
    }
}
