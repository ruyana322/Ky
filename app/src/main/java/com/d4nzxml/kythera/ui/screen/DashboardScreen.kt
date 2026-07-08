package com.d4nzxml.kythera.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor

@Composable
fun DashboardScreen(onNavigate: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        HeroCard(onNavigate = onNavigate)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "1,247", label = "Foto Di-enhance",
                delta = "+12%", icon = Icons.Rounded.Image, iconColor = KColor.Accent
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "856", label = "Video Dikonversi",
                delta = "+8%", icon = Icons.Rounded.SwapHoriz, iconColor = KColor.Accent2
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "432", label = "Video Dikompres",
                delta = "+24%", icon = Icons.Rounded.Compress, iconColor = KColor.Accent3
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "128", label = "Video Dipatch",
                delta = "+3%", icon = Icons.Rounded.Edit, iconColor = KColor.Orange
            )
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Tools Cepat", Icons.Rounded.Bolt, KColor.Accent)
        Spacer(Modifier.height(14.dp))
        ToolGrid(onNavigate = onNavigate)
        Spacer(Modifier.height(24.dp))

        GlassCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktivitas Terbaru", color = KColor.Text,
                    fontWeight = FontWeight.W600, fontSize = 15.sp)
                Text("Lihat Semua",
                    color = KColor.Accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { onNavigate(4) })
            }
            Spacer(Modifier.height(8.dp))
            ActivityItem(Icons.Rounded.Image, KColor.Accent,
                "Enhance foto_wedding.jpg", "Photo Enhance · 4x Upscale", "2m lalu")
            ActivityItem(Icons.Rounded.SwapHoriz, KColor.Accent2,
                "Convert gameplay.mov to .mp4", "Converter · H.264 / 1080p", "15m lalu")
            ActivityItem(Icons.Rounded.Compress, KColor.Accent3,
                "Compress tutorial.mp4", "Compress · 85% size reduction", "1j lalu")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HeroCard(onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KColor.Surface2)
            .border(1.dp, KColor.Border, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KBadge("PRO", KColor.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Developer by D4nzxml", color = KColor.Text3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Kythera Tools", color = KColor.Text, fontSize = 30.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.height(8.dp))
            Text(
                "Platform all-in-one untuk enhance foto, convert, compress, dan patch video.",
                color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KPrimaryButton(
                    label = "Upload TikTok",
                    icon = Icons.Rounded.Upload,
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.tiktok.com/upload"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1.5f)
                )
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier.weight(1f).height(52.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.OndemandVideo, null, tint = KColor.Text2, modifier = Modifier.size(24.dp).clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.tiktok.com/@dadanpeople")))
                    })
                    Icon(Icons.Rounded.CameraAlt, null, tint = KColor.Text2, modifier = Modifier.size(24.dp).clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.instagram.com/dadanpeople")))
                    })
                    Icon(Icons.Rounded.Send, null, tint = KColor.Text2, modifier = Modifier.size(24.dp).clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/kytheraa_123")))
                    })
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, delta: String, icon: ImageVector, iconColor: Color) {
    GlassCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { 
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) 
            }
            Text(delta, color = KColor.Accent3, fontSize = 10.sp, fontWeight = FontWeight.W600)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
        Text(label, color = KColor.Text3, fontSize = 11.sp)
    }
}

private data class ToolInfo(val title: String, val desc: String, val icon: ImageVector, val color: Color, val navIndex: Int)

@Composable
private fun ToolGrid(onNavigate: (Int) -> Unit) {
    val tools = listOf(
        ToolInfo("Photo Enhance / HD", "Upscale foto hingga 4x dengan AI.", Icons.Rounded.Image, KColor.Accent, 5),
        ToolInfo("Converter Video", "Konversi antar format: MP4, AVI, MKV, MOV...", Icons.Rounded.SwapHoriz, KColor.Accent2, 1),
        ToolInfo("Compress Video", "Kurangi ukuran video hingga 90%.", Icons.Rounded.Compress, KColor.Accent3, 2),
        ToolInfo("Patch Video", "Patch metadata, inject watermark...", Icons.Rounded.Edit, KColor.Orange, 3),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(tools[0]) { onNavigate(tools[0].navIndex) }
            ToolCard(tools[2]) { onNavigate(tools[2].navIndex) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(tools[1]) { onNavigate(tools[1].navIndex) }
            ToolCard(tools[3]) { onNavigate(tools[3].navIndex) }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolInfo, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPressed) tool.color.copy(0.05f) else KColor.Surface2.copy(0.85f))
            .border(1.dp, if (isPressed) tool.color.copy(0.3f) else Color.White.copy(0.06f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
            .padding(18.dp)
    ) {
        Column {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(tool.color.copy(0.25f), tool.color.copy(0.05f)))), contentAlignment = Alignment.Center) { 
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(22.dp)) 
            }
            Spacer(Modifier.height(14.dp))
            Text(tool.title, color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(tool.desc, color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buka Tool", color = tool.color, fontSize = 11.sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = tool.color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ActivityItem(icon: ImageVector, iconColor: Color, title: String, subtitle: String, time: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(0.1f)), contentAlignment = Alignment.Center) { 
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) 
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = KColor.Text, fontSize = 13.sp, fontWeight = FontWeight.W500, maxLines = 1)
            Text(subtitle, color = KColor.Text3, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = KColor.Text3, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            KBadge("Done", KColor.Accent3)
        }
    }
}
