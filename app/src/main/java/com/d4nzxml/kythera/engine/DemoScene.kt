package com.d4nzxml.kythera.engine

/** Scene contoh untuk menguji mesin. Bukan konten pengguna. */
object DemoScene {

    const val DURATION_MS = 6_000L

    fun create(): SceneSpec {
        val title = SceneObject(
            id = "title",
            type = ObjectType.TEXT,
            x = 0.5f, y = 0.20f,
            text = "JS Video Studio",
            textSize = 0.09f,
            color = 0xFFF8FAFC.toInt(),
            zIndex = 3
        )
            .withPreset(Preset.ZOOM_IN, startMs = 0, durationMs = 700)
            .withPreset(Preset.IDLE, startMs = 700, durationMs = DURATION_MS - 700)

        val subtitle = SceneObject(
            id = "subtitle",
            type = ObjectType.TEXT,
            x = 0.5f, y = 0.28f,
            text = "Mesin animasi Phase 2",
            textSize = 0.04f,
            color = 0xFF94A3B8.toInt(),
            zIndex = 3
        )
            .withPreset(Preset.FADE_IN, startMs = 1_200, durationMs = 600)

        val box = SceneObject(
            id = "box",
            type = ObjectType.RECT,
            x = 0.5f, y = 0.52f,
            width = 0.28f, height = 0.28f,
            color = 0xFF8B5CF6.toInt(),
            zIndex = 1
        )
            .withPreset(Preset.SLIDE_IN, startMs = 300, durationMs = 900, direction = Direction.LEFT)
            .withPreset(Preset.ROTATE, startMs = 1_500, durationMs = 2_000)

        val ball = SceneObject(
            id = "ball",
            type = ObjectType.CIRCLE,
            x = 0.5f, y = 0.80f,
            width = 0.18f,
            color = 0xFF06B6D4.toInt(),
            zIndex = 2
        )
            .withPreset(Preset.FADE_IN, startMs = 800, durationMs = 200)
            .withPreset(Preset.BOUNCE, startMs = 800, durationMs = 1_000)
            .withPreset(Preset.SHAKE, startMs = 3_200, durationMs = 700)
            .withPreset(Preset.FADE_OUT, startMs = 5_000, durationMs = 800)

        val camera = Camera(
            mapOf(
                Prop.ZOOM to listOf(
                    Keyframe(0, 1f, Easing.EASE_IN_OUT_QUAD),
                    Keyframe(DURATION_MS, 1.12f)
                )
            )
        )

        return SceneSpec(
            id = "demo",
            durationMs = DURATION_MS,
            backgroundColor = 0xFF151A2B.toInt(),
            objects = listOf(title, subtitle, box, ball),
            camera = camera
        )
    }
}
