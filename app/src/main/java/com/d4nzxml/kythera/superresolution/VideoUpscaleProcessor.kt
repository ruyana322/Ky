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

            // ─── TARGET RESOLUTION LOGIC ──────────────────────────────────────────
            // NCNN x2 model ALWAYS outputs 2x the input size. 
            // So to control the OUTPUT size, we must scale the INPUT size before inference.
            
            val originalLongest = maxOf(bitmap.width, bitmap.height).toFloat()
            
            val inputBitmap = when (targetResMode) {
                "original" -> {
                    // Output = original. So Input = original / 2
                    val downScale = (originalLongest / scale) / originalLongest
                    val w = (bitmap.width * downScale).toInt().coerceAtLeast(8)
                    val h = (bitmap.height * downScale).toInt().coerceAtLeast(8)
                    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                    bitmap.recycle()
                    scaled
                }
                "1080p" -> {
                    // Output max 1920 (1080p). So Input max = 1920 / scale = 960
                    val maxInputSide = 1920f / scale
                    if (originalLongest > maxInputSide) {
                        val downScale = maxInputSide / originalLongest
                        val w = (bitmap.width * downScale).toInt().coerceAtLeast(8)
                        val h = (bitmap.height * downScale).toInt().coerceAtLeast(8)
                        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                        bitmap.recycle()
                        scaled
                    } else {
                        bitmap
                    }
                }
                else -> {
                    // "2x" mode: Output = 2x original. So Input = original
                    bitmap
                }
            }

            Log.d(TAG, "Frame[$frameIndex] mode=$targetResMode original=${bitmap.width}x${bitmap.height} " +
                "→ input=${inputBitmap.width}x${inputBitmap.height}")

            val expectedOutW = inputBitmap.width * 2
            val expectedOutH = inputBitmap.height * 2

            // MNN inference (MnnVideoBridge handles its own 1024x1024 C++ tiling internally)
            var enhanced = try {
                com.d4nzxml.kythera.service.MnnVideoBridge.enhance(inputBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "processFrame[$frameIndex] inference error: ${e.message}")
                null
            } finally {
                // Recycle the (possibly scaled) input
                if (!inputBitmap.isRecycled) inputBitmap.recycle()
            }

            // If the model was 1x scale (like TimeCut's MNN models), upscale it using Android to match the expected 2x target
            if (enhanced != null && enhanced.width < expectedOutW) {
                val scaled = Bitmap.createScaledBitmap(enhanced, expectedOutW, expectedOutH, true)
                enhanced.recycle()
                enhanced = scaled
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
        // A tile size of 300 prevents GPU OOM on low-end mobile GPUs, ensuring it runs on Vulkan instead of CPU fallback.
        val TILE_SIZE = 300
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
