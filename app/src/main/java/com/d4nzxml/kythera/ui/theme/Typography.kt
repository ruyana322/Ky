package com.d4nzxml.kythera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val KytheraTypography = Typography(
    displayLarge  = TextStyle(color = KColor.Text, fontWeight = FontWeight.W800, fontSize = 30.sp),
    titleLarge    = TextStyle(color = KColor.Text, fontWeight = FontWeight.W700, fontSize = 22.sp),
    titleMedium   = TextStyle(color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 16.sp),
    bodyLarge     = TextStyle(color = KColor.Text, fontSize = 14.sp),
    bodyMedium    = TextStyle(color = KColor.Text2, fontSize = 13.sp),
    bodySmall     = TextStyle(color = KColor.Text3, fontSize = 11.sp),
    labelSmall    = TextStyle(color = KColor.Text3, fontSize = 10.sp, fontWeight = FontWeight.W500),
)
