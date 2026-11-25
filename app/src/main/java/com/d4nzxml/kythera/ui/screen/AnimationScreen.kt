package com.d4nzxml.kythera.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.engine.DemoScene
import com.d4nzxml.kythera.engine.SceneJson
import com.d4nzxml.kythera.engine.SceneSpec
import com.d4nzxml.kythera.ui.components.GlassCard
import com.d4nzxml.kythera.ui.components.KBadge
import com.d4nzxml.kythera.ui.components.KFieldLabel
import com.d4nzxml.kythera.ui.components.KFormatTabButton
import com.d4nzxml.kythera.ui.components.KInfoRow
import com.d4nzxml.kythera.ui.components.KPrimaryButton
import com.d4nzxml.kythera.ui.components.KSectionHeader
import com.d4nzxml.kythera.ui.components.ScenePreview
import com.d4nzxml.kythera.ui.theme.KColor

private enum class CanvasRatio(val label: String, val w: Int, val h: Int) {
    PORTRAIT("9:16", 9, 16),
    LANDSCAPE("16:9", 16, 9),
    SQUARE("1:1", 1, 1)
}

/**
 * Animasi (BETA): mesin animasi berbasis Canvas.
 * Tahap ini menampilkan scene contoh untuk menguji mesin dan format JSON.
 * Editor scene, karakter, dan ekspor MP4 menyusul di tahap berikutnya.
 */
@Composable
fun AnimationScreen() {
    val scene = remember { DemoScene.create() }
    var ratio by remember { mutableStateOf(CanvasRatio.PORTRAIT) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KSectionHeader("Animasi", Icons.Rounded.Movie)
            KBadge("BETA", KColor.Accent3)
        }

        GlassCard {
            ScenePreview(scene = scene, aspectWidth = ratio.w, aspectHeight = ratio.h)
            Spacer(Modifier.height(12.dp))
            KFieldLabel("Rasio canvas")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CanvasRatio.entries.forEach { r ->
                    KFormatTabButton(r.label, ratio == r) { ratio = r }
                }
            }
        }

        GlassCard {
            KSectionHeader("Scene contoh", Icons.Rounded.Info, KColor.Accent3)
            Spacer(Modifier.height(8.dp))
            KInfoRow("Durasi", "${scene.durationMs / 1000} detik")
            KInfoRow("Objek", scene.objects.size.toString())
            KInfoRow("Keyframe", countKeyframes(scene).toString())
            KInfoRow("Kamera", "Zoom pelan")
            Spacer(Modifier.height(8.dp))
            Text(
                "Ini scene uji untuk memastikan mesin animasi berjalan di perangkatmu. " +
                    "Editor scene dan ekspor MP4 menyusul.",
                color = KColor.Text3,
                fontSize = 11.sp
            )
        }

        GlassCard {
            KSectionHeader("Format JSON scene", Icons.Rounded.ContentCopy)
            Spacer(Modifier.height(8.dp))
            Text(
                "Menguji simpan lalu baca ulang scene sebagai JSON, memastikan hasilnya identik, " +
                    "lalu menyalin JSON ke clipboard.",
                color = KColor.Text3,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            KPrimaryButton(
                label = "Uji & salin JSON",
                icon = Icons.Rounded.ContentCopy,
                onClick = {
                    val (ok, message, json) = runJsonSelfTest(scene)
                    if (json != null) clipboard.setText(AnnotatedString(json))
                    testResult = ok to message
                },
                modifier = Modifier.fillMaxWidth()
            )
            testResult?.let { (ok, message) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    color = if (ok) KColor.Accent else KColor.Accent2,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun countKeyframes(scene: SceneSpec): Int =
    scene.objects.sumOf { o -> o.keyframes.values.sumOf { it.size } } +
        scene.camera.keyframes.values.sumOf { it.size }

private fun runJsonSelfTest(scene: SceneSpec): Triple<Boolean, String, String?> =
    try {
        val json = SceneJson.toJson(scene)
        val back = SceneJson.fromJson(json)
        if (back == scene) {
            Triple(true, "Lolos: round-trip identik (${json.length} karakter). JSON disalin.", json)
        } else {
            Triple(false, "Gagal: scene berubah setelah disimpan lalu dibaca ulang.", json)
        }
    } catch (e: Exception) {
        Triple(false, "Gagal: ${e.message}", null)
    }
