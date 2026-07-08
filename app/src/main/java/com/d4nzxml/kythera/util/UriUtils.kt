package com.d4nzxml.kythera.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Dapatkan real file path dari URI.
 * Untuk Android 10+ pakai copy ke cache karena direct path tidak tersedia.
 */
fun getRealPathFromUri(context: Context, uri: Uri): String? {
    // Coba lewat MediaStore dulu
    if (uri.scheme == "content") {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            if (cursor.moveToFirst()) {
                val path = cursor.getString(col)
                if (!path.isNullOrEmpty() && File(path).exists()) return path
            }
        }
    }

    // Fallback: copy ke cache dir
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val ext  = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "mp4"
        val file = File(context.cacheDir, "kythera_input_${System.currentTimeMillis()}.$ext")
        FileOutputStream(file).use { out -> inputStream.copyTo(out) }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
