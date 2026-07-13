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

// ─── History Screen (Berubah jadi Profile Telegram) ───────────────────────────
@Composable
fun HistoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = context.getSharedPreferences("KytheraPrefs", android.content.Context.MODE_PRIVATE)
    
    val isTiktokLinked = sharedPref.getBoolean("is_tiktok_verified", false)
    val tiktokCookie = sharedPref.getString("tiktok_cookie", "") ?: ""

    // 🔥 PENTING: Ganti tulisan ID_LU_DISINI dengan angka ID Telegram lu yang asli!
    // Contoh: val telegramId = "1234567890" (Bisa dicek dari bot @userinfobot di Tele)
    // 🔥 Ambil ID Telegram dinamis dari brankas (yang diketik user pas awal login)
val telegramId = sharedPref.getString("telegram_id", "") ?: ""
    val botToken = "8787965434:AAHEmWXdCW4EuO4pudbl2SqdlZU7q6sVpqQ"

    // STATE UNTUK MENAMPUNG DATA DARI TELEGRAM
    var userNameProfile by remember { mutableStateOf("Mencari Sinyal...") }
    var nickNameProfile by remember { mutableStateOf("@telegram") }
    var profileBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingData by remember { mutableStateOf(false) }

    // 🔥 MESIN PENYEDOT API TELEGRAM (Anti-Blokir)
    LaunchedEffect(Unit) {
        if (telegramId != "ID_LU_DISINI" && telegramId.isNotEmpty()) {
            isLoadingData = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // 1. Narik Nama & Username Telegram
                    val chatUrl = java.net.URL("https://api.telegram.org/bot$botToken/getChat?chat_id=$telegramId")
                    val chatConn = chatUrl.openConnection() as java.net.HttpURLConnection
                    if (chatConn.responseCode == 200) {
                        val res = chatConn.inputStream.bufferedReader().readText()
                        val resultObj = org.json.JSONObject(res).optJSONObject("result")
                        if (resultObj != null) {
                            val firstName = resultObj.optString("first_name", "Kythera User")
                            val lastName = resultObj.optString("last_name", "")
                            val tUsername = resultObj.optString("username", "")

                            userNameProfile = if (lastName.isNotEmpty()) "$firstName $lastName" else firstName
                            nickNameProfile = if (tUsername.isNotEmpty()) "@$tUsername" else "@telegram_user"
                        }
                    }

                    // 2. Narik Foto Profil Telegram
                    val photoUrl = java.net.URL("https://api.telegram.org/bot$botToken/getUserProfilePhotos?user_id=$telegramId&limit=1")
                    val photoConn = photoUrl.openConnection() as java.net.HttpURLConnection
                    if (photoConn.responseCode == 200) {
                        val pRes = photoConn.inputStream.bufferedReader().readText()
                        val pResult = org.json.JSONObject(pRes).optJSONObject("result")
                        val photosArray = pResult?.optJSONArray("photos")
                        
                        if (photosArray != null && photosArray.length() > 0) {
                            // Ambil file_id dari foto (index 0 itu resolusi paling kecil/sedang)
                            val photoObj = photosArray.getJSONArray(0).getJSONObject(0)
                            val fileId = photoObj.optString("file_id")

                            // 3. Ubah file_id jadi Link Gambar Asli
                            val fileUrl = java.net.URL("https://api.telegram.org/bot$botToken/getFile?file_id=$fileId")
                            val fileConn = fileUrl.openConnection() as java.net.HttpURLConnection
                            if (fileConn.responseCode == 200) {
                                val fRes = fileConn.inputStream.bufferedReader().readText()
                                val fResult = org.json.JSONObject(fRes).optJSONObject("result")
                                val filePath = fResult?.optString("file_path", "")

                                if (!filePath.isNullOrEmpty()) {
                                    // 4. Eksekusi Download Bitmap!
                                    val downloadUrl = java.net.URL("https://api.telegram.org/file/bot$botToken/$filePath")
                                    val dlConn = downloadUrl.openConnection() as java.net.HttpURLConnection
                                    dlConn.doInput = true
                                    dlConn.connect()
                                    val bitmap = BitmapFactory.decodeStream(dlConn.inputStream)
                                    if (bitmap != null) {
                                        profileBitmap = bitmap.asImageBitmap()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    userNameProfile = "Gagal Konek"
                } finally {
                    isLoadingData = false
                }
            }
        } else {
            userNameProfile = "Dadan Ruyana" // Balik ke default kalau ID belum diisi
            nickNameProfile = "@jonggolgamecenter"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Profile", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Sistem Terintegrasi Kythera.", color = KColor.Text3, fontSize = 14.sp)
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
                // 🔥 AVATAR TELEGRAM
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
                
                // 🔥 NAMA & USERNAME DARI TELEGRAM
                if (isLoadingData) {
                    CircularProgressIndicator(color = KColor.Accent, modifier = Modifier.size(24.dp))
                } else {
                    Text(userNameProfile, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(nickNameProfile, color = KColor.Text2, fontSize = 14.sp)
                }
                
                Spacer(Modifier.height(24.dp))
                
                // 🔥 STATISTIK (Disesuaikan buat gaya Hacker / Tool Developer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat("User ID", if(telegramId == "ID_LU_DISINI") "Unknown" else telegramId)
                    ProfileStat("Tipe Akun", "Premium")
                    ProfileStat("Status", "Online")
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

// 🔥 Komponen pendukung 1 (Profil)
@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
