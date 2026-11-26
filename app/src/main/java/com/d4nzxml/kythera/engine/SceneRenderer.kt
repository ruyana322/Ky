package com.d4nzxml.kythera.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Menggambar satu frame scene pada waktu [timeMs].
 *
 * Hasil gambar hanya bergantung pada (scene, timeMs, ukuran). Tidak ada state waktu internal,
 * sehingga preview (waktu nyata) dan renderer video (waktu = nomor frame / fps) menghasilkan gambar yang sama.
 */
class SceneRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var cachedScene: SceneSpec? = null
    private var sortedObjects: List<SceneObject> = emptyList()

    /** Sumber gambar untuk objek IMAGE. Jika null atau gambar tidak ada, digambar kotak penanda merah. */
    var images: ImageSource? = null

    fun draw(canvas: Canvas, width: Int, height: Int, scene: SceneSpec, timeMs: Long) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()

        if (cachedScene !== scene) {
            sortedObjects = scene.objects.sortedBy { it.zIndex }
            cachedScene = scene
        }

        val saveCount = canvas.save()
        try {
            canvas.clipRect(0f, 0f, w, h)
            canvas.drawColor(scene.backgroundColor)

            // Kamera: zoom dan pan di sekitar titik tengah canvas.
            val zoom = scene.camera.valueAt(Prop.ZOOM, timeMs).coerceAtLeast(0.01f)
            val panX = scene.camera.valueAt(Prop.PAN_X, timeMs)
            val panY = scene.camera.valueAt(Prop.PAN_Y, timeMs)
            canvas.translate(w / 2f, h / 2f)
            canvas.scale(zoom, zoom)
            canvas.translate(-(w / 2f + panX * w), -(h / 2f + panY * h))

            for (obj in sortedObjects) {
                drawObject(canvas, obj, w, h, timeMs)
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun drawObject(canvas: Canvas, obj: SceneObject, w: Float, h: Float, timeMs: Long) {
        val opacity = obj.valueAt(Prop.OPACITY, timeMs).coerceIn(0f, 1f)
        if (opacity <= 0f) return

        val px = obj.valueAt(Prop.X, timeMs) * w
        val py = obj.valueAt(Prop.Y, timeMs) * h
        val s = obj.valueAt(Prop.SCALE, timeMs)
        if (s == 0f) return
        val rot = obj.valueAt(Prop.ROTATION, timeMs)
        val alpha = (Color.alpha(obj.color) * opacity).roundToInt()

        val saveCount = canvas.save()
        canvas.translate(px, py)
        canvas.rotate(rot)
        canvas.scale(s, s)

        when (obj.type) {
            ObjectType.RECT -> {
                fillPaint.color = obj.color
                fillPaint.alpha = alpha
                val halfW = obj.width * w / 2f
                val halfH = obj.height * w / 2f
                rect.set(-halfW, -halfH, halfW, halfH)
                canvas.drawRect(rect, fillPaint)
            }
            ObjectType.CIRCLE -> {
                fillPaint.color = obj.color
                fillPaint.alpha = alpha
                canvas.drawCircle(0f, 0f, obj.width * w / 2f, fillPaint)
            }
            ObjectType.TEXT -> {
                if (obj.text.isNotEmpty()) {
                    textPaint.color = obj.color
                    textPaint.alpha = alpha
                    textPaint.textSize = obj.textSize * w
                    val baseline = -(textPaint.ascent() + textPaint.descent()) / 2f
                    canvas.drawText(obj.text, 0f, baseline, textPaint)
                }
            }
            ObjectType.IMAGE -> {
                val bmp = images?.bitmap(obj.src)
                val boxW = obj.width * w
                val boxH = obj.height * w
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0 && boxW > 0f && boxH > 0f) {
                    // Muat di dalam kotak width x height, rasio asli gambar dipertahankan.
                    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                    var dw = boxW
                    var dh = boxW / ratio
                    if (dh > boxH) {
                        dh = boxH
                        dw = boxH * ratio
                    }
                    rect.set(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f)
                    imagePaint.alpha = alpha
                    canvas.drawBitmap(bmp, null, rect, imagePaint)
                } else if (boxW > 0f && boxH > 0f) {
                    // Aset hilang/rusak: kotak merah transparan sebagai penanda.
                    fillPaint.color = 0xFFFF5555.toInt()
                    fillPaint.alpha = (alpha * 0.35f).roundToInt()
                    rect.set(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f)
                    canvas.drawRect(rect, fillPaint)
                }
            }
        }
        canvas.restoreToCount(saveCount)
    }
}
