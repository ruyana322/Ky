package com.d4nzxml.kythera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.d4nzxml.kythera.ui.theme.KColor
import kotlin.random.Random

/**
 * Background garis tipis kayak graticule monitor broadcast. Dipake di belakang
 * hero card atau full-screen container biar nuansa "scope"-nya kerasa tanpa
 * ngeganggu keterbacaan konten di atasnya.
 */
fun Modifier.scopeGrid(
    step: Dp = 24.dp,
    lineColor: Color = KColor.Accent.copy(alpha = 0.045f),
): Modifier = this.drawBehind {
    val stepPx = step.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += stepPx
    }
    var y = 0f
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += stepPx
    }
}

/** Dot kecil + label, buat status "sinyal aktif" (misal di app bar atau header). */
@Composable
fun SignalDot(label: String, color: Color = KColor.Accent, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(color) },
        )
        Box(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Mini waveform dekoratif buat aksen kartu (bukan data asli, cuma visual motif
 * biar tiap kartu nggak keliatan identik). Seed beda per feature -> bentuk beda.
 */
@Composable
fun MiniWaveform(
    modifier: Modifier = Modifier,
    color: Color = KColor.Accent,
    seed: Int = 0,
    alpha: Float = 0.6f,
) {
    val rnd = remember(seed) { Random(seed) }
    val points = remember(seed) { List(17) { 0.15f + rnd.nextFloat() * 0.7f } }
    Canvas(modifier) {
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * (1f - v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2.5f))
    }
}
