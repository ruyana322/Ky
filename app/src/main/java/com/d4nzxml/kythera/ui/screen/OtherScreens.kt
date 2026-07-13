package com.d4nzxml.kythera.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import java.util.Locale

// ─── History Screen (Berubah jadi Profile) ────────────────────────────────────
@Composable
fun HistoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = context.getSharedPreferences("KytheraPrefs", android.content.Context.MODE_PRIVATE)
    
    val isTiktokLinked = sharedPref.getBoolean("is_tiktok_verified", false)
    val tiktokCookie = sharedPref.getString("tiktok_cookie", "") ?: ""

    // 🔥 STATE UNTUK MENAMPUNG DATA FULL DARI API
    var usernameTikTok by remember { mutableStateOf("Memuat...") }
    var nicknameTikTok by remember { mutableStateOf("@memuat...") }
    var profileBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    var followingCount by remember { mutableStateOf("-") }
    var followerCount by remember { mutableStateOf("-") }
    var likesCount by remember { mutableStateOf("-") }
    
    var isLoadingData by remember { mutableStateOf(false) }

    // 🔥 MESIN PENYEDOT API GANDA (Berjalan otomatis di background)
    LaunchedEffect(isTiktokLinked) {
        if (isTiktokLinked && tiktokCookie.isNotEmpty()) {
            isLoadingData = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // TAHAP 1: Ambil Username dan Link Avatar
                    val url = java.net.URL("https://www.tiktok.com/passport/web/account/info/")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.setRequestProperty("Cookie", tiktokCookie)

                    var extractedUsername = ""

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = org.json.JSONObject(response)
                        val dataObj = jsonObject.optJSONObject("data")
                        
                        if (dataObj != null) {
                            extractedUsername = dataObj.optString("username", "")
                            if (extractedUsername.isNotEmpty()) {
                                usernameTikTok = extractedUsername
                                nicknameTikTok = "@$extractedUsername"
                            }
                            
                            // Download Foto Profil manual jadi Bitmap
                            val avatarUrl = dataObj.optString("avatar_url", "")
                            if (avatarUrl.isNotEmpty()) {
                                try {
                                    val imgUrl = java.net.URL(avatarUrl)
                                    val imgConn = imgUrl.openConnection() as java.net.HttpURLConnection
                                    imgConn.doInput = true
                                    imgConn.connect()
                                    val bitmap = BitmapFactory.decodeStream(imgConn.inputStream)
                                    if (bitmap != null) {
                                        profileBitmap = bitmap.asImageBitmap()
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }

                    // TAHAP 2: Pakai Username buat nembak data Statistik (Followers dll)
                    if (extractedUsername.isNotEmpty()) {
                        try {
                            val statsUrl = java.net.URL("https://www.tiktok.com/api/user/detail/?uniqueId=$extractedUsername")
                            val statsConn = statsUrl.openConnection() as java.net.HttpURLConnection
                            statsConn.requestMethod = "GET"
                            statsConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            statsConn.setRequestProperty("Cookie", tiktokCookie)

                            if (statsConn.responseCode == 200) {
                                val statsRes = statsConn.inputStream.bufferedReader().use { it.readText() }
                                val statsJson = org.json.JSONObject(statsRes)
                                val userInfo = statsJson.optJSONObject("userInfo")
                                val statsObj = userInfo?.optJSONObject("stats")
                                
                                if (statsObj != null) {
                                    // Parse angka dan format jadi "10.5K", "1.2M", dll
                                    followingCount = formatK(statsObj.optInt("followingCount", 0))
                                    followerCount = formatK(statsObj.optInt("followerCount", 0))
                                    likesCount = formatK(statsObj.optInt("heartCount", 0))
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingData = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Akun TikTok yang terhubung.", color = KColor.Text3, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        // 🔥 Card Profil Utama
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(KColor.Surface2)
                .border(1.dp, KColor.Border, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.fillMaxWidth()
            ) {
                // 🔥 AVATAR DINAMIS
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(KColor.Accent, KColor.Accent2))),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap!!,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                
                // 🔥 NAMA & USERNAME DINAMIS
                if (isLoadingData) {
                    CircularProgressIndicator(color = KColor.Accent, modifier = Modifier.size(24.dp))
                } else {
                    Text(usernameTikTok, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(nicknameTikTok, color = KColor.Text2, fontSize = 14.sp)
                }
                
                Spacer(Modifier.height(24.dp))
                
                // 🔥 STATISTIK DINAMIS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat("Mengikuti", followingCount)
                    ProfileStat("Pengikut", followerCount)
                    ProfileStat("Suka", likesCount)
                }
            }
        }
        
        Spacer(Modifier.height(28.dp))
        
        Text("Pengaturan Akun", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        
        ProfileMenuItem(
            icon = Icons.Rounded.CloudSync, 
            title = "Sinkronisasi TikTok", 
            trailingText = if (isTiktokLinked) "Terhubung 🟢" else "Belum 🔴"
        )
        
        ProfileMenuItem(
            icon = Icons.Rounded.Cookie, 
            title = "Sesi TikTok", 
            trailingText = if (tiktokCookie.length > 15) tiktokCookie.take(15) + "..." else tiktokCookie
        )
        
        ProfileMenuItem(icon = Icons.Rounded.Storage, title = "Bersihkan Cache", trailingText = "124 MB")
        
        ProfileMenuItem(
            icon = Icons.Rounded.Logout, 
            title = "Keluar & Hapus Data", 
            trailingText = "", 
            isDestructive = true,
            onClick = {
                sharedPref.edit().clear().apply()
                kotlin.system.exitProcess(0)
            }
        )
        
        Spacer(Modifier.height(100.dp))
    }
}

// 🔥 Fungsi Pembantu buat ngeringkas angka (Misal: 10500 jadi 10.5K)
private fun formatK(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0).replace(".0M", "M")
        num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0).replace(".0K", "K")
        else -> num.toString()
    }
}

// 🔥 Komponen pendukung 1 (Profil)
@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = KColor.Text3, fontSize = 12.sp)
    }
}

// 🔥 Komponen pendukung 2 (Profil)
@Composable
fun ProfileMenuItem(
    icon: ImageVector, 
    title: String, 
    trailingText: String, 
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val contentColor = if (isDestructive) KColor.Orange else Color.White
    val iconColor = if (isDestructive) KColor.Orange else KColor.Accent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KColor.Surface2)
            .clickable(onClick = onClick)
            .border(1.dp, KColor.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(title, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        
        if (trailingText.isNotEmpty()) {
            Text(trailingText, color = KColor.Text3, fontSize = 12.sp)
        }
    }
}

// ─── Settings Screen ──────────────────────────────────────────────────────────
@Composable
fun SettingsScreen() {
    var isDarkMode by remember { mutableStateOf(true) }
    var isTurboMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Pengaturan", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Konfigurasi dan preferensi Kythera Tools.", color = KColor.Text3, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

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

        KSectionHeader("Engine", Icons.Rounded.SmartToy, KColor.Accent2)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.CheckCircle, "Status AI Engine", "Online & Siap", iconTint = KColor.Accent2)
                SettingActionRow(Icons.Rounded.SystemUpdate, "Update Engine", "Cek pembaruan model AI")
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Aplikasi", Icons.Rounded.AppShortcut, KColor.Accent)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.History, "Riwayat", "Lihat log proses video")
                SettingActionRow(Icons.Rounded.BarChart, "Statistik", "Data penggunaan tools")
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Tentang", Icons.Rounded.Info, KColor.Accent3)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.Favorite, "Support Dev D4nzxml", "Traktir kopi / Donasi", iconTint = Color(0xFFFF4B4B))
                SettingActionRow(Icons.Rounded.Article, "Changelog", "Versi 2.0.1")
                SettingActionRow(Icons.Rounded.ChatBubbleOutline, "Feedback", "Laporkan bug atau saran")
                SettingActionRow(Icons.Rounded.PrivacyTip, "Privacy Policy", "Kebijakan Privasi")
                Spacer(Modifier.height(8.dp))
                SettingActionRow(Icons.Rounded.Logout, "Keluar Akun", "Akhiri sesi saat ini", isDestructive = true)
            }
        }

        Spacer(Modifier.height(40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("© 2026 Powered by D4nzxml", color = KColor.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Jonggol Game Center", color = KColor.Text3.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        Spacer(Modifier.height(100.dp))
    }
}

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
