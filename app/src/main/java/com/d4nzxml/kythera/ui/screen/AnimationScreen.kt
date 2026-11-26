package com.d4nzxml.kythera.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.project.CanvasAspect
import com.d4nzxml.kythera.project.ProjectStore
import com.d4nzxml.kythera.project.VideoProject
import com.d4nzxml.kythera.ui.components.GlassCard
import com.d4nzxml.kythera.ui.components.KBadge
import com.d4nzxml.kythera.ui.components.KFieldLabel
import com.d4nzxml.kythera.ui.components.KFormatTabButton
import com.d4nzxml.kythera.ui.components.KPrimaryButton
import com.d4nzxml.kythera.ui.components.KSectionHeader
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Animasi (BETA): daftar project. Tiap project adalah folder berisi scene.json dan aset (ikon/gambar).
 */
@Composable
fun AnimationScreen() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<VideoProject>>(emptyList()) }
    var opened by remember { mutableStateOf<VideoProject?>(null) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<VideoProject?>(null) }

    fun refresh() {
        scope.launch {
            projects = try {
                withContext(Dispatchers.IO) { store.list() }
            } catch (e: Exception) {
                status = false to "Gagal membaca daftar project: ${e.message}"
                emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = try {
                    val project = withContext(Dispatchers.IO) { store.importZip(context.contentResolver, uri) }
                    refresh()
                    true to "Project '${project.name}' berhasil diimpor"
                } catch (e: Exception) {
                    false to "Impor gagal: ${e.message}"
                }
            }
        }
    }

    val current = opened
    if (current != null) {
        ProjectWorkspace(
            project = current,
            store = store,
            onBack = {
                opened = null
                refresh()
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KSectionHeader("Animasi", Icons.Rounded.Movie)
            KBadge("BETA", KColor.Accent3)
        }

        GlassCard {
            Text(
                "Setiap project berupa folder: scene.json plus aset (ikon dan gambar). " +
                    "Folder bisa diekspor dan diimpor sebagai .zip.",
                color = KColor.Text3,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            KPrimaryButton(
                label = "Project baru",
                icon = Icons.Rounded.Add,
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KFormatTabButton("Impor folder (.zip)", false) {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                    )
                }
            }
        }

        status?.let { (ok, message) ->
            Text(message, color = if (ok) KColor.Accent else KColor.Accent2, fontSize = 12.sp)
        }

        if (projects.isEmpty()) {
            GlassCard {
                Text("Belum ada project. Buat project baru atau impor folder.", color = KColor.Text3, fontSize = 12.sp)
            }
        } else {
            projects.forEach { project ->
                GlassCard(modifier = Modifier.clickable { opened = project }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = KColor.Accent)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(project.name, color = KColor.Text, fontSize = 15.sp)
                            Text("Rasio ${project.aspect.label}", color = KColor.Text3, fontSize = 11.sp)
                        }
                        IconButton(onClick = { deleteTarget = project }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus project", tint = KColor.Text3)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NewProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, aspect ->
                showCreate = false
                scope.launch {
                    status = try {
                        val created = withContext(Dispatchers.IO) { store.create(name, aspect) }
                        refresh()
                        opened = created
                        null
                    } catch (e: Exception) {
                        false to "Gagal membuat project: ${e.message}"
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = KColor.Surface2,
            title = { Text("Hapus project?", color = KColor.Text) },
            text = {
                Text(
                    "'${target.name}' beserta scene dan asetnya akan dihapus dari app ini. " +
                        "Ekspor dulu kalau mau menyimpan salinannya.",
                    color = KColor.Text2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        status = try {
                            withContext(Dispatchers.IO) { store.delete(target) }
                            refresh()
                            true to "Project '${target.name}' dihapus"
                        } catch (e: Exception) {
                            false to "Gagal menghapus: ${e.message}"
                        }
                    }
                }) { Text("Hapus", color = KColor.Accent2) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal", color = KColor.Text2) }
            }
        )
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, CanvasAspect) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var aspect by remember { mutableStateOf(CanvasAspect.PORTRAIT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KColor.Surface2,
        title = { Text("Project baru", color = KColor.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= ProjectStore.MAX_NAME) name = it },
                    label = { Text("Nama project") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KColor.Accent,
                        unfocusedBorderColor = KColor.Border2,
                        focusedTextColor = KColor.Text,
                        unfocusedTextColor = KColor.Text,
                        cursorColor = KColor.Accent,
                        focusedLabelColor = KColor.Accent,
                        unfocusedLabelColor = KColor.Text3
                    )
                )
                KFieldLabel("Rasio canvas")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasAspect.entries.forEach { a ->
                        KFormatTabButton(a.label, aspect == a) { aspect = a }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, aspect) },
                enabled = name.isNotBlank()
            ) { Text("Buat", color = if (name.isNotBlank()) KColor.Accent else KColor.Text3) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = KColor.Text2) }
        }
    )
}
