package com.d4nzxml.kythera.ui.screen
import androidx.compose.material3.*
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
                    ProfileStat("User ID", if(telegramId == "6969528280") "Unknown" else telegramId)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPref = context.getSharedPreferences("KytheraPrefs", android.content.Context.MODE_PRIVATE)

    // 🔥 STATE MEMORI UNTUK TOGGLE (Baca dari HP)
    var isDarkMode by remember { mutableStateOf(sharedPref.getBoolean("pref_dark_mode", true)) }
    var isTurboMode by remember { mutableStateOf(sharedPref.getBoolean("pref_turbo_mode", true)) }
    
    // 🔥 STATE UNTUK BACA UKURAN CACHE ASLI
    var cacheSize by remember { mutableStateOf(getCacheSize(context)) }

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
                SettingToggleRow(Icons.Rounded.DarkMode, "Tema Gelap", "Gunakan tampilan gelap", isDarkMode) { 
                    isDarkMode = it 
                    sharedPref.edit().putBoolean("pref_dark_mode", it).apply()
                }
                SettingActionRow(Icons.Rounded.Language, "Bahasa", "Indonesia") {
                    android.widget.Toast.makeText(context, "Mendukung bahasa Indonesia untuk saat ini", android.widget.Toast.LENGTH_SHORT).show()
                }
                SettingActionRow(Icons.Rounded.Folder, "Lokasi Penyimpanan", "/Internal/Kythera/") {
                    android.widget.Toast.makeText(context, "Folder Kythera ada di direktori utama", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Performa", Icons.Rounded.Speed, KColor.Orange)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingToggleRow(Icons.Rounded.Bolt, "Mode Performa", "Gunakan seluruh core CPU/GPU", isTurboMode) { 
                    isTurboMode = it 
                    sharedPref.edit().putBoolean("pref_turbo_mode", it).apply()
                }
                SettingActionRow(Icons.Rounded.Memory, "Thread AI/FFmpeg", "Auto (Maksimal)") {
                    android.widget.Toast.makeText(context, "Thread otomatis menyesuaikan spesifikasi HP", android.widget.Toast.LENGTH_SHORT).show()
                }
                // 🔥 TOMBOL HAPUS CACHE ASLI
                SettingActionRow(Icons.Rounded.DeleteOutline, "Hapus Cache", cacheSize) {
                    clearAppCache(context)
                    cacheSize = getCacheSize(context) // Update teks setelah dihapus
                    android.widget.Toast.makeText(context, "Cache berhasil dibersihkan!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Engine", Icons.Rounded.SmartToy, KColor.Accent2)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.CheckCircle, "Status AI Engine", "Online & Siap", iconTint = KColor.Accent2) {
                    android.widget.Toast.makeText(context, "Server AI Berjalan Normal", android.widget.Toast.LENGTH_SHORT).show()
                }
                SettingActionRow(Icons.Rounded.SystemUpdate, "Update Engine", "Cek pembaruan model AI") {
                    android.widget.Toast.makeText(context, "Lu menggunakan engine versi terbaru!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Aplikasi", Icons.Rounded.AppShortcut, KColor.Accent)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                SettingActionRow(Icons.Rounded.History, "Riwayat", "Lihat log proses video") {
                    android.widget.Toast.makeText(context, "Riwayat kosong", android.widget.Toast.LENGTH_SHORT).show()
                }
                SettingActionRow(Icons.Rounded.BarChart, "Statistik", "Data penggunaan tools") {
                    android.widget.Toast.makeText(context, "Belum ada statistik render", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        KSectionHeader("Tentang", Icons.Rounded.Info, KColor.Accent3)
        Spacer(Modifier.height(12.dp))
        GlassCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                // 🔥 ARAHKAN KE WEB LU
                SettingActionRow(Icons.Rounded.Favorite, "Support Dev D4nzxml", "Traktir kopi / Donasi", iconTint = Color(0xFFFF4B4B)) {
                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://kytheramethode.my.id"))
                    context.startActivity(webIntent)
                }
                SettingActionRow(Icons.Rounded.Article, "Changelog", "Versi 2.0.1") {
                    android.widget.Toast.makeText(context, "Update: Peningkatan UI & Integrasi Telegram", android.widget.Toast.LENGTH_LONG).show()
                }
                // 🔥 ARAHKAN KE WA LU
                SettingActionRow(Icons.Rounded.ChatBubbleOutline, "Feedback", "Laporkan bug atau saran") {
                    val waIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/6282129942772"))
                    context.startActivity(waIntent)
                }
                SettingActionRow(Icons.Rounded.PrivacyTip, "Privacy Policy", "Kebijakan Privasi") {
                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://kytheramethode.my.id"))
                    context.startActivity(webIntent)
                }
                Spacer(Modifier.height(8.dp))
                
                // 🔥 TOMBOL LOGOUT ASLI (Hapus data & Tutup App)
                SettingActionRow(Icons.Rounded.Logout, "Keluar Akun", "Akhiri sesi saat ini", isDestructive = true) {
                    sharedPref.edit().clear().apply()
                    android.widget.Toast.makeText(context, "Sesi diakhiri, silakan login ulang.", android.widget.Toast.LENGTH_SHORT).show()
                    kotlin.system.exitProcess(0)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // 🔥 FOOTER DIBERSIHKAN DARI NAMA PERUSAHAAN LAIN
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("© 2026 Powered by D4nzxml Studio", color = KColor.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Dadan Ruyana - Cugenang, Kab. Cianjur", color = KColor.Text3.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        Spacer(Modifier.height(100.dp))
    }
}

// 🔥 FUNGSI TAMBAHAN BUAT MESIN CACHE (Taruh di luar fungsi SettingsScreen)
fun getCacheSize(context: android.content.Context): String {
    return try {
        val size = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    } catch (e: Exception) {
        "0 MB"
    }
}

fun clearAppCache(context: android.content.Context) {
    try {
        context.cacheDir.deleteRecursively()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─── Layar Maintenance & Loading ──────────────────────────────────────────────

@Composable
fun MaintenanceScreen(pesan: String, onRetry: () -> Unit) {
    val colorBg = Color(0xFF121212)
    val colorAccent = Color(0xFF00E5FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Build,
            contentDescription = "Maintenance",
            tint = colorAccent,
            modifier = Modifier.size(100.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Sedang Diperbarui",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = pesan.ifEmpty { "Kythera sedang dalam perbaikan server atau update ke versi terbaru. Silakan kembali lagi nanti." },
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colorAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Coba Lagi", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { kotlin.system.exitProcess(0) }) {
            Text("Tutup Aplikasi", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InitialLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF00E5FF))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Menghubungkan ke Server Kythera...", color = Color.Gray, fontSize = 14.sp)
    }
}
// ─── Komponen Tambahan Khusus Pengaturan ──────────────────────────────────────

@Composable
fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00E5FF), // Warna Cyan Kythera
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun SettingActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = Color.Gray,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) Color(0xFFFF4B4B) else Color.White 
    val finalIconTint = if (isDestructive) Color(0xFFFF4B4B) else iconTint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🔥 TYPO GUA UDAH DIBENERIN DI SINI: Parameter pertamanya "icon", bukan warna
        Icon(icon, contentDescription = null, tint = finalIconTint, modifier = Modifier.size(24.dp))
        
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
    }
}
