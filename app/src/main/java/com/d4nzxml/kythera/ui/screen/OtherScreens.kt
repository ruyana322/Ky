package com.d4nzxml.kythera.ui.screen

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
    var notif    by remember { mutableStateOf(true) }
    var autoSave by remember { mutableStateOf(true) }
    var dark     by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Pengaturan", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
        Text("Konfigurasi aplikasi Kythera Tools.", color = KColor.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        KSectionHeader("Umum", Icons.Rounded.Settings, KColor.Accent)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            KToggleRow("Notifikasi", "Tampilkan notifikasi setelah proses selesai",
                notif) { notif = it }
            Spacer(Modifier.height(14.dp))
            KToggleRow("Auto Simpan ke Galeri", "Langsung simpan output ke galeri",
                autoSave) { autoSave = it }
            Spacer(Modifier.height(14.dp))
            KToggleRow("Dark Mode", "Tema gelap (default aktif)",
                dark) { dark = it }
        }
        Spacer(Modifier.height(20.dp))

        KSectionHeader("Tentang", Icons.Rounded.Info, KColor.Accent2)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            KInfoRow("Versi", "1.0.0")
            KInfoRow("Build", "FFmpeg min-gpl 6.x")
            KInfoRow("Developer", "D4nzxml · JGC")
            KInfoRow("Platform", "Android Native (Kotlin + Compose)")
        }
    }
}
