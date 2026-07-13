package com.d4nzxml.kythera.ui.screen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor

// ─── History Screen ───────────────────────────────────────────────────────────
@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("History", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
        Text("Riwayat semua proses video.", color = KColor.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // Placeholder items
        val items = listOf(
            Triple(Icons.Rounded.Image,       KColor.Accent,  "Enhance foto_wedding.jpg"  to "Photo Enhance · 4x Upscale · 2m lalu"),
            Triple(Icons.Rounded.SwapHoriz,   KColor.Accent2, "Convert gameplay.mov"       to "Converter · H.264 / 1080p · 15m lalu"),
            Triple(Icons.Rounded.Compress,    KColor.Accent3, "Compress tutorial.mp4"      to "Compress · 85% size reduction · 1j lalu"),
            Triple(Icons.Rounded.Edit,        KColor.Orange,  "Patch vlog_final.mp4"       to "Patch Metadata · D4nzxml · 3j lalu"),
        )

        GlassCard {
            items.forEachIndexed { i, (icon, color, data) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = color.copy(0.1f),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(data.first, color = KColor.Text, fontSize = 13.sp,
                            fontWeight = FontWeight.W500, maxLines = 1)
                        Text(data.second, color = KColor.Text3, fontSize = 11.sp)
                    }
                    KBadge("Done", KColor.Accent3)
                }
                if (i < items.lastIndex) HorizontalDivider(color = KColor.Border, thickness = 0.5.dp)
            }
        }
    }
}

// ─── Settings Screen ──────────────────────────────────────────────────────────
@Composable
fun SettingsScreen() {
    // State buat Toggle
    var isDarkMode by remember { mutableStateOf(true) }
    var isTurboMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text("Pengaturan", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Konfigurasi dan preferensi Kythera Tools.", color = KColor.Text3, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        // ⚙️ UMUM
        KSectionHeader("Umum", Icons.Rounded.Settings, KColor.Accent)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingToggleRow(Icons.Rounded.DarkMode, "Tema Gelap", "Gunakan tampilan gelap", isDarkMode) { isDarkMode = it }
                SettingActionRow(Icons.Rounded.Language, "Bahasa", "Indonesia")
                SettingActionRow(Icons.Rounded.Folder, "Lokasi Penyimpanan", "/Internal/Kythera/")
            }
        }
        Spacer(Modifier.height(24.dp))

        // 🚀 PERFORMA
        KSectionHeader("Performa", Icons.Rounded.Speed, KColor.Orange)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingToggleRow(Icons.Rounded.Bolt, "Mode Performa", "Gunakan seluruh core CPU/GPU", isTurboMode) { isTurboMode = it }
                SettingActionRow(Icons.Rounded.Memory, "Thread AI/FFmpeg", "Auto (Maksimal)")
                SettingActionRow(Icons.Rounded.DeleteOutline, "Hapus Cache", "Aman untuk dibersihkan")
            }
        }
        Spacer(Modifier.height(24.dp))

        // 🤖 ENGINE
        KSectionHeader("Engine", Icons.Rounded.SmartToy, KColor.Accent2)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.CheckCircle, "Status AI Engine", "Online & Siap", iconTint = KColor.Accent2)
                SettingActionRow(Icons.Rounded.SystemUpdate, "Update Engine", "Cek pembaruan model AI")
            }
        }
        Spacer(Modifier.height(24.dp))

        // 📱 APLIKASI
        KSectionHeader("Aplikasi", Icons.Rounded.AppShortcut, KColor.Accent)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.History, "Riwayat", "Lihat log proses video")
                SettingActionRow(Icons.Rounded.BarChart, "Statistik", "Data penggunaan tools")
            }
        }
        Spacer(Modifier.height(24.dp))

        // ℹ️ TENTANG
        KSectionHeader("Tentang", Icons.Rounded.Info, KColor.Accent3)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.Favorite, "Support Dev D4nzxml", "Traktir kopi / Donasi", iconTint = Color(0xFFFF4B4B))
                SettingActionRow(Icons.Rounded.Article, "Changelog", "Versi 2.0.1")
                SettingActionRow(Icons.Rounded.ChatBubbleOutline, "Feedback", "Laporkan bug atau saran")
                SettingActionRow(Icons.Rounded.PrivacyTip, "Privacy Policy", "Kebijakan Privasi")
                Spacer(Modifier.height(8.dp))
                // Tombol Logout dikasih warna peringatan
                SettingActionRow(Icons.Rounded.Logout, "Keluar Akun", "Akhiri sesi saat ini", isDestructive = true)
            }
        }

        Spacer(Modifier.height(40.dp))

        // 🔥 COPYRIGHT FOOTER
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("© 2026 Powered by D4nzxml", color = KColor.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Jonggol Game Center", color = KColor.Text3.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        Spacer(Modifier.height(100.dp)) // Jarak aman buat Bottom Navigation
    }
}

// ─── Komponen Pendukung Khusus Settings (Taruh di bawah SettingsScreen) ───

@Composable
fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = KColor.Text2,
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val color = if (isDestructive) KColor.Orange else Color.White
    val tint = if (isDestructive) KColor.Orange else iconTint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = KColor.Text3, fontSize = 11.sp)
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = KColor.Text3, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = KColor.Text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = KColor.Text3, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KColor.Accent,
                uncheckedThumbColor = KColor.Text3,
                uncheckedTrackColor = KColor.Surface2
            )
        )
    }
}
