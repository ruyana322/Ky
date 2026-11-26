package com.d4nzxml.kythera.engine

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Format JSON scene (sumber kebenaran scene). Generator naskah atau AI kelak cukup menghasilkan JSON ini.
 * Semua input divalidasi; error dilempar sebagai IllegalArgumentException dengan pesan yang bisa ditampilkan.
 */
object SceneJson {

    const val FORMAT = "jsvideostudio.scene"
    const val VERSION = 1
    const val MIN_DURATION_MS = 100L
    const val MAX_DURATION_MS = 3_600_000L
    const val MAX_OBJECTS = 500
    const val MAX_KEYFRAMES = 5_000
    const val MAX_AUDIO = 50
    const val MAX_PRESETS_PER_OBJECT = 100
    private const val MAX_FADE_MS = 600_000L
    private const val MAX_TEXT = 200

    private val OBJECT_PROPS = setOf(Prop.X, Prop.Y, Prop.SCALE, Prop.ROTATION, Prop.OPACITY)
    private val CAMERA_PROPS = setOf(Prop.ZOOM, Prop.PAN_X, Prop.PAN_Y)

    fun toJson(scene: SceneSpec): String {
        val objects = JSONArray()
        scene.objects.forEach { o ->
            objects.put(
                JSONObject()
                    .put("id", o.id)
                    .put("type", o.type.name)
                    .put("x", o.x.toDouble())
                    .put("y", o.y.toDouble())
                    .put("width", o.width.toDouble())
                    .put("height", o.height.toDouble())
                    .put("scale", o.scale.toDouble())
                    .put("rotation", o.rotation.toDouble())
                    .put("opacity", o.opacity.toDouble())
                    .put("zIndex", o.zIndex)
                    .put("color", colorToString(o.color))
                    .put("text", o.text)
                    .put("textSize", o.textSize.toDouble())
                    .put("src", o.src)
                    .put("keyframes", keyframesToJson(o.keyframes))
            )
        }
        val body = JSONObject()
            .put("id", scene.id)
            .put("durationMs", scene.durationMs)
            .put("background", colorToString(scene.backgroundColor))
            .put("objects", objects)
            .put("camera", keyframesToJson(scene.camera.keyframes))
            .put("audio", audioToJson(scene.audio))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("scene", body)
            .toString(2)
    }

    fun fromJson(text: String): SceneSpec {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("File bukan JSON yang valid")
        }
        require(root.optString("format") == FORMAT) { "Format scene tidak dikenali" }
        val version = root.optInt("version", -1)
        require(version == VERSION) { "Versi scene tidak didukung ($version)" }

        val s = root.optJSONObject("scene")
            ?: throw IllegalArgumentException("Data scene tidak ditemukan")
        val id = s.optString("id").trim()
        require(id.isNotEmpty()) { "Scene tanpa id" }

        val duration = s.optLong("durationMs", -1L)
        require(duration in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Durasi scene harus $MIN_DURATION_MS..$MAX_DURATION_MS ms"
        }
        val background = parseColor(s.optString("background", "#000000"))

        val objectsJson = s.optJSONArray("objects") ?: JSONArray()
        require(objectsJson.length() <= MAX_OBJECTS) { "Objek terlalu banyak (maks $MAX_OBJECTS)" }
        val objects = (0 until objectsJson.length()).map { i ->
            val o = objectsJson.optJSONObject(i)
                ?: throw IllegalArgumentException("Objek #$i tidak valid")
            parseObject(o)
        }
        require(objects.map { it.id }.toSet().size == objects.size) { "ID objek harus unik" }

        val camera = Camera(parseKeyframes(s.optJSONArray("camera"), CAMERA_PROPS))
        val keyframeTotal = objects.sumOf { o -> o.keyframes.values.sumOf { it.size } } +
            camera.keyframes.values.sumOf { it.size }
        require(keyframeTotal <= MAX_KEYFRAMES) { "Keyframe terlalu banyak setelah preset diterapkan (maks $MAX_KEYFRAMES)" }

        val audio = parseAudio(s.optJSONArray("audio"))
        return SceneSpec(id, duration, background, objects, camera, audio)
    }

    private fun parseObject(o: JSONObject): SceneObject {
        val id = o.optString("id").trim()
        require(id.isNotEmpty()) { "Ada objek tanpa id" }
        val type = enumOf<ObjectType>(o.optString("type"), "type")

        fun num(key: String, default: Float): Float {
            val v = o.optDouble(key, default.toDouble())
            require(v.isFinite()) { "Nilai '$key' pada objek '$id' tidak valid" }
            return v.toFloat()
        }

        val src = o.optString("src", "").trim()
        require(src.isEmpty() || AssetPaths.isSafeRelative(src)) { "Path aset tidak aman pada objek '$id'" }
        require(type != ObjectType.IMAGE || src.isNotEmpty()) { "Objek gambar '$id' belum punya 'src'" }

        val base = SceneObject(
            id = id,
            type = type,
            x = num("x", 0.5f),
            y = num("y", 0.5f),
            width = num("width", 0.2f).coerceAtLeast(0f),
            height = num("height", 0.2f).coerceAtLeast(0f),
            scale = num("scale", 1f),
            rotation = num("rotation", 0f),
            opacity = num("opacity", 1f).coerceIn(0f, 1f),
            zIndex = o.optInt("zIndex", 0),
            color = parseColor(o.optString("color", "#FFFFFFFF")),
            text = o.optString("text", "").take(MAX_TEXT),
            textSize = num("textSize", 0.08f).coerceAtLeast(0f),
            src = src,
            keyframes = parseKeyframes(o.optJSONArray("keyframes"), OBJECT_PROPS)
        )
        return applyPresets(base, o.optJSONArray("presets"))
    }

    /** Preset di JSON, mis. {"name":"FADE_IN","start":0,"duration":500}, diubah jadi keyframe. */
    private fun applyPresets(base: SceneObject, arr: JSONArray?): SceneObject {
        if (arr == null) return base
        require(arr.length() <= MAX_PRESETS_PER_OBJECT) { "Preset terlalu banyak pada objek '${base.id}'" }
        var result = base
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i)
                ?: throw IllegalArgumentException("Preset #$i pada '${base.id}' tidak valid")
            val preset = enumOf<Preset>(p.optString("name"), "preset")
            val start = p.optLong("start", -1L)
            require(start >= 0) { "Waktu mulai preset ${preset.name} pada '${base.id}' tidak valid" }
            val duration = p.optLong("duration", -1L)
            require(duration in 1..600_000L) { "Durasi preset ${preset.name} pada '${base.id}' tidak valid" }
            val direction = if (p.has("direction")) enumOf<Direction>(p.optString("direction"), "direction") else Direction.LEFT
            result = result.withPreset(preset, start, duration, direction)
        }
        return result
    }

    private fun parseAudio(arr: JSONArray?): List<AudioClip> {
        if (arr == null) return emptyList()
        require(arr.length() <= MAX_AUDIO) { "Audio terlalu banyak (maks $MAX_AUDIO)" }
        val list = ArrayList<AudioClip>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: throw IllegalArgumentException("Audio #$i tidak valid")
            val src = a.optString("src").trim()
            require(AssetPaths.isSafeRelative(src) && src.startsWith("assets/audio/")) {
                "Path audio harus di assets/audio/ dan aman: '$src'"
            }
            val start = a.optLong("startMs", 0L)
            require(start >= 0) { "startMs audio '$src' tidak valid" }
            val end: Long? = if (a.has("endMs") && !a.isNull("endMs")) a.optLong("endMs", -1L) else null
            require(end == null || end > start) { "endMs audio '$src' harus lebih besar dari startMs" }
            val volume = a.optDouble("volume", 1.0)
            require(volume.isFinite() && volume in 0.0..2.0) { "volume audio '$src' harus 0..2" }
            val fadeIn = a.optLong("fadeInMs", 0L)
            val fadeOut = a.optLong("fadeOutMs", 0L)
            require(fadeIn in 0..MAX_FADE_MS && fadeOut in 0..MAX_FADE_MS) { "Fade audio '$src' tidak valid" }
            val kind = if (a.has("kind")) enumOf<AudioKind>(a.optString("kind"), "kind") else AudioKind.MUSIC
            list.add(
                AudioClip(
                    id = a.optString("id", "a${i + 1}").ifBlank { "a${i + 1}" },
                    src = src,
                    startMs = start,
                    endMs = end,
                    volume = volume.toFloat(),
                    fadeInMs = fadeIn,
                    fadeOutMs = fadeOut,
                    loop = a.optBoolean("loop", false),
                    kind = kind
                )
            )
        }
        return list
    }

    private fun audioToJson(clips: List<AudioClip>): JSONArray {
        val arr = JSONArray()
        clips.forEach { c ->
            val o = JSONObject()
                .put("id", c.id)
                .put("src", c.src)
                .put("startMs", c.startMs)
                .put("volume", c.volume.toDouble())
                .put("fadeInMs", c.fadeInMs)
                .put("fadeOutMs", c.fadeOutMs)
                .put("loop", c.loop)
                .put("kind", c.kind.name)
            if (c.endMs != null) o.put("endMs", c.endMs)
            arr.put(o)
        }
        return arr
    }

    private fun parseKeyframes(arr: JSONArray?, allowed: Set<Prop>): Map<Prop, List<Keyframe>> {
        if (arr == null) return emptyMap()
        val result = LinkedHashMap<Prop, List<Keyframe>>()
        var total = 0
        for (i in 0 until arr.length()) {
            val track = arr.optJSONObject(i)
                ?: throw IllegalArgumentException("Track keyframe #$i tidak valid")
            val prop = enumOf<Prop>(track.optString("prop"), "prop")
            require(prop in allowed) { "Properti $prop tidak boleh dipakai di sini" }
            val frames = track.optJSONArray("frames")
                ?: throw IllegalArgumentException("Track $prop tanpa 'frames'")

            val list = ArrayList<Keyframe>(frames.length())
            for (j in 0 until frames.length()) {
                val f = frames.optJSONObject(j)
                    ?: throw IllegalArgumentException("Keyframe $prop #$j tidak valid")
                val t = f.optLong("t", -1L)
                require(t >= 0) { "Waktu keyframe $prop #$j tidak valid" }
                val v = f.optDouble("v", Double.NaN)
                require(v.isFinite()) { "Nilai keyframe $prop #$j tidak valid" }
                val easing = if (f.has("e")) enumOf<Easing>(f.optString("e"), "easing") else Easing.LINEAR
                list.add(Keyframe(t, v.toFloat(), easing))
            }
            total += list.size
            require(total <= MAX_KEYFRAMES) { "Keyframe terlalu banyak (maks $MAX_KEYFRAMES)" }
            result[prop] = Keyframes.normalize(list)
        }
        return result
    }

    private fun keyframesToJson(map: Map<Prop, List<Keyframe>>): JSONArray {
        val tracks = JSONArray()
        map.forEach { (prop, frames) ->
            val arr = JSONArray()
            frames.forEach { k ->
                arr.put(
                    JSONObject()
                        .put("t", k.timeMs)
                        .put("v", k.value.toDouble())
                        .put("e", k.easing.name)
                )
            }
            tracks.put(JSONObject().put("prop", prop.name).put("frames", arr))
        }
        return tracks
    }

    private fun colorToString(color: Int): String = String.format(Locale.US, "#%08X", color)

    private fun parseColor(value: String): Int = try {
        Color.parseColor(value)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Warna tidak valid: '$value'")
    }

    private inline fun <reified T : Enum<T>> enumOf(value: String, field: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Nilai '$field' tidak valid: '$value'")
}
