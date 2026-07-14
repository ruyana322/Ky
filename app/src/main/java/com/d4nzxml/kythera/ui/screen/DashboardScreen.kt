package com.d4nzxml.kythera.ui.screen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Palette Warna Khusus Dashboard ---
val DashBg = Color(0xFF18152B) 
val CardSolidBg = Color(0xFF26233E)
val TextTitle = Color(0xFFF1F1F1)
val TextDesc = Color(0xFFAAA8C2)

@Composable
fun DashboardScreen(onNavigate: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp) // Padding udah dilegain
    ) {
        
        // 1. HERO BANNER (Transform Your Media)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    // Gradient premium dengan 3 lapis warna
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4E3DBB),
                            Color(0xFF382B73), 
                            Color(0xFF27214F)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4A3E85).copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("POWERED BY KYTHERA AI", color = Color(0xFFB4A5F4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("Transform Your Media dengan AI", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Platform all-in-one untuk enhance, convert, dan compress foto & video dengan teknologi AI terbaru.",
                    color = Color(0xFFD0CBE6), fontSize = 12.sp, lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tombol dengan efek Gradient Modern
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7A61E3), Color(0xFF5D48D0))
                            )
                        )
                        .clickable { /* Buka link panduan */ }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Panduan Lengkap", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. STATS ROW (Engine, Storage, Render)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.Settings, iconBg = Color(0xFF1DD1A1), title = "Engine", value = "Active")
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.Storage, iconBg = Color(0xFF5F27CD), title = "Storage", value = "Local")
            // Icon Memory diganti DeveloperBoard biar aman dari error
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.DeveloperBoard, iconBg = Color(0xFFFF9F43), title = "Render", value = "Hardware")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. QUICK TOOLS TITLE
        Text("Quick Tools", color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // 4. QUICK TOOLS GRID
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Video Enhance (Index Nav 5)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    title = "AI Video Enhance",
                    desc = "Upscale video hingga 4K dengan AI. Tingkatkan kualitas menjadi baru.",
                    icon = Icons.Rounded.MovieFilter, iconBg = Color(0xFFE84393),
                    badge = "AI POWER", badgeColor = Color(0xFFE84393),
                    onClick = { onNavigate(5) }
                )
                // Photo Enhance (Index Nav 4)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    title = "AI Photo Enhance",
                    desc = "Upscale foto hingga 4x. Perbaiki kualitas dan remove noise otomatis.",
                    icon = Icons.Rounded.Image, iconBg = Color(0xFF00CEC9),
                    badge = "AI", badgeColor = Color(0xFF9B59B6),
                    onClick = { onNavigate(4) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Video Converter (Index Nav 1)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    title = "Video Converter",
                    desc = "Convert MP4, AVI, MKV, MOV, dan format lainnya tanpa hilang kualitas.",
                    icon = Icons.Rounded.SwapHoriz, iconBg = Color(0xFF6C5CE7),
                    onClick = { onNavigate(1) }
                )
                // Video Compress (Index Nav 2) - Icon Compress diganti FolderZip biar 100% jalan
                ToolCard(
                    modifier = Modifier.weight(1f),
                    title = "Video Compress",
                    desc = "Kurangi ukuran video hingga 90% tanpa mengurangi kualitas visual yang signifikan.",
                    icon = Icons.Rounded.FolderZip, iconBg = Color(0xFF1ABC9C),
                    badge = "PRO", badgeColor = Color(0xFFF39C12),
                    onClick = { onNavigate(2) }
                )
            }
        }
        
        // Spacer bawah
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// --- Komponen Pendukung ---

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, iconBg: Color, title: String, value: String) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardSolidBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(iconBg.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconBg, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, color = TextDesc, fontSize = 10.sp)
            Text(value, color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToolCard(
    modifier: Modifier = Modifier, title: String, desc: String, 
    icon: ImageVector, iconBg: Color, 
    badge: String? = null, badgeColor: Color = Color.Transparent, 
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(190.dp) // Udah ditambahin jadi 190.dp biar deskripsi lega
            .clip(RoundedCornerShape(16.dp))
            .background(CardSolidBg)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Icon Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(iconBg.copy(alpha = 0.8f), iconBg)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            
            // Badge (AI POWER / PRO)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badge, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            desc, color = TextDesc, fontSize = 11.sp, lineHeight = 16.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // Panah kecil
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextDesc, modifier = Modifier.size(16.dp))
            }
        }
    }
}
