package com.d4nzxml.kythera.engine

/**
 * Properti yang bisa dianimasikan.
 * Objek memakai X, Y, SCALE, ROTATION, OPACITY. Kamera memakai ZOOM, PAN_X, PAN_Y.
 */
enum class Prop { X, Y, SCALE, ROTATION, OPACITY, ZOOM, PAN_X, PAN_Y }

enum class ObjectType { RECT, CIRCLE, TEXT, IMAGE }

/**
 * Satu titik keyframe. [easing] berlaku untuk segmen yang KELUAR dari keyframe ini menuju keyframe berikutnya.
 */
data class Keyframe(
    val timeMs: Long,
    val value: Float,
    val easing: Easing = Easing.LINEAR
)

/**
 * Objek pada scene.
 * Koordinat dinormalisasi agar tidak tergantung resolusi:
 * - [x], [y]: 0..1 dari lebar/tinggi canvas (titik tengah objek).
 * - [width], [height], [textSize]: pecahan dari LEBAR canvas.
 * - [rotation]: derajat.
 */
data class SceneObject(
    val id: String,
    val type: ObjectType,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 0.2f,
    val height: Float = 0.2f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val zIndex: Int = 0,
    val color: Int = 0xFFFFFFFF.toInt(),
    val text: String = "",
    val textSize: Float = 0.08f,
    /** Path relatif aset (mis. assets/icons/bintang.png) untuk objek IMAGE. */
    val src: String = "",
    val keyframes: Map<Prop, List<Keyframe>> = emptyMap()
) {
    fun valueAt(prop: Prop, timeMs: Long): Float {
        val base = when (prop) {
            Prop.X -> x
            Prop.Y -> y
            Prop.SCALE -> scale
            Prop.ROTATION -> rotation
            Prop.OPACITY -> opacity
            else -> 0f
        }
        return Keyframes.evaluate(keyframes[prop], timeMs, base)
    }
}

/** Kamera: ZOOM (1 = normal), PAN_X/PAN_Y (pergeseran titik pandang, pecahan lebar/tinggi canvas). */
data class Camera(val keyframes: Map<Prop, List<Keyframe>> = emptyMap()) {

    fun valueAt(prop: Prop, timeMs: Long): Float {
        val base = if (prop == Prop.ZOOM) 1f else 0f
        return Keyframes.evaluate(keyframes[prop], timeMs, base)
    }
}

data class SceneSpec(
    val id: String,
    val durationMs: Long,
    val backgroundColor: Int,
    val objects: List<SceneObject>,
    val camera: Camera = Camera()
)

object Keyframes {

    /** Hitung nilai di [timeMs]. [frames] harus terurut menurut waktu (lihat [normalize]). */
    fun evaluate(frames: List<Keyframe>?, timeMs: Long, default: Float): Float {
        if (frames.isNullOrEmpty()) return default
        val first = frames.first()
        val last = frames.last()
        if (timeMs <= first.timeMs) return first.value
        if (timeMs >= last.timeMs) return last.value

        var i = 0
        while (i < frames.size - 1 && timeMs >= frames[i + 1].timeMs) i++
        val a = frames[i]
        val b = frames[i + 1]
        val span = (b.timeMs - a.timeMs).toFloat()
        val t = if (span <= 0f) 1f else (timeMs - a.timeMs) / span
        return a.value + (b.value - a.value) * a.easing.apply(t)
    }

    /** Urutkan menurut waktu; jika ada waktu kembar, keyframe yang terakhir dimenangkan. */
    fun normalize(frames: List<Keyframe>): List<Keyframe> {
        val byTime = LinkedHashMap<Long, Keyframe>()
        frames.forEach { byTime[it.timeMs] = it }
        return byTime.values.sortedBy { it.timeMs }
    }

    fun merge(existing: List<Keyframe>?, added: List<Keyframe>): List<Keyframe> =
        normalize((existing ?: emptyList()) + added)
}
