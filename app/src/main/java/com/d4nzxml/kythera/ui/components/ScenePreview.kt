package com.d4nzxml.kythera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.engine.ImageSource
import com.d4nzxml.kythera.engine.SceneRenderer
import com.d4nzxml.kythera.engine.SceneSpec
import com.d4nzxml.kythera.ui.theme.KColor
import java.util.Locale

/**
 * Preview scene di Canvas. Waktu diambil dari frame clock Compose lalu diteruskan ke
 * [SceneRenderer], yang gambarnya hanya bergantung pada (scene, waktu, ukuran).
 */
@Composable
fun ScenePreview(
    scene: SceneSpec,
    aspectWidth: Int,
    aspectHeight: Int,
    modifier: Modifier = Modifier,
    images: ImageSource? = null
) {
    val renderer = remember { SceneRenderer() }
    renderer.images = images
    var timeMs by remember(scene) { mutableLongStateOf(0L) }
    var playing by remember(scene) { mutableStateOf(true) }
    val shape = RoundedCornerShape(12.dp)

    LaunchedEffect(playing, scene) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val deltaMs = (now - last) / 1_000_000L
            last = now
            val next = timeMs + deltaMs
            timeMs = if (next >= scene.durationMs) next % scene.durationMs else next
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(max = 360.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                Modifier
                    .heightIn(max = 360.dp)
                    .aspectRatio(aspectWidth.toFloat() / aspectHeight.toFloat())
                    .clip(shape)
                    .border(1.dp, KColor.Border2, shape)
            ) {
                val t = timeMs
                drawIntoCanvas { canvas ->
                    renderer.draw(
                        canvas.nativeCanvas,
                        size.width.toInt(),
                        size.height.toInt(),
                        scene,
                        t
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Jeda" else "Putar",
                    tint = KColor.Text
                )
            }
            IconButton(onClick = { timeMs = 0L }) {
                Icon(Icons.Rounded.Replay, contentDescription = "Ulang dari awal", tint = KColor.Text)
            }
            Slider(
                value = timeMs.toFloat(),
                onValueChange = {
                    playing = false
                    timeMs = it.toLong()
                },
                valueRange = 0f..scene.durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = KColor.Accent,
                    activeTrackColor = KColor.Accent,
                    inactiveTrackColor = KColor.Border2
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${formatTime(timeMs)} / ${formatTime(scene.durationMs)}",
                color = KColor.Text2,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
