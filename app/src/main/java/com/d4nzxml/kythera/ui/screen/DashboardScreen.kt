package com.d4nzxml.kythera.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.components.MiniWaveform
import com.d4nzxml.kythera.ui.components.SignalDot
import com.d4nzxml.kythera.ui.components.scopeGrid
import com.d4nzxml.kythera.ui.theme.KColor

@Composable
fun DashboardScreen(onNavigate: (Int) -> Unit) {
    val context = LocalContext.current

    // 🔥 Saklar buat munculin Popup Panduan
    var showGuideDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KColor.Bg)
            .scopeGrid()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {

        // 1. HERO — "CH.01 DASHBOARD"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KColor.Surface)
                .border(1.dp, KColor.Border, RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CH.01 — DASHBOARD",
                    color = KColor.Text3,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                )
                SignalDot(label = "SIGNAL OK")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Setiap konversi, warna aslinya kejaga.",
                color = KColor.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 26.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Bukan upscale palsu, bukan tempel metadata. Lu liat apa yang beneran diproses.",
                color = KColor.Text2,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))
            MiniWaveform(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                color = KColor.Accent,
                seed = 0,
                alpha = 0.5f,
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, KColor.Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { showGuideDialog = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Panduan Lengkap", color = KColor.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = KColor.Accent, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. STATS ROW — readout gaya instrumen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.Settings, title = "Engine", value = "Active", on = true)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.Storage, title = "Storage", value = "Local", on = true)
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Rounded.DeveloperBoard, title = "Render", value = "Hardware", on = true)
        }

        Spacer(modifier = Modifier.height(26.dp))

        // 3. QUICK TOOLS TITLE
        Text(
            "QUICK TOOLS",
            color = KColor.Text3,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 4. QUICK TOOLS GRID
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // SDR to HDR (Index Nav 5) — gantiin AI Video Enhance, udah nggak dipakai
                ToolCard(
                    modifier = Modifier.weight(1f),
                    channel = "CH.02 — COLOR",
                    title = "SDR to HDR",
                    desc = "BT.709 ke BT.2020, kurva HLG/PQ. Transformasi beneran.",
                    icon = Icons.Rounded.HdrOn,
                    badge = "BARU", badgeColor = KColor.Accent,
                    waveSeed = 1,
                    onClick = { onNavigate(5) },
                )
                // Photo Enhance (Index Nav 4)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    channel = "CH.03 — ENHANCE",
                    title = "AI Photo Enhance",
                    desc = "Upscale 4x. Perbaiki kualitas, remove noise otomatis.",
                    icon = Icons.Rounded.Image,
                    badge = "AI", badgeColor = KColor.Accent3,
                    waveSeed = 2,
                    onClick = { onNavigate(4) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Video Converter (Index Nav 1)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    channel = "CH.04 — FORMAT",
                    title = "Video Converter",
                    desc = "MP4, AVI, MKV, MOV, dan lainnya tanpa hilang kualitas.",
                    icon = Icons.Rounded.SwapHoriz,
                    badge = null, badgeColor = KColor.Accent,
                    waveSeed = 3,
                    onClick = { onNavigate(1) },
                )
                // Video Compress (Index Nav 2)
                ToolCard(
                    modifier = Modifier.weight(1f),
                    channel = "CH.05 — SIZE",
                    title = "Video Compress",
                    desc = "Kecilin ukuran hingga 90% tanpa ngorbanin kualitas visual.",
                    icon = Icons.Rounded.FolderZip,
                    badge = "PRO", badgeColor = KColor.Accent2,
                    waveSeed = 4,
                    onClick = { onNavigate(2) },
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // 🔥 5. POPUP DIALOG PANDUAN LENGKAP
        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                containerColor = KColor.Surface2,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Panduan Kythera Tools",
                        color = KColor.Text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // Tutorial 1
                        Column {
                            Text("1. Tutorial Upload HD ke TikTok", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("1. Buka aplikasi Kythera.\n2. Tekan ikon Garis Tiga (Menu) di sudut atas.\n3. Pilih menu Upload TikTok.\n4. Masukkan file video yang ingin diunggah.\n5. Tambahkan caption sesuai keinginan.", color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp)

                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KColor.Surface3)
                                    .border(1.dp, KColor.Border, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                            ) {
                                Text("Catatan Penting:\n• Sampul (Cover): Belum tersedia (tahap perbaikan bug).\n• Privasi: Langsung unggah dengan mode Publik, tidak perlu diprivat dulu.", color = KColor.Text2, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }

                        // Tutorial 2
                        Column {
                            Text("2. Tutorial Converter", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Sangat disarankan menggunakan standar berikut:", color = KColor.Text2, fontSize = 13.sp)
                            Text("• Mode: CRF\n• Preset: Veryfast (Rekomendasi)\n• Opsi Cepat: Gunakan Ultrafast untuk render kilat, namun ukuran file akan lebih besar.", color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp)
                        }

                        // Tutorial 3
                        Column {
                            Text("3. Tutorial Compress", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Fitur untuk mengecilkan ukuran video:\n1. Buka menu Compress.\n2. Pilih salah satu dari 3 Preset yang tersedia.\n3. Mulai proses kompresi.", color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp)

                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x332E1212))
                                    .border(1.dp, Color(0xFF5C2A2A), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                            ) {
                                Text("Peringatan Penting:\nSebelum memulai kompresi, pastikan lu telah mematikan semua toggle/switch pada menu Compress.", color = KColor.Accent2, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }

                        // Tutorial 4 (Troubleshooting)
                        Column {
                            Text("4. Solusi Video Miring", color = KColor.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Jika hasil video rotasinya salah, lakukan Convert ulang dengan 'Obat Sakti' ini:\n• Mode: CRF\n• CRF: 20\n• Preset: Veryfast\n• Resolution: Original", color = KColor.Text2, fontSize = 13.sp, lineHeight = 20.sp)
                        }

                        Divider(color = KColor.Border)

                        Text("Terima kasih,\nD4nzxml Developer", color = KColor.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text("Tutup", color = KColor.Accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
    }
}

// --- Komponen Pendukung ---

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String, on: Boolean) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KColor.Surface)
            .border(1.dp, KColor.Border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(KColor.Surface3)
                .border(1.dp, if (on) KColor.Accent.copy(alpha = 0.4f) else KColor.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (on) KColor.Accent else KColor.Text3, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, color = KColor.Text3, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(value, color = KColor.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToolCard(
    modifier: Modifier = Modifier,
    channel: String,
    title: String,
    desc: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = KColor.Accent,
    waveSeed: Int = 0,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KColor.Surface)
            .border(1.dp, KColor.Border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                channel,
                color = KColor.Text3,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = badgeColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(KColor.Surface3)
                .border(1.dp, badgeColor.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(title, color = KColor.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 15.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        MiniWaveform(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            color = badgeColor,
            seed = waveSeed,
            alpha = 0.45f,
        )
    }
}
