package com.d4nzxml.kythera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Kythera Color Tokens ─────────────────────────────────────────────────────
// Arah "Scope": meja warna colorist/broadcast monitor. Hijau sinyal dipakai buat
// status aktif/beneran-jalan, bukan dekorasi; amber buat Pro/perhatian. Border
// ditintin hijau-gelap biar nyambung sama garis graticule di background.
object KColor {
    val Bg        = Color(0xFF0A0C0E) // Latar belakang utama paling gelap
    val Surface   = Color(0xFF0D1210) // Warna dasar, tintin ijo gelap
    val Surface2  = Color(0xFF131815) // Warna kartu (lebih terang biar elevation kelihatan)
    val Surface3  = Color(0xFF1A211C)
    val Border    = Color(0xFF1B2320)
    val Border2   = Color(0xFF2A332D)
    val Accent    = Color(0xFF39FF8A) // signal green — status aktif, CTA utama
    val Accent2   = Color(0xFFFFB454) // monitor amber — Pro, perhatian
    val Accent3   = Color(0xFF35D6E6) // cyan — info/beta, aksen sekunder
    val Orange    = Color(0xFFFFA726)
    val Text      = Color(0xFFE7EDE9)
    val Text2     = Color(0xFF8FA69C)
    val Text3     = Color(0xFF5C6D66)
}


private val KytheraColorScheme = darkColorScheme(
    primary         = KColor.Accent,
    secondary       = KColor.Accent2,
    tertiary        = KColor.Accent3,
    background      = KColor.Bg,
    surface         = KColor.Surface,
    onPrimary       = Color.Black,
    onBackground    = KColor.Text,
    onSurface       = KColor.Text,
)

@Composable
fun KytheraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KytheraColorScheme,
        typography  = KytheraTypography,
        content     = content
    )
}
