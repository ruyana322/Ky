package com.d4nzxml.kythera.ui.screen

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor

@Composable
fun DashboardScreen(onNavigate: (Int) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    // 🔥 Memori untuk menampilkan Pop-up Panduan
    var showGuideDialog by remember { mutableStateOf(false) }
    
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
            // 🔥 Lempar perintah untuk membuka dialog ke HeroCard
            HeroCard(onNavigate = onNavigate, onOpenGuide = { showGuideDialog = true })
            Spacer(Modifier.height(24.dp))

            SystemStatusCard()
            Spacer(Modifier.height(24.dp))

            KSectionHeader("Tools Cepat", Icons.Rounded.Bolt, KColor.Accent)
            Spacer(Modifier.height(16.dp))

            ToolGrid(onNavigate = onNavigate)

            Spacer(Modifier.height(100.dp))
        }
    }

    // 🔥 Panggil Desain Pop-up Panduan di Sini
    if (showGuideDialog) {
        GuideDialog(onDismiss = { showGuideDialog = false })
    }
}

@Composable
private fun HeroCard(onNavigate: (Int) -> Unit, onOpenGuide: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors = listOf(KColor.Surface2, Color(0xFF1E1E2E))))
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

            // 🔥 TOMBOL PANDUAN PENGGUNA (Sekarang berfungsi)
            Button(
                onClick = onOpenGuide,
                colors = ButtonDefaults.buttonColors(containerColor = KColor.Accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Panduan Pengguna", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SystemStatusCard() {
    GlassCard(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp))) {
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
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = KColor.Text3, fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class ToolInfo(val title: String, val desc: String, val icon: ImageVector, val color: Color, val navIndex: Int, val badge: String? = null)

@Composable
private fun ToolGrid(onNavigate: (Int) -> Unit) {
    val tools = listOf(
        ToolInfo("Photo Enhance", "Upscale foto hingga 4x.", Icons.Rounded.AutoAwesome, KColor.Accent, 4, "AI"),
        ToolInfo("Video Converter", "MP4, AVI, MKV, MOV...", Icons.Rounded.Transform, KColor.Accent2, 1),
        ToolInfo("Video Compress", "Kurangi ukuran hingga 90%.", Icons.Rounded.Compress, KColor.Accent3, 2, "PRO")
    )
    
    // 🔥 Kita bungkus semuanya pakai Column biar berjejer ke bawah
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        // 1. Photo Enhance (Index 0) dibuat FULL WIDTH di atas
        ToolCard(tools[0]) { onNavigate(tools[0].navIndex) }

        // 2. Baris kedua untuk Video Converter & Compress (Dibagi 2 sama rata)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Kolom Kiri
            Box(modifier = Modifier.weight(1f)) {
                ToolCard(tools[1]) { onNavigate(tools[1].navIndex) }
            }
            // Kolom Kanan
            Box(modifier = Modifier.weight(1f)) {
                ToolCard(tools[2]) { onNavigate(tools[2].navIndex) }
            }
        }
    }
}


@Composable
private fun ToolCard(tool: ToolInfo, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, animationSpec = tween(durationMillis = 150), label = "")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
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
                ) { Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(22.dp)) }

                if (tool.badge != null) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(tool.color.copy(alpha = 0.2f))
                            .border(0.5.dp, tool.color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(text = tool.badge, color = tool.color, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(tool.title, color = KColor.Text, fontWeight = FontWeight.W700, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(tool.desc, color = KColor.Text3, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── Desain Pop-up Panduan Pengguna ──────────────────────────────────────────

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val colorSurface = Color(0xFF1E1E1E)
    val colorCyan = Color(0xFF00E5FF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f), 
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Panduan Kythera",
                        color = colorCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                }

                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                // Isi Panduan yang bisa di-scroll
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    GuideSection(
                        title = "✨ 1. Cara Meng-HD-kan Foto",
                        desc = "• Masuk ke menu 'Photo Enhance'.\n• Upload foto yang buram atau pecah.\n• Biarkan AI Kythera bekerja melakukan Upscale hingga 4x lipat.\n• Klik tombol 'Simpan' untuk mendownload hasil."
                    )
                    
                    GuideSection(
                        title = "🎬 2. Cara Meng-HD-kan Video",
                        desc = "• Masuk ke menu 'Video Enhance' (Jika tersedia).\n• Pilih video target dari galeri.\n• Engine akan merender ulang frame-by-frame untuk resolusi maksimal. Mendukung output mulus hingga 60fps untuk animasi atau gameplay.\n• Tunggu proses rendering selesai."
                    )
                    
                    GuideSection(
                        title = "🔀 3. Cara Mengubah Format",
                        desc = "• Masuk ke menu 'Video Converter'.\n• Masukkan file video asli lu.\n• Pilih format tujuan di pengaturan (contoh: dari MKV ke MP4).\n• Klik 'Convert' dan tunggu Engine FFmpeg memproses file tersebut."
                    )
                    
                    GuideSection(
                        title = "🗜️ 4. Cara Kompres Video",
                        desc = "• Masuk ke menu 'Video Compress'.\n• Pilih file video yang ukurannya terlalu besar.\n• Geser slider untuk mengatur seberapa kecil lu mau memadatkan ukurannya.\n• Tekan 'Compress' dan biarkan sistem merampingkan file tanpa merusak kualitas utama."
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Jonggol Game Center - Engine Active",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tombol Paham
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Gua Paham!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun GuideSection(title: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = desc,
            color = Color(0xFFB0B0B0), 
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
