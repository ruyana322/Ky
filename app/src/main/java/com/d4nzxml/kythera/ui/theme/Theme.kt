package com.d4nzxml.kythera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Kythera Color Tokens ─────────────────────────────────────────────────────
object KColor {
    val Bg        = Color(0xFF0A0B0E) // Latar belakang utama paling gelap
    val Surface   = Color(0xFF0F1115) // Warna dasar
    val Surface2  = Color(0xFF1E2025) // Warna kartu (lebih terang biar elevation kelihatan)
    val Surface3  = Color(0xFF26282E)
    val Border    = Color(0xFF2A2D35)
    val Border2   = Color(0xFF353943)
    val Accent    = Color(0xFF00D4FF)
    val Accent2   = Color(0xFF7C3AED)
    val Accent3   = Color(0xFF10B981)
    val Orange    = Color(0xFFF59E0B)
    val Text      = Color(0xFFE5E5E5)
    val Text2     = Color(0xFFA1A1AA)
    val Text3     = Color(0xFF71717A)
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
