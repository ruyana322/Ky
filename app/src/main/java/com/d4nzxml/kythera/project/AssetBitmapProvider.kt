package com.d4nzxml.kythera.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.d4nzxml.kythera.engine.AssetPaths
import com.d4nzxml.kythera.engine.ImageSource
import java.io.File

/**
 * Memuat gambar dari folder project untuk objek IMAGE.
 * - Path divalidasi dan harus tetap berada di dalam folder project.
 * - Gambar di-downsample agar sisi terpanjangnya <= [maxEdge] (hemat RAM).
 * - Hasil di-cache (LRU, ±32 MB). File yang gagal dibaca tidak dicoba ulang tiap frame.
 * Gunakan satu instance per konsumen (preview dan ekspor masing-masing punya sendiri).
 */
class AssetBitmapProvider(
    projectDir: File,
    private val maxEdge: Int = 1280
) : ImageSource {

    private val rootDir: File = projectDir.canonicalFile
    private val rootPrefix: String = rootDir.path + File.separator

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val resolved = HashMap<String, File?>()
    private val failed = HashSet<String>()

    @Synchronized
    override fun bitmap(src: String): Bitmap? {
        val file = resolve(src) ?: return null
        if (!file.isFile) return null

        val key = "$src|${file.lastModified()}|${file.length()}"
        cache.get(key)?.let { return it }
        if (key in failed) return null

        val bmp = decode(file)
        if (bmp == null) {
            failed.add(key)
            return null
        }
        cache.put(key, bmp)
        return bmp
    }

    private fun resolve(src: String): File? {
        resolved[src]?.let { return it }
        if (resolved.containsKey(src)) return null
        val file = if (AssetPaths.isSafeRelative(src)) {
            val candidate = File(rootDir, src)
            val canonical = runCatching { candidate.canonicalFile }.getOrNull()
            if (canonical != null && canonical.path.startsWith(rootPrefix)) canonical else null
        } else {
            null
        }
        resolved[src] = file
        return file
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val CACHE_KB = 32 * 1024
    }
}
