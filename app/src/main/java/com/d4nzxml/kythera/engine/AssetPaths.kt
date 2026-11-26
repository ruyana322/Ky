package com.d4nzxml.kythera.engine

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

    /** Nama file aman: hanya huruf, angka, titik, strip, underscore. */
    fun sanitizeFileName(name: String, maxLen: Int = 60): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.').take(maxLen)
}
