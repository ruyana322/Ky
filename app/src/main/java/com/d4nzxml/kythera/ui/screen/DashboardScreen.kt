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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            .padding(16.dp)
    ) {
        HeroCard()
        Spacer(Modifier.height(24.dp))

        // Statistik 1 Baris Horizontal
        GlassCard(
            modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStatItem("1,247", "Foto HD", KColor.Accent)
                MiniStatItem("856", "Convert", KColor.Accent2)
                MiniStatItem("432", "Compress", KColor.Accent3)
                MiniStatItem("128", "Patch", KColor.Orange)
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Tools Cepat", Icons.Rounded.Bolt, KColor.Accent)
        Spacer(Modifier.height(16.dp))
        ToolGrid(onNavigate = onNavigate)
        
        // Jarak lega di bawah untuk tempat FAB (Upload TikTok) nanti
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun HeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(KColor.Surface2)
            .border(1.dp, KColor.Border, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KBadge("PRO", KColor.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Developer by D4nzxml", color = KColor.Text3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Kythera Tools", color = KColor.Text, fontSize = 28.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.height(8.dp))
            Text(
                "Platform all-in-one untuk enhance foto, convert, compress, dan patch video.",
                color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun MiniStatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = KColor.Text3, fontSize = 11.sp)
    }
}

private data class ToolInfo(val title: String, val desc: String, val icon: ImageVector, val color: Color, val navIndex: Int)

@Composable
private fun ToolGrid(onNavigate: (Int) -> Unit) {
    val tools = listOf(
        ToolInfo("Photo Enhance / HD", "Upscale foto hingga 4x.", Icons.Rounded.Image, KColor.Accent, 4),
        ToolInfo("Converter Video", "MP4, AVI, MKV, MOV...", Icons.Rounded.SwapHoriz, KColor.Accent2, 1),
        ToolInfo("Compress Video", "Kurangi ukuran hingga 90%.", Icons.Rounded.Compress, KColor.Accent3, 2),
        ToolInfo("Patch Video", "Metadata & watermark.", Icons.Rounded.Edit, KColor.Orange, 3),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ToolCard(tools[0]) { onNavigate(tools[0].navIndex) }
            ToolCard(tools[2]) { onNavigate(tools[2].navIndex) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPressed) tool.color.copy(0.05f) else KColor.Surface2)
            .border(1.dp, if (isPressed) tool.color.copy(0.3f) else KColor.Border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
            .padding(16.dp)
    ) {
        Column {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(tool.color.copy(0.25f), tool.color.copy(0.05f)))), contentAlignment = Alignment.Center) { 
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(20.dp)) 
            }
            Spacer(Modifier.height(16.dp))
            Text(tool.title, color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(tool.desc, color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            
            // Ikon panah polos menggantikan teks Buka Tool
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    Icons.Rounded.ChevronRight, 
                    contentDescription = "Buka Tool", 
                    tint = Color(0x4DFFFFFF), // setara rgba(255,255,255,0.3)
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
