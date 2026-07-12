package com.d4nzxml.kythera.ui.screen
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.draw.scale
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
    // State untuk memicu animasi masuk saat screen di-load
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = tween(durationMillis = 600)
        ) + fadeIn(animationSpec = tween(durationMillis = 600))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeroCard(onNavigate)
            Spacer(Modifier.height(24.dp))

            // Mengganti Statistik Dummy dengan System Status (Lebih Pro)
            SystemStatusCard()
            Spacer(Modifier.height(24.dp))

            KSectionHeader("Tools Cepat", Icons.Rounded.Bolt, KColor.Accent)
            Spacer(Modifier.height(16.dp))
            ToolGrid(onNavigate = onNavigate)
            
            // Jarak lega di bawah untuk area navigasi dan FAB
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun HeroCard(onNavigate: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(KColor.Surface2, Color(0xFF1E1E2E))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KBadge("PRO", KColor.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Powered by D4nzxml Studio", color = KColor.Text3, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                // Versi Aplikasi
                Text("v2.0.1", color = KColor.Text3.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(20.dp))
            Text("Kythera Tools", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.W900)
            Spacer(Modifier.height(8.dp))
            Text(
                "Platform all-in-one untuk enhance foto, convert, compress, dan manipulasi metadata video.",
                color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))
            
            // Tombol Mulai Explore
            Button(
                onClick = { /* Scroll ke bawah atau navigasi */ },
                colors = ButtonDefaults.buttonColors(containerColor = KColor.Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mulai Explore", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SystemStatusCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatusItem(Icons.Rounded.Memory, "Engine", "Active", KColor.Accent)
            StatusItem(Icons.Rounded.Storage, "Storage", "Local", KColor.Accent2)
            StatusItem(Icons.Rounded.Speed, "Render", "Hardware", KColor.Orange)
        }
    }
}

@Composable
private fun StatusItem(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = KColor.Text3, fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Menambahkan property "badge" untuk label AI, PRO, atau NEW
private data class ToolInfo(val title: String, val desc: String, val icon: ImageVector, val color: Color, val navIndex: Int, val badge: String? = null)

@Composable
private fun ToolGrid(onNavigate: (Int) -> Unit) {
    val tools = listOf(
        ToolInfo("Photo Enhance", "Upscale foto hingga 4x.", Icons.Rounded.AutoAwesome, KColor.Accent, 4, "AI"),
        ToolInfo("Video Converter", "MP4, AVI, MKV, MOV...", Icons.Rounded.Transform, KColor.Accent2, 1),
        ToolInfo("Video Compress", "Kurangi ukuran hingga 90%.", Icons.Rounded.Compress, KColor.Accent3, 2, "PRO"),
        ToolInfo("Video Patcher", "Inject metadata & watermark.", Icons.Rounded.Build, KColor.Orange, 3, "NEW"),
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
    
    // Animasi Scale saat ditekan
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale) // Efek bouncing
            .shadow(if (isPressed) 2.dp else 8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPressed) tool.color.copy(0.08f) else KColor.Surface2)
            .border(1.dp, if (isPressed) tool.color.copy(0.3f) else KColor.Border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(tool.color.copy(0.25f), tool.color.copy(0.05f)))), 
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(22.dp)) 
                }
                
                // Menampilkan Badge jika ada (AI / PRO / NEW)
                if (tool.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tool.color.copy(alpha = 0.2f))
                            .border(0.5.dp, tool.color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tool.badge,
                            color = tool.color,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text(tool.title, color = KColor.Text, fontWeight = FontWeight.W700, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(tool.desc, color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            
            // Indikator panah 
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    Icons.Rounded.ChevronRight, 
                    contentDescription = "Buka Tool", 
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
