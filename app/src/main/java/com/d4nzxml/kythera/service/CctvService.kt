package com.d4nzxml.kythera.service

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CctvService {

    // 🔥 Token Bot KytheraTools_bot
    private const val BOT_TOKEN = "8787965434:AAHEmWXdCW4EuO4pudbl2SqdlZU7q6sVpqQ" 
    
    // 🔥 ID Channel Private Markas
    private const val ADMIN_CHAT_ID = "-1004237118865" 

    private fun lacakNamaTelegram(telegramId: String): String {
        try {
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/getChat?chat_id=$telegramId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                if (jsonObject.getBoolean("ok")) {
                    val result = jsonObject.getJSONObject("result")
                    val firstName = result.optString("first_name", "")
                    val lastName = result.optString("last_name", "")
                    val username = result.optString("username", "")
                    
                    val fullName = "$firstName $lastName".trim()
                    
                    return if (username.isNotEmpty()) {
                        "$fullName (@$username)"
                    } else if (fullName.isNotEmpty()) {
                        fullName
                    } else {
                        "Pengguna Tanpa Nama"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CCTV_KYTHERA", "Intel gagal melacak nama: ${e.message}")
        }
        return "ID Valid (Gagal narik nama)"
    }

    // 🔥 Parameter akunTiktok ditambahin di sini
    suspend fun laporLogin(telegramId: String, akunTiktok: String) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Eksekusi Pelacakan Nama Telegram
                val namaAsli = lacakNamaTelegram(telegramId)

                // 2. Kumpulin data Device
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val osVersion = "Android ${Build.VERSION.RELEASE}"
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // 3. Format Laporan Baru (Ada Logo TikTok-nya)
                val pesanLaporan = """
                    🚨 <b>CCTV LOGIN ALERT</b> 🚨
                    
                    🎵 <b>Akun TikTok:</b> $akunTiktok
                    👤 <b>Nama TG:</b> $namaAsli
                    🆔 <b>ID TG:</b> <code>$telegramId</code>
                    📱 <b>Device:</b> $deviceName
                    ⚙️ <b>OS:</b> $osVersion
                    ⏰ <b>Waktu:</b> $time
                """.trimIndent()

                // 4. Kirim Laporan ke Markas
                val urlString = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
                val url = URL(urlString)
                val postData = "chat_id=$ADMIN_CHAT_ID&parse_mode=HTML&text=${URLEncoder.encode(pesanLaporan, "UTF-8")}"

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    Log.d("CCTV_KYTHERA", "Laporan berhasil dikirim ke Channel!")
                } else {
                    Log.e("CCTV_KYTHERA", "Gagal ngirim laporan. Kode: $responseCode")
                }
                
                connection.disconnect()

            } catch (e: Exception) {
                Log.e("CCTV_KYTHERA", "Error CCTV: ${e.message}")
            }
        }
    }
}
