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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelosFilesApp(start: File? = null, showHiddenFiles: Boolean = false) {
    var query by remember { mutableStateOf("") }
    var path by remember(start?.absolutePath) { mutableStateOf(start ?: TelosFilesNav.path) }
    var tick by remember { mutableStateOf(0) }
    val dir = path
    val trashRoot = remember { XdgTrash.filesDir() }
    val inTrash = remember(dir, tick) {
        val p = dir.absoluteFile
        val tdir = trashRoot.absoluteFile
        p == tdir || p.path.startsWith(tdir.path + File.separator)
    }
    val entries = remember(dir, query, showHiddenFiles, tick) {
        val list = dir.listFiles()
            ?.filter { showHiddenFiles || !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
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
        ).map { it }
    }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            shortcuts.forEach { (n, f) ->
                TextButton(onClick = { if (f.exists() || f.mkdirs()) { path = f; TelosFilesNav.path = f } }) {
                    Text(n, fontSize = 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { dir.parentFile?.let { path = it; TelosFilesNav.path = it } }) { Text(t("menu_back")) }
            TextButton(onClick = {
                val folder = File(dir, "Folder")
                var dest = folder
                var i = 1
                while (dest.exists()) dest = File(dir, "Folder $i").also { i++ }
                dest.mkdirs()
                tick++
            }) { Text(t("hc_new_folder")) }
            if (inTrash) {
                TextButton(onClick = { XdgTrash.empty(); tick++; path = trashRoot }) { Text(t("hc_delete")) }
            }
        }
        Text(dir.absolutePath, color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.absolutePath }) { f ->
                val size = remember(f.length(), f.isDirectory) {
                    if (f.isDirectory) "" else humanSize(f.length())
                }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            if (f.isDirectory) {
                                path = f
                                TelosFilesNav.path = f
                            } else if (inTrash) {
                                XdgTrash.restore(f)
                                tick++
                            } else {
                                TelosFileHandler.open(f)
                            }
                        },
                        onLongClick = {
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
                    CandyCyberIcon(if (f.isDirectory) CandyIcon.Folder else CandyIcon.Settings, 20.dp)
                    Text(
                        f.name + if (size.isNotEmpty()) "  $size" else "",
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
