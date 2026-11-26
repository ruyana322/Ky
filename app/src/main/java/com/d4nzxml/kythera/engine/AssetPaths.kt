package com.d4nzxml.kythera.engine

import java.io.File

/** Aturan path relatif yang aman (tanpa Android, bisa di-unit-test). */
object AssetPaths {

    const val MAX_LENGTH = 200

    /** True jika [path] relatif, tanpa "..", tanpa drive/skema, dan tanpa segmen kosong. */
    fun isSafeRelative(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_LENGTH) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains(':') || path.contains('\u0000')) return false
        val segments = path.replace('\\', '/').split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }

    /** File di [root] untuk path relatif [rel]; null jika path tidak aman atau keluar dari [root]. */
    fun resolveInside(root: File, rel: String): File? {
        if (!isSafeRelative(rel)) return null
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(rootCanonical, rel).canonicalFile }.getOrNull() ?: return null
        return if (candidate.path.startsWith(rootCanonical.path + File.separator)) candidate else null
    }

    /** Nama file aman: hanya huruf, angka, titik, strip, underscore. */
    fun sanitizeFileName(name: String, maxLen: Int = 60): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.').take(maxLen)
}
