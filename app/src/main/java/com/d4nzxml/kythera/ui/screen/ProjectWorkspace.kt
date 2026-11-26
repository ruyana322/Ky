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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.engine.Preset
import com.d4nzxml.kythera.engine.ObjectType
import com.d4nzxml.kythera.engine.SceneJson
import com.d4nzxml.kythera.engine.SceneObject
import com.d4nzxml.kythera.engine.SceneSpec
import com.d4nzxml.kythera.engine.withPreset
import com.d4nzxml.kythera.project.AssetBitmapProvider
import com.d4nzxml.kythera.project.AssetFile
import com.d4nzxml.kythera.project.CanvasAspect
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

    BackHandler(onBack = onBack)

    var scene by remember(project.id) { mutableStateOf<SceneSpec?>(null) }
    var assets by remember(project.id) { mutableStateOf<List<AssetFile>>(emptyList()) }
    var status by remember(project.id) { mutableStateOf<Pair<Boolean, String>?>(null) }

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

    LaunchedEffect(project.id) {
        try {
            val loaded = withContext(Dispatchers.IO) { store.loadScene(project) to store.assets(project) }
            scene = loaded.first
            assets = loaded.second
        } catch (e: Exception) {
            status = false to "Gagal membaca scene: ${e.message}"
        }
    }

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
                    withContext(Dispatchers.IO) { store.exportZip(project, context.contentResolver, uri) }
                    true to "Folder project diekspor sebagai zip"
                } catch (e: Exception) {
                    false to "Ekspor folder gagal: ${e.message}"
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
            KFormatTabButton("‹ Kembali", false) { if (!exporting) onBack() }
            Text(project.name, color = KColor.Text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            KBadge(project.aspect.label, KColor.Accent3)
        }

        status?.let { (ok, message) ->
            Text(message, color = if (ok) KColor.Accent else KColor.Accent2, fontSize = 12.sp)
        }

        val current = scene
        if (current == null) {
            GlassCard { Text("Memuat scene…", color = KColor.Text3, fontSize = 12.sp) }
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

        GlassCard {
            KSectionHeader("Scene", Icons.Rounded.Info, KColor.Accent3)
            Spacer(Modifier.height(8.dp))
            KInfoRow("Durasi", "${current.durationMs / 1000.0} detik")
            KInfoRow("Objek", current.objects.size.toString())
            KInfoRow("Aset", assets.size.toString())
            Spacer(Modifier.height(10.dp))
            KFieldLabel("JSON scene (edit di editor teks, lalu tempel kembali)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("Salin JSON", false) {
                    clipboard.setText(AnnotatedString(SceneJson.toJson(current)))
                    status = true to "JSON scene disalin ke clipboard"
                }
                KFormatTabButton("Tempel JSON", false) {
                    val text = clipboard.getText()?.text.orEmpty()
                    scope.launch {
                        status = try {
                            val parsed = SceneJson.fromJson(text)
                            withContext(Dispatchers.IO) { store.saveScene(project, parsed) }
                            scene = parsed
                            true to "Scene diperbarui dari clipboard (${parsed.objects.size} objek)"
                        } catch (e: Exception) {
                            false to "JSON ditolak: ${e.message}"
                        }
                    }
                }
            }
        }

        // ── Aset ────────────────────────────────────────────────
        GlassCard {
            KSectionHeader("Aset (ikon dan gambar)", Icons.Rounded.Image)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("Tambah ikon", false) {
                    pendingCategory = "icons"
                    assetPicker.launch(arrayOf("image/*"))
                }
                KFormatTabButton("Tambah gambar", false) {
                    pendingCategory = "images"
                    assetPicker.launch(arrayOf("image/*"))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (assets.isEmpty()) {
                Text("Belum ada aset. PNG, JPG, atau WEBP.", color = KColor.Text3, fontSize = 11.sp)
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
                            val updated = addImageToScene(current, asset)
                            scope.launch {
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
                }
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
            Text("Hasil: ${w}x$h, H.264, $fps fps", color = KColor.Text3, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            KPrimaryButton(
                label = if (exporting) "Sedang mengekspor…" else "Ekspor MP4",
                icon = Icons.Rounded.Movie,
                enabled = !exporting,
                onClick = {
                    exportJob = scope.launch {
                        exporting = true
                        progress = 0f
                        progressText = "Menyiapkan…"
                        status = null
                        try {
                            val result = VideoExporter(context).export(
                                project, current, VideoExporter.Options(w, h, fps)
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
                "Isi folder:\n  project.json\n  scene.json\n  assets/icons/\n  assets/images/",
                color = KColor.Text3,
                fontSize = 11.sp
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

/** Tambah objek IMAGE di tengah canvas dengan animasi zoom-in singkat. ID dijamin unik. */
private fun addImageToScene(scene: SceneSpec, asset: AssetFile): SceneSpec {
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
