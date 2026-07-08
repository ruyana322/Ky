package com.d4nzxml.kythera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.theme.KColor

// ─── Glass Card ───────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.06f),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KColor.Surface2.copy(alpha = 0.85f))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content
    )
}

// ─── Primary Button ───────────────────────────────────────────────────────────
@Composable
fun KPrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startColor: Color = KColor.Accent,
    endColor: Color = KColor.Accent2,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, tween(100), label = "btn")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(startColor, endColor))
                else Brush.horizontalGradient(listOf(KColor.Border2, KColor.Border2))
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null,
                tint = if (enabled) Color.Black else KColor.Text3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label,
                color = if (enabled) Color.Black else KColor.Text3,
                fontWeight = FontWeight.W700, fontSize = 14.sp)
        }
    }
}

// ─── Drop Zone ────────────────────────────────────────────────────────────────
@Composable
fun KDropZone(
    onTap: () -> Unit,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = KColor.Accent,
    selectedFileName: String? = null,
    selectedFileSize: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KColor.Surface.copy(alpha = 0.5f))
            .border(2.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onTap)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selectedFileName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(selectedFileName, color = KColor.Text, fontSize = 13.sp,
                        fontWeight = FontWeight.W600, maxLines = 1)
                    if (selectedFileSize != null)
                        Text(selectedFileSize, color = KColor.Text3, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(title, color = KColor.Text, fontSize = 14.sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = KColor.Text3, fontSize = 11.sp)
            }
        }
    }
}

// ─── Toggle Row ───────────────────────────────────────────────────────────────
@Composable
fun KToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KColor.Text, fontSize = 13.sp, fontWeight = FontWeight.W500)
            Text(subtitle, color = KColor.Text3, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor     = Color.Black,
                checkedTrackColor     = KColor.Accent,
                uncheckedThumbColor   = KColor.Text2,
                uncheckedTrackColor   = KColor.Border2,
            )
        )
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────
@Composable
fun KSectionHeader(title: String, icon: ImageVector, iconColor: Color = KColor.Accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = KColor.Text, fontSize = 15.sp, fontWeight = FontWeight.W700)
    }
}

// ─── Info Row ─────────────────────────────────────────────────────────────────
@Composable
fun KInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = KColor.Text3, fontSize = 12.sp)
        Text(value, color = KColor.Text2, fontSize = 12.sp, fontWeight = FontWeight.W500)
    }
}

// ─── Badge ────────────────────────────────────────────────────────────────────
@Composable
fun KBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.W700)
    }
}

// ─── Field Label ──────────────────────────────────────────────────────────────
@Composable
fun KFieldLabel(text: String) {
    Text(
        text = text,
        color = KColor.Text3,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ─── Format Tab Button ────────────────────────────────────────────────────────
@Composable
fun KFormatTabButton(label: String, isActive: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) KColor.Accent.copy(0.12f) else Color.Transparent)
            .border(1.dp,
                if (isActive) KColor.Accent.copy(0.4f) else KColor.Border,
                RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label,
            color = if (isActive) KColor.Accent else KColor.Text2,
            fontSize = 12.sp, fontWeight = if (isActive) FontWeight.W700 else FontWeight.W400)
    }
}

// ─── Step Badge ───────────────────────────────────────────────────────────────
@Composable
fun KStepBadge(number: String, color: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(number, color = color, fontSize = 12.sp, fontWeight = FontWeight.W700)
    }
}

// ─── Loading Overlay ──────────────────────────────────────────────────────────
@Composable
fun KLoadingOverlay(
    visible: Boolean,
    message: String = "Memproses Video...",
    subMessage: String = "Harap tunggu sampai selesai!"
) {
    AnimatedVisibility(visible = visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = KColor.Accent, strokeWidth = 3.dp)
                Spacer(Modifier.height(24.dp))
                Text(message, color = KColor.Text, fontSize = 18.sp, fontWeight = FontWeight.W700)
                Spacer(Modifier.height(8.dp))
                Text(subMessage, color = KColor.Text3, fontSize = 12.sp)
            }
        }
    }
}
