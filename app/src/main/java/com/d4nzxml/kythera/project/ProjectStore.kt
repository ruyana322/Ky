package com.d4nzxml.kythera.project

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.d4nzxml.kythera.engine.AssetPaths
import com.d4nzxml.kythera.engine.DemoScene
import com.d4nzxml.kythera.engine.SceneJson
import com.d4nzxml.kythera.engine.SceneSpec
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class CanvasAspect(val label: String, val w: Int, val h: Int) {
    PORTRAIT("9:16", 9, 16),
    LANDSCAPE("16:9", 16, 9),
    SQUARE("1:1", 1, 1)
}

data class VideoProject(
    val id: String,
    val name: String,
    val aspect: CanvasAspect,
    val dir: File
)

data class AssetFile(val category: String, val fileName: String) {
    val relativePath: String get() = "assets/$category/$fileName"
}

/**
 * Project disimpan sebagai folder di penyimpanan internal app:
 *
 *   p_<id>/
 *     project.json      (nama, rasio)
 *     scene.json        (scene, lihat SceneJson)
 *     assets/icons/     (ikon)
 *     assets/images/    (gambar)
 *
 * Folder inilah yang diekspor/diimpor sebagai .zip. Semua fungsi bersifat blocking,
 * panggil dari Dispatchers.IO.
 */
class ProjectStore(context: Context) {

    private val root = File(context.filesDir, "jsvideo")

    init {
        root.mkdirs()
    }

    // ─── Daftar / buat / hapus ───────────────────────────────────────────────

    fun list(): List<VideoProject> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { dir -> runCatching { readProject(dir) }.getOrNull() }
            .sortedByDescending { File(it.dir, SCENE_FILE).lastModified() }

    fun create(name: String, aspect: CanvasAspect): VideoProject {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Nama project tidak boleh kosong" }
        require(clean.length <= MAX_NAME) { "Nama project maksimal $MAX_NAME karakter" }

        val dir = File(root, "p_${UUID.randomUUID()}")
        if (!dir.mkdirs()) throw IOException("Gagal membuat folder project")
        try {
            ASSET_CATEGORIES.forEach { File(dir, "assets/$it").mkdirs() }
            writeMeta(dir, clean, aspect)
            File(dir, SCENE_FILE).writeText(SceneJson.toJson(DemoScene.create()), Charsets.UTF_8)
            File(dir, SCRIPT_FILE).writeText(JsScripts.STARTER, Charsets.UTF_8)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return VideoProject(dir.name, clean, aspect, dir)
    }

    /** Hanya menghapus folder project milik app ini (di dalam folder jsvideo), tidak menyentuh file lain. */
    fun delete(project: VideoProject) {
        val parent = project.dir.parentFile?.canonicalFile
        require(parent == root.canonicalFile) { "Folder project tidak valid" }
        project.dir.deleteRecursively()
    }

    // ─── Scene ───────────────────────────────────────────────────────────────

    fun loadScene(project: VideoProject): SceneSpec {
        val file = File(project.dir, SCENE_FILE)
        return if (file.isFile) SceneJson.fromJson(file.readText(Charsets.UTF_8)) else DemoScene.create()
    }

    fun saveScene(project: VideoProject, scene: SceneSpec) {
        val json = SceneJson.toJson(scene)
        val target = File(project.dir, SCENE_FILE)
        val tmp = File(project.dir, "$SCENE_FILE.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }

    // ─── Aset ────────────────────────────────────────────────────────────────

    fun hasScene(project: VideoProject): Boolean = File(project.dir, SCENE_FILE).isFile

    fun loadScript(project: VideoProject): String? {
        val file = File(project.dir, SCRIPT_FILE)
        return if (file.isFile) file.readText(Charsets.UTF_8) else null
    }

    /** Simpan skrip JS. Skrip kosong tidak membuat file baru (project hanya-JSON tetap bersih). */
    fun saveScript(project: VideoProject, text: String) {
        require(text.length <= MAX_SCRIPT_CHARS) { "Skrip terlalu panjang (maks $MAX_SCRIPT_CHARS karakter)" }
        val target = File(project.dir, SCRIPT_FILE)
        if (text.isBlank() && !target.exists()) return
        val tmp = File(project.dir, "$SCRIPT_FILE.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun assets(project: VideoProject): List<AssetFile> =
        ASSET_CATEGORIES.flatMap { category ->
            (File(project.dir, "assets/$category").listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension.lowercase() in extensionsFor(category) }
                .sortedBy { it.name.lowercase() }
                .map { AssetFile(category, it.name) }
        }

    /** Salin gambar pilihan pengguna ke assets/<category>/. Mengembalikan path relatif. */
    fun addAsset(project: VideoProject, resolver: ContentResolver, uri: Uri, category: String): String {
        require(category in ASSET_CATEGORIES) { "Kategori aset tidak valid" }
        val displayName = queryDisplayName(resolver, uri) ?: "aset"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val isAudio = category == "audio"
        require(ext in extensionsFor(category)) {
            if (isAudio) "Format audio tidak didukung. Pakai MP3, M4A, WAV, OGG, atau AAC"
            else "Format tidak didukung. Pakai PNG, JPG, atau WEBP"
        }

        val base = AssetPaths.sanitizeFileName(displayName.substringBeforeLast('.', displayName)).ifEmpty { "aset" }
        val dir = File(project.dir, "assets/$category").apply { mkdirs() }
        var target = File(dir, "$base.$ext")
        var n = 1
        while (target.exists()) {
            target = File(dir, "${base}_$n.$ext")
            n++
        }

        val input = resolver.openInputStream(uri) ?: throw IOException("Tidak bisa membuka file")
        input.use { copyLimited(it, target, if (isAudio) MAX_AUDIO_BYTES else MAX_ASSET_BYTES) }
        if (isAudio) return "assets/$category/${target.name}"

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            maxOf(bounds.outWidth, bounds.outHeight) > MAX_IMAGE_EDGE
        ) {
            target.delete()
            throw IllegalArgumentException("File bukan gambar yang valid (maks $MAX_IMAGE_EDGE px)")
        }
        return "assets/$category/${target.name}"
    }

    // ─── Ekspor / impor folder (zip) ─────────────────────────────────────────

    fun exportZip(project: VideoProject, resolver: ContentResolver, dest: Uri) {
        val rootName = AssetPaths.sanitizeFileName(project.name).ifEmpty { "project" }
        val out = resolver.openOutputStream(dest, "wt") ?: throw IOException("Tidak bisa membuka file tujuan")
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            project.dir.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .forEach { file ->
                    val rel = file.relativeTo(project.dir).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry("$rootName/$rel"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    /**
     * Impor project dari zip. Zip dianggap tidak tepercaya: path dicek (anti zip-slip),
     * jumlah dan ukuran dibatasi, hanya file json/gambar yang diambil, dan scene divalidasi.
     */
    fun importZip(resolver: ContentResolver, src: Uri): VideoProject {
        val staging = File(root, ".import_${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("Gagal menyiapkan folder impor")
        try {
            val input = resolver.openInputStream(src) ?: throw IOException("Tidak bisa membuka file")
            var entries = 0
            var total = 0L
            val stagingPrefix = staging.canonicalPath + File.separator

            ZipInputStream(BufferedInputStream(input)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    require(AssetPaths.isSafeRelative(name)) { "Zip berisi path tidak aman: $name" }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in IMPORT_EXT) continue
                    val fileLimit = if (ext in AUDIO_EXT) MAX_AUDIO_BYTES else MAX_ASSET_BYTES

                    entries++
                    require(entries <= MAX_ZIP_ENTRIES) { "Terlalu banyak file di dalam zip" }

                    val target = File(staging, name)
                    require(target.canonicalPath.startsWith(stagingPrefix)) { "Path zip tidak aman: $name" }
                    target.parentFile?.mkdirs()

                    var fileBytes = 0L
                    target.outputStream().buffered().use { out ->
                        val buf = ByteArray(8 * 1024)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            fileBytes += n
                            total += n
                            require(fileBytes <= fileLimit) { "File '$name' terlalu besar" }
                            require(total <= MAX_IMPORT_TOTAL_BYTES) { "Isi zip terlalu besar" }
                            out.write(buf, 0, n)
                        }
                    }
                }
            }

            val base: File = when {
                isProjectRoot(staging) -> staging
                else -> staging.listFiles()?.singleOrNull { it.isDirectory && isProjectRoot(it) }
            } ?: throw IllegalArgumentException("Bukan project JS Video: tidak ada project.json, script.js, atau scene.json")

            val metaFile = File(base, META_FILE)
            val (name, aspect) = if (metaFile.isFile) {
                parseMeta(metaFile.readText(Charsets.UTF_8))
            } else {
                val fromZip = (queryDisplayName(resolver, src) ?: "").substringBeforeLast('.').trim().take(MAX_NAME)
                fromZip.ifEmpty { "Project impor" } to CanvasAspect.PORTRAIT
            }
            if (!metaFile.isFile) writeMeta(base, name, aspect)

            val sceneFile = File(base, SCENE_FILE)
            if (sceneFile.isFile) {
                SceneJson.fromJson(sceneFile.readText(Charsets.UTF_8)) // validasi saja
            }
            // Jika hanya ada script.js, scene dibuat saat project dibuka (skrip dijalankan).
            ASSET_CATEGORIES.forEach { File(base, "assets/$it").mkdirs() }

            val finalDir = File(root, "p_${UUID.randomUUID()}")
            if (!base.renameTo(finalDir)) throw IOException("Gagal memindahkan project hasil impor")
            return VideoProject(finalDir.name, name, aspect, finalDir)
        } finally {
            staging.deleteRecursively()
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private fun isProjectRoot(dir: File): Boolean =
        File(dir, META_FILE).isFile || File(dir, SCRIPT_FILE).isFile || File(dir, SCENE_FILE).isFile

    private fun extensionsFor(category: String): Set<String> =
        if (category == "audio") AUDIO_EXT else IMAGE_EXT

    private fun readProject(dir: File): VideoProject {
        val (name, aspect) = parseMeta(File(dir, META_FILE).readText(Charsets.UTF_8))
        return VideoProject(dir.name, name, aspect, dir)
    }

    private fun parseMeta(text: String): Pair<String, CanvasAspect> {
        val o = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("project.json bukan JSON yang valid")
        }
        require(o.optString("format") == FOLDER_FORMAT) { "Format project tidak dikenali" }
        val name = o.optString("name").trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME) { "Nama project tidak valid" }
        val aspect = CanvasAspect.entries.firstOrNull { it.name == o.optString("aspect") }
            ?: throw IllegalArgumentException("Rasio project tidak valid")
        return name to aspect
    }

    private fun writeMeta(dir: File, name: String, aspect: CanvasAspect) {
        val json = JSONObject()
            .put("format", FOLDER_FORMAT)
            .put("version", 1)
            .put("name", name)
            .put("aspect", aspect.name)
            .toString(2)
        File(dir, META_FILE).writeText(json, Charsets.UTF_8)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment

    private fun copyLimited(input: InputStream, target: File, maxBytes: Long) {
        var total = 0L
        try {
            target.outputStream().buffered().use { out ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    require(total <= maxBytes) { "File terlalu besar (maks ${maxBytes / 1024 / 1024} MB)" }
                    out.write(buf, 0, n)
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    companion object {
        const val META_FILE = "project.json"
        const val SCENE_FILE = "scene.json"
        const val SCRIPT_FILE = "script.js"
        const val FOLDER_FORMAT = "jsvideostudio.folder"
        const val MAX_NAME = 60
        val ASSET_CATEGORIES = listOf("icons", "images", "audio")
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
        private val AUDIO_EXT = setOf("mp3", "m4a", "wav", "ogg", "aac")
        private val IMPORT_EXT = IMAGE_EXT + AUDIO_EXT + setOf("json", "js")
        private const val MAX_ASSET_BYTES = 20L * 1024 * 1024
        private const val MAX_AUDIO_BYTES = 60L * 1024 * 1024
        private const val MAX_SCRIPT_CHARS = 200_000
        private const val MAX_IMPORT_TOTAL_BYTES = 300L * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 1000
        private const val MAX_IMAGE_EDGE = 8192
    }
}
