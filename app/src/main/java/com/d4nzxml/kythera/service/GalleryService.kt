package com.d4nzxml.kythera.service

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object GalleryService {

    // ─── 1. SIMPAN VIDEO ───────────────────────────────────────────────────
    suspend fun saveVideo(context: Context, sourcePath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) return@withContext false

                val filename = sourceFile.name
                val resolver = context.contentResolver

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/Kythera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val uri: Uri = resolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { it.copyTo(out) }
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                // Hapus file temp setelah tersimpan
                sourceFile.delete()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ─── 2. SIMPAN POTO (HASIL AI) ─────────────────────────────────────────
    suspend fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    // Bikin folder khusus Kythera di dalam folder Pictures
                    put(MediaStore.Images.Media.RELATIVE_PATH, 
                        "${Environment.DIRECTORY_PICTURES}/Kythera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri: Uri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false

                resolver.openOutputStream(uri)?.use { out ->
                    // Kompres jadi PNG kualitas 100% biar HD-nya nggak luntur
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
