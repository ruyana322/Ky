package com.d4nzxml.kythera.engine

/** Fungsi easing murni: input 0..1 (di-clamp), output 0..1 (EASE_OUT_BACK boleh sedikit melewati 1). */
enum class Easing {
    LINEAR,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_OUT_BACK,
    EASE_OUT_BOUNCE;

    fun apply(input: Float): Float {
        val t = input.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            EASE_IN_QUAD -> t * t
            EASE_OUT_QUAD -> 1f - (1f - t) * (1f - t)
            EASE_IN_OUT_QUAD ->
                if (t < 0.5f) 2f * t * t
                else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
            EASE_OUT_BACK -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                val u = t - 1f
                1f + c3 * u * u * u + c1 * u * u
            }
            EASE_OUT_BOUNCE -> bounceOut(t)
        }
    }

    private fun bounceOut(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> {
                val u = t - 1.5f / d1
                n1 * u * u + 0.75f
            }
            t < 2.5f / d1 -> {
                val u = t - 2.25f / d1
                n1 * u * u + 0.9375f
            }
            else -> {
                val u = t - 2.625f / d1
                n1 * u * u + 0.984375f
            }
        }
    }
}
