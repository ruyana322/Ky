package com.d4nzxml.kythera.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.engine.AudioClip
import com.d4nzxml.kythera.engine.ObjectType
import com.d4nzxml.kythera.engine.Preset
import com.d4nzxml.kythera.engine.SceneJson
import com.d4nzxml.kythera.engine.SceneObject
import com.d4nzxml.kythera.engine.SceneSpec
import com.d4nzxml.kythera.engine.withPreset
import com.d4nzxml.kythera.project.AssetBitmapProvider
import com.d4nzxml.kythera.project.AssetFile
import com.d4nzxml.kythera.project.CanvasAspect
import com.d4nzxml.kythera.project.JsSceneRunner
import com.d4nzxml.kythera.project.JsScripts
import com.d4nzxml.kythera.project.ProjectStore
import com.d4nzxml.kythera.project.VideoExporter
import com.d4nzxml.kythera.project.VideoProject
import com.d4nzxml.kythera.ui.components.GlassCard
import com.d4nzxml.kythera.ui.components.KBadge
import com.d4nzxml.kythera.ui.components.KFieldLabel
import com.d4nzxml.kythera.ui.components.KFormatTabButton
import com.d4nzxml.kythera.ui.components.KInfoRow
import com.d4nzxml.kythera.ui.components.KPrimaryButton
import com.d4nzxml.kythera.ui.components.KSectionHeader
import com.d4nzxml.kythera.ui.components.ScenePreview
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SCRIPT_CHARS = 200_000

@Composable
fun ProjectWorkspace(
    project: VideoProject,
    store: ProjectStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current

    var scene by remember(project.id) { mutableStateOf<SceneSpec?>(null) }
    var assets by remember(project.id) { mutableStateOf<List<AssetFile>>(emptyList()) }
    var status by remember(project.id) { mutableStateOf<Pair<Boolean, String>?>(null) }

    var scriptText by remember(project.id) { mutableStateOf("") }
    var ranText by remember(project.id) { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    var shortEdge by remember { mutableIntStateOf(720) }
    var fps by remember { mutableIntStateOf(30) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var pendingCategory by remember { mutableStateOf("icons") }

    val images = remember(project.id) { AssetBitmapProvider(project.dir, 1280) }

    // Layar tetap menyala selama ekspor supaya proses tidak terputus.
    DisposableEffect(exporting) {
        view.keepScreenOn = exporting
        onDispose { view.keepScreenOn = false }
    }

    /** Jalankan skrip JS, validasi hasilnya, lalu simpan skrip dan scene. */
    suspend fun executeScript(text: String) {
        running = true
        try {
            val json = JsSceneRunner(context).run(text)
            val parsed = SceneJson.fromJson(json)
            withContext(Dispatchers.IO) {
                store.saveScript(project, text)
                store.saveScene(project, parsed)
            }
            scene = parsed
            ranText = text
            status = true to "Skrip dijalankan: ${parsed.objects.size} objek, ${parsed.audio.size} audio"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = false to (e.message ?: "Skrip gagal dijalankan")
        } finally {
            running = false
        }
    }

    LaunchedEffect(project.id) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                Triple(store.loadScript(project), store.hasScene(project), store.assets(project))
            }
            scriptText = loaded.first ?: ""
            ranText = scriptText
            assets = loaded.third
            if (loaded.second || scriptText.isBlank()) {
                scene = withContext(Dispatchers.IO) { store.loadScene(project) }
            } else {
                // Project hasil impor yang hanya berisi script.js: jalankan skrip untuk membuat scene.
                executeScript(scriptText)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = false to "Gagal membuka project: ${e.message}"
        }
    }

    fun leave() {
        scope.launch {
            if (exporting) exportJob?.cancel()
            try {
                withContext(Dispatchers.IO) { store.saveScript(project, scriptText) }
            } catch (e: Exception) {
                // Draf gagal disimpan: tetap keluar, jangan menahan pengguna.
            }
            onBack()
        }
    }

    BackHandler { leave() }

    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val category = pendingCategory
            scope.launch {
                status = try {
                    val path = withContext(Dispatchers.IO) {
                        store.addAsset(project, context.contentResolver, uri, category)
                    }
                    assets = withContext(Dispatchers.IO) { store.assets(project) }
                    true to "Aset ditambahkan: $path"
                } catch (e: Exception) {
                    false to "Gagal menambah aset: ${e.message}"
                }
            }
        }
    }

    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        store.saveScript(project, scriptText)
                        store.exportZip(project, context.contentResolver, uri)
                    }
                    true to "Folder project diekspor sebagai zip"
                } catch (e: Exception) {
                    false to "Ekspor folder gagal: ${e.message}"
                }
            }
        }
    }

    fun addAssetToScene(asset: AssetFile, current: SceneSpec) {
        scope.launch {
            if (scriptText.isNotBlank()) {
                // Skrip adalah sumber scene: tambahkan baris kode, lalu jalankan.
                val newText = scriptText.trimEnd() + "\n\n" + snippetFor(asset) + "\n"
                scriptText = newText
                executeScript(newText)
            } else {
                val updated = addToSceneJson(current, asset)
                status = try {
                    withContext(Dispatchers.IO) { store.saveScene(project, updated) }
                    scene = updated
                    true to "${asset.fileName} ditambahkan ke scene"
                } catch (e: Exception) {
                    false to "Gagal menyimpan scene: ${e.message}"
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KFormatTabButton("‹ Kembali", false) { if (!exporting) leave() }
            Text(project.name, color = KColor.Text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            KBadge(project.aspect.label, KColor.Accent3)
        }

        status?.let { (ok, message) ->
            Text(message, color = if (ok) KColor.Accent else KColor.Accent2, fontSize = 12.sp)
        }

        val current = scene
        if (current == null) {
            GlassCard { Text("Memuat project…", color = KColor.Text3, fontSize = 12.sp) }
            return@Column
        }

        // ── Preview ─────────────────────────────────────────────
        GlassCard {
            ScenePreview(
                scene = current,
                aspectWidth = project.aspect.w,
                aspectHeight = project.aspect.h,
                images = images
            )
        }

        // ── Skrip JS ────────────────────────────────────────────
        GlassCard {
            KSectionHeader("Skrip JS", Icons.Rounded.Code)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tulis atau tempel JS di sini, lalu Jalankan untuk melihat hasilnya di preview. " +
                    "JS hanya mendeskripsikan scene (bukan menggambar), jadi preview dan hasil MP4 sama persis.",
                color = KColor.Text3,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = scriptText,
                onValueChange = { if (it.length <= MAX_SCRIPT_CHARS) scriptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 16,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = KColor.Text),
                placeholder = { Text("Taruh JS di sini…", color = KColor.Text3, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KColor.Accent,
                    unfocusedBorderColor = KColor.Border2,
                    focusedTextColor = KColor.Text,
                    unfocusedTextColor = KColor.Text,
                    cursorColor = KColor.Accent
                )
            )
            if (scriptText != ranText) {
                Spacer(Modifier.height(6.dp))
                Text("Skrip diubah. Tekan Jalankan untuk memperbarui preview.", color = KColor.Accent2, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            KPrimaryButton(
                label = if (running) "Menjalankan…" else "Jalankan & pratinjau",
                icon = Icons.Rounded.PlayArrow,
                enabled = !running && !exporting && scriptText.isNotBlank(),
                onClick = { scope.launch { executeScript(scriptText) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("Salin JS", false) {
                    clipboard.setText(AnnotatedString(scriptText))
                    status = true to "Skrip disalin ke clipboard"
                }
                KFormatTabButton("Tempel JS", false) {
                    val pasted = clipboard.getText()?.text.orEmpty()
                    if (pasted.isBlank()) {
                        status = false to "Clipboard kosong"
                    } else if (pasted.length > MAX_SCRIPT_CHARS) {
                        status = false to "Isi clipboard terlalu panjang (maks $MAX_SCRIPT_CHARS karakter)"
                    } else {
                        scriptText = pasted
                        status = true to "Skrip ditempel. Tekan Jalankan."
                    }
                }
                KFormatTabButton("Panduan AI", false) {
                    clipboard.setText(AnnotatedString(JsScripts.AI_GUIDE))
                    status = true to "Panduan disalin. Tempel ke AI, tulis ide animasimu, lalu tempel jawabannya ke sini."
                }
            }
        }

        // ── Info scene ──────────────────────────────────────────
        GlassCard {
            KSectionHeader("Scene", Icons.Rounded.Info, KColor.Accent3)
            Spacer(Modifier.height(8.dp))
            KInfoRow("Durasi", "${current.durationMs / 1000.0} detik")
            KInfoRow("Objek", current.objects.size.toString())
            KInfoRow("Audio", current.audio.size.toString())
            KInfoRow("Aset", assets.size.toString())
            Spacer(Modifier.height(8.dp))
            KFormatTabButton("Salin JSON scene", false) {
                clipboard.setText(AnnotatedString(SceneJson.toJson(current)))
                status = true to "JSON scene disalin ke clipboard"
            }
        }

        // ── Aset ────────────────────────────────────────────────
        GlassCard {
            KSectionHeader("Aset (ikon, gambar, audio)", Icons.Rounded.Image)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("Tambah ikon", false) {
                    pendingCategory = "icons"
                    assetPicker.launch(arrayOf("image/*"))
                }
                KFormatTabButton("Gambar/banner", false) {
                    pendingCategory = "images"
                    assetPicker.launch(arrayOf("image/*"))
                }
                KFormatTabButton("Audio", false) {
                    pendingCategory = "audio"
                    assetPicker.launch(arrayOf("audio/*"))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (assets.isEmpty()) {
                Text(
                    "Belum ada aset. Gambar: PNG/JPG/WEBP. Audio: MP3/M4A/WAV/OGG/AAC (narasi, musik, efek).",
                    color = KColor.Text3,
                    fontSize = 11.sp
                )
            } else {
                assets.forEach { asset ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(asset.fileName, color = KColor.Text, fontSize = 13.sp, maxLines = 1)
                            Text(asset.relativePath, color = KColor.Text3, fontSize = 10.sp, maxLines = 1)
                        }
                        KFormatTabButton("Ke scene", false) {
                            if (!running && !exporting) addAssetToScene(asset, current)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Di skrip, pakai path di atas, mis. s.image(\"assets/images/a.png\", { w: 0.3 }) atau s.audio(\"assets/audio/a.mp3\", { volume: 0.6 }).",
                    color = KColor.Text3,
                    fontSize = 10.sp
                )
            }
        }

        // ── Ekspor MP4 ──────────────────────────────────────────
        GlassCard {
            KSectionHeader("Ekspor MP4", Icons.Rounded.Movie)
            Spacer(Modifier.height(8.dp))
            KFieldLabel("Resolusi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("720p", shortEdge == 720) { if (!exporting) shortEdge = 720 }
                KFormatTabButton("1080p", shortEdge == 1080) { if (!exporting) shortEdge = 1080 }
            }
            Spacer(Modifier.height(8.dp))
            KFieldLabel("Frame rate")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 30, 60).forEach { f ->
                    KFormatTabButton("$f fps", fps == f) { if (!exporting) fps = f }
                }
            }
            Spacer(Modifier.height(12.dp))
            val (w, h) = outputSize(project.aspect, shortEdge)
            val audioNote = if (current.audio.isEmpty()) "tanpa audio" else "${current.audio.size} audio dicampur"
            Text("Hasil: ${w}x$h, H.264, $fps fps, $audioNote", color = KColor.Text3, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            KPrimaryButton(
                label = if (exporting) "Sedang mengekspor…" else "Ekspor MP4",
                icon = Icons.Rounded.Movie,
                enabled = !exporting && !running,
                onClick = {
                    exportJob = scope.launch {
                        exporting = true
                        progress = 0f
                        progressText = "Menyiapkan…"
                        status = null
                        try {
                            // Pastikan scene yang diekspor = skrip terbaru.
                            if (scriptText.isNotBlank() && scriptText != ranText) {
                                progressText = "Menjalankan skrip…"
                                executeScript(scriptText)
                                if (scriptText != ranText) return@launch
                            }
                            val sceneNow = scene ?: return@launch
                            val result = VideoExporter(context).export(
                                project, sceneNow, VideoExporter.Options(w, h, fps)
                            ) { p, label ->
                                progress = p
                                progressText = label
                            }
                            status = if (result.success) {
                                true to "Selesai. Tersimpan di ${result.outputPath}"
                            } else {
                                false to (result.errorMessage ?: "Ekspor gagal")
                            }
                        } catch (e: CancellationException) {
                            status = false to "Ekspor dibatalkan"
                            throw e
                        } catch (e: Exception) {
                            status = false to "Ekspor gagal: ${e.message}"
                        } finally {
                            exporting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (exporting) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = KColor.Accent,
                    trackColor = KColor.Border2
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(progressText, color = KColor.Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    KFormatTabButton("Batal", false) { exportJob?.cancel() }
                }
            }
        }

        // ── Ekspor folder ───────────────────────────────────────
        GlassCard {
            KSectionHeader("Folder project", Icons.Rounded.Folder)
            Spacer(Modifier.height(8.dp))
            Text(
                "Isi folder:\n  project.json\n  script.js\n  scene.json\n  assets/icons/\n  assets/images/\n  assets/audio/",
                color = KColor.Text3,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Zip dari AI atau dari HP lain bisa diimpor lewat daftar project. " +
                    "Cukup ada script.js (project.json boleh tidak ada) plus folder assets.",
                color = KColor.Text3,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            KFormatTabButton("Ekspor folder (.zip)", false) {
                val safe = project.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "project" }
                zipExporter.launch("$safe.zip")
            }
        }
    }
}

/** Ukuran video genap sesuai rasio. 720 → 1280x720, 1080 → 1920x1080 (dibalik untuk portrait). */
private fun outputSize(aspect: CanvasAspect, shortEdge: Int): Pair<Int, Int> = when (aspect) {
    CanvasAspect.PORTRAIT -> shortEdge to shortEdge * 16 / 9
    CanvasAspect.LANDSCAPE -> shortEdge * 16 / 9 to shortEdge
    CanvasAspect.SQUARE -> shortEdge to shortEdge
}

/** Baris kode yang ditambahkan ke skrip untuk sebuah aset. Nama file sudah disanitasi (tanpa tanda kutip). */
private fun snippetFor(asset: AssetFile): String =
    if (asset.category == "audio") {
        "scene.audio(\"${asset.relativePath}\", { start: 0, volume: 1, kind: \"music\" });"
    } else {
        "scene.image(\"${asset.relativePath}\", { x: 0.5, y: 0.5, w: 0.25 }).zoomIn(0, 0.6);"
    }

/** Untuk project tanpa skrip: tambahkan aset langsung ke scene (JSON). ID dijamin unik. */
private fun addToSceneJson(scene: SceneSpec, asset: AssetFile): SceneSpec {
    if (asset.category == "audio") {
        var n = scene.audio.size + 1
        val existing = scene.audio.map { it.id }.toSet()
        while ("a$n" in existing) n++
        return scene.copy(audio = scene.audio + AudioClip(id = "a$n", src = asset.relativePath))
    }

    var n = scene.objects.count { it.type == ObjectType.IMAGE } + 1
    val existing = scene.objects.map { it.id }.toSet()
    while ("img_$n" in existing) n++

    val obj = SceneObject(
        id = "img_$n",
        type = ObjectType.IMAGE,
        x = 0.5f,
        y = 0.5f,
        width = 0.25f,
        height = 0.25f,
        zIndex = (scene.objects.maxOfOrNull { it.zIndex } ?: 0) + 1,
        src = asset.relativePath
    ).withPreset(Preset.ZOOM_IN, startMs = 0, durationMs = 600)

    return scene.copy(objects = scene.objects + obj)
}
