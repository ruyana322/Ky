package com.d4nzxml.kythera.engine

enum class Direction { LEFT, RIGHT, TOP, BOTTOM }

/** Preset yang tersedia di Phase 2 langkah 1. Walk hadir bersama karakter di langkah 2. */
enum class Preset(val label: String) {
    FADE_IN("Fade In"),
    FADE_OUT("Fade Out"),
    SLIDE_IN("Slide In"),
    SLIDE_OUT("Slide Out"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    SHAKE("Shake"),
    BOUNCE("Bounce"),
    ROTATE("Rotate"),
    IDLE("Idle")
}

private const val MAX_PRESET_DURATION_MS = 600_000L
private const val IDLE_HALF_PERIOD_MS = 750L
private const val SHAKE_STEP_MS = 50L

/**
 * Menerapkan preset sebagai keyframe pada objek, dimulai di [startMs] selama [durationMs].
 * Hasilnya digabung dengan keyframe yang sudah ada, sehingga beberapa preset bisa dirantai.
 */
fun SceneObject.withPreset(
    preset: Preset,
    startMs: Long,
    durationMs: Long,
    direction: Direction = Direction.LEFT
): SceneObject {
    require(startMs >= 0) { "startMs tidak boleh negatif" }
    require(durationMs in 1..MAX_PRESET_DURATION_MS) { "durationMs harus 1..$MAX_PRESET_DURATION_MS" }
    val end = startMs + durationMs

    val added: Map<Prop, List<Keyframe>> = when (preset) {
        Preset.FADE_IN -> mapOf(
            Prop.OPACITY to listOf(
                Keyframe(startMs, 0f, Easing.EASE_OUT_QUAD),
                Keyframe(end, opacity)
            )
        )
        Preset.FADE_OUT -> mapOf(
            Prop.OPACITY to listOf(
                Keyframe(startMs, opacity, Easing.EASE_IN_QUAD),
                Keyframe(end, 0f)
            )
        )
        Preset.SLIDE_IN -> slideFrames(direction, startMs, end, entering = true)
        Preset.SLIDE_OUT -> slideFrames(direction, startMs, end, entering = false)
        Preset.ZOOM_IN -> mapOf(
            Prop.SCALE to listOf(
                Keyframe(startMs, 0f, Easing.EASE_OUT_BACK),
                Keyframe(end, scale)
            )
        )
        Preset.ZOOM_OUT -> mapOf(
            Prop.SCALE to listOf(
                Keyframe(startMs, scale, Easing.EASE_IN_QUAD),
                Keyframe(end, 0f)
            )
        )
        Preset.SHAKE -> mapOf(Prop.X to shakeFrames(startMs, durationMs))
        Preset.BOUNCE -> mapOf(
            Prop.Y to listOf(
                Keyframe(startMs, y - 0.15f, Easing.EASE_OUT_BOUNCE),
                Keyframe(end, y)
            )
        )
        Preset.ROTATE -> mapOf(
            Prop.ROTATION to listOf(
                Keyframe(startMs, rotation, Easing.LINEAR),
                Keyframe(end, rotation + 360f)
            )
        )
        Preset.IDLE -> mapOf(Prop.SCALE to idleFrames(startMs, end))
    }

    val merged = HashMap<Prop, List<Keyframe>>(keyframes)
    added.forEach { (prop, frames) -> merged[prop] = Keyframes.merge(keyframes[prop], frames) }
    return copy(keyframes = merged)
}

private fun SceneObject.slideFrames(
    direction: Direction,
    start: Long,
    end: Long,
    entering: Boolean
): Map<Prop, List<Keyframe>> {
    val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
    val prop = if (horizontal) Prop.X else Prop.Y
    val home = if (horizontal) x else y
    val offset = when (direction) {
        Direction.LEFT, Direction.TOP -> -1f
        Direction.RIGHT, Direction.BOTTOM -> 1f
    }
    val off = home + offset
    val frames = if (entering) {
        listOf(Keyframe(start, off, Easing.EASE_OUT_QUAD), Keyframe(end, home))
    } else {
        listOf(Keyframe(start, home, Easing.EASE_IN_QUAD), Keyframe(end, off))
    }
    return mapOf(prop to frames)
}

private fun SceneObject.shakeFrames(start: Long, durationMs: Long): List<Keyframe> {
    val steps = (durationMs / SHAKE_STEP_MS).toInt().coerceIn(2, 200)
    val amplitude = 0.015f
    val frames = ArrayList<Keyframe>(steps + 1)
    for (i in 0..steps) {
        val time = start + durationMs * i / steps
        val value = if (i == steps) {
            x
        } else {
            val sign = if (i % 2 == 0) 1f else -1f
            x + sign * amplitude * (1f - i.toFloat() / steps)
        }
        frames.add(Keyframe(time, value))
    }
    return frames
}

private fun SceneObject.idleFrames(start: Long, end: Long): List<Keyframe> {
    val frames = ArrayList<Keyframe>()
    var t = start
    var up = false
    frames.add(Keyframe(t, scale, Easing.EASE_IN_OUT_QUAD))
    while (t < end) {
        t = minOf(t + IDLE_HALF_PERIOD_MS, end)
        up = !up
        frames.add(Keyframe(t, if (up) scale * 1.04f else scale, Easing.EASE_IN_OUT_QUAD))
    }
    return frames
}
