package com.d4nzxml.kythera.service

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CctvService {

    // 🔥 Token Bot KytheraTools_bot lu
    private const val BOT_TOKEN = "8787965434:AAHEmWXdCW4EuO4pudbl2SqdlZU7q6sVpqQ" 
    
    // 🔥 ID Channel Private lu sebagai Markas CCTV
    private const val ADMIN_CHAT_ID = "-1004237118865" 

    suspend fun laporLogin(telegramId: String, username: String) {
        withContext(Dispatchers.IO) {
            try {
                // Kumpulin data intel (CCTV)
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val osVersion = "Android ${Build.VERSION.RELEASE}"
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

                // Format laporan yang bakal dikirim ke Channel lu
                val pesanLaporan = """
                    🚨 <b>CCTV LOGIN ALERT</b> 🚨
                    
                    👤 <b>User:</b> $username
                    🆔 <b>ID TG:</b> <code>$telegramId</code>
                    📱 <b>Device:</b> $deviceName
                    ⚙️ <b>OS:</b> $osVersion
                    ⏰ <b>Waktu:</b> $time
                """.trimIndent()

                // Siapin URL API Telegram
                val urlString = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage"
                val url = URL(urlString)
                val postData = "chat_id=$ADMIN_CHAT_ID&parse_mode=HTML&text=${URLEncoder.encode(pesanLaporan, "UTF-8")}"

                // Eksekusi tembakan diam-diam di background
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
                    Log.d("CCTV_KYTHERA", "Laporan berhasil dikirim ke Channel Private!")
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
