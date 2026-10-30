package com.telos.desktop.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.files.ArchivePasswordException
import com.telos.desktop.files.NetworkPlaces
import com.telos.desktop.files.ZipArchives
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.xdg.XdgTrash
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File

object TelosFilesNav {
    var path by mutableStateOf(File(System.getProperty("user.home")))
}

private data class FilesRow(
    val name: String,
    val directory: Boolean,
    val disk: File?,
    val zipInner: String?,
    val size: Long,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelosFilesApp(
    start: File? = null,
    showHiddenFiles: Boolean = false,
    extraPlaces: List<com.telos.desktop.files.NetworkPlace> = emptyList(),
    onPathChange: (File) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var path by remember(start?.absolutePath) { mutableStateOf(start ?: TelosFilesNav.path) }
    androidx.compose.runtime.LaunchedEffect(path) {
        TelosFilesNav.path = path
        onPathChange(path)
    }
    var zipPrefix by remember { mutableStateOf("") }
    var archiveSecret by remember { mutableStateOf("") }
    var needPassword by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val dir = path
    val inZip = ZipArchives.isArchive(dir)
    val trashRoot = remember { XdgTrash.filesDir() }
    val inTrash = remember(dir, tick) {
        val p = dir.absoluteFile
        val tdir = trashRoot.absoluteFile
        p == tdir || p.path.startsWith(tdir.path + File.separator)
    }
    val rows = remember(dir, query, showHiddenFiles, tick, zipPrefix, archiveSecret) {
        val list = if (inZip) {
            try {
                val secret = archiveSecret.toCharArray().takeIf { it.isNotEmpty() }
                val kids = ZipArchives.listChildren(dir, zipPrefix, secret).map {
                    FilesRow(it.name, it.directory, dir, it.inner, it.size)
                }
                needPassword = false
                kids
            } catch (_: ArchivePasswordException) {
                needPassword = true
                emptyList()
            }
        } else {
            dir.listFiles()
                ?.filter { showHiddenFiles || !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory && !ZipArchives.isArchive(it) }, { it.name.lowercase() }))
                ?.map {
                    FilesRow(it.name, it.isDirectory || ZipArchives.isArchive(it), it, null, if (it.isFile) it.length() else 0)
                }
                ?: emptyList()
        }
        if (query.isBlank()) list else list.filter { it.name.contains(query, true) }
    }
    val xdg = remember { XdgUserDirsManager.resolve() }
    val shortcuts = remember(xdg) {
        listOf(
            t("pdft_files") to xdg.home,
            t("dl_title") to xdg.download,
            t("file_type_document") to xdg.documents,
            t("au_music_title") to xdg.music,
            t("hc_photos") to xdg.pictures,
            t("hc_all_videos") to xdg.videos,
            t("ermis_trash") to xdg.trash,
        )
    }
    val net = remember(tick, extraPlaces) { NetworkPlaces.places() + extraPlaces }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            shortcuts.forEach { (n, f) ->
                TextButton(onClick = {
                    zipPrefix = ""
                    archiveSecret = ""
                    if (f.exists() || f.mkdirs()) {
                        path = f
                        TelosFilesNav.path = f
                    }
                }) { Text(n, fontSize = 11.sp) }
            }
            net.forEach { place ->
                TextButton(onClick = {
                    zipPrefix = ""
                    archiveSecret = ""
                    val local = NetworkPlaces.open(place)
                    if (local != null) {
                        path = local
                        TelosFilesNav.path = local
                    }
                    tick++
                }) { Text(place.label, fontSize = 11.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                if (inZip && zipPrefix.isNotEmpty()) {
                    zipPrefix = zipPrefix.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
                } else {
                    zipPrefix = ""
                    archiveSecret = ""
                    dir.parentFile?.let { path = it; TelosFilesNav.path = it }
                }
            }) { Text(t("menu_back")) }
            if (!inZip) {
                TextButton(onClick = {
                    val folder = File(dir, "Folder")
                    var dest = folder
                    var i = 1
                    while (dest.exists()) dest = File(dir, "Folder $i").also { i++ }
                    dest.mkdirs()
                    tick++
                }) { Text(t("hc_new_folder")) }
                TextButton(onClick = {
                    val zip = File(dir.parentFile ?: dir, dir.name + ".zip")
                    ZipArchives.compressDir(dir, zip)
                    tick++
                }) { Text(t("hc_compress_to_zip")) }
            } else {
                TextButton(onClick = {
                    val dest = File(dir.parentFile ?: dir, dir.nameWithoutExtension)
                    ZipArchives.extractAll(dir, dest, archiveSecret.toCharArray().takeIf { it.isNotEmpty() })
                    zipPrefix = ""
                    path = dest
                    TelosFilesNav.path = dest
                    tick++
                }) { Text(t("hc_extract_here")) }
            }
            if (inTrash) {
                TextButton(onClick = { XdgTrash.empty(); tick++; path = trashRoot }) { Text(t("hc_delete")) }
            }
        }
        Text(
            if (inZip) "${ZipArchives.archiveRoot(dir).absolutePath} :: ${zipPrefix.ifBlank { "/" }}" else dir.absolutePath,
            color = TelosColors.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (inZip && ZipArchives.isSplitZip(dir)) {
            Text(t("files_archive_split_note"), color = TelosColors.secondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        if (needPassword) {
            OutlinedTextField(
                archiveSecret,
                { archiveSecret = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("hc_password")) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.zipInner ?: it.disk?.absolutePath ?: it.name }) { row ->
                val size = if (row.directory) "" else humanSize(row.size)
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            when {
                                row.zipInner != null && row.directory -> zipPrefix = row.zipInner
                                row.zipInner != null -> {
                                    val cache = File(System.getProperty("java.io.tmpdir"), "telos-zip")
                                    val extracted = ZipArchives.extractEntry(
                                        dir,
                                        row.zipInner,
                                        cache,
                                        archiveSecret.toCharArray().takeIf { it.isNotEmpty() },
                                    )
                                    extracted?.let { TelosFileHandler.open(it) }
                                }
                                row.disk != null && (row.disk.isDirectory || ZipArchives.isArchive(row.disk)) -> {
                                    zipPrefix = ""
                                    archiveSecret = ""
                                    val next = if (ZipArchives.isArchive(row.disk)) ZipArchives.archiveRoot(row.disk) else row.disk
                                    path = next
                                    TelosFilesNav.path = next
                                }
                                inTrash && row.disk != null -> {
                                    XdgTrash.restore(row.disk)
                                    tick++
                                }
                                row.disk != null -> TelosFileHandler.open(row.disk)
                            }
                        },
                        onLongClick = {
                            val f = row.disk ?: return@combinedClickable
                            if (inZip) return@combinedClickable
                            if (inTrash) {
                                f.deleteRecursively()
                                File(XdgTrash.infoDir(), f.name + ".trashinfo").delete()
                            } else {
                                XdgTrash.moveToTrash(f)
                            }
                            tick++
                        },
                    ).padding(8.dp),
                ) {
                    CandyCyberIcon(if (row.directory) CandyIcon.Folder else CandyIcon.Settings, 20.dp)
                    Text(
                        row.name + if (size.isNotEmpty()) "  $size" else "",
                        color = TelosColors.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}
