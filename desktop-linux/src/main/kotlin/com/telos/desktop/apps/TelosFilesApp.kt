package com.telos.desktop.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File

object TelosFilesNav {
    var path by mutableStateOf(File(System.getProperty("user.home")))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelosFilesApp(start: File? = null) {
    var query by remember { mutableStateOf("") }
    var path by remember(start?.absolutePath) { mutableStateOf(start ?: TelosFilesNav.path) }
    val dir = path
    val entries = remember(dir, query) {
        val list = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        if (query.isBlank()) list else list.filter { it.name.contains(query, true) }
    }
    val xdg = remember { XdgUserDirsManager.resolve() }
    Column(Modifier.fillMaxSize().background(TelosColors.surface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { dir.parentFile?.let { path = it; TelosFilesNav.path = it } }) { Text(t("menu_back")) }
            Text(dir.absolutePath, color = TelosColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
        Row {
            listOf(t("pdft_files") to xdg.home, t("dl_title") to xdg.download, t("hc_photos") to xdg.pictures, t("au_music_title") to xdg.music).forEach { (n, f) ->
                TextButton(onClick = { if (f.exists()) { path = f; TelosFilesNav.path = f } }) { Text(n, fontSize = 11.sp) }
            }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t("hc_filter")) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.absolutePath }) { f ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            if (f.isDirectory) { path = f; TelosFilesNav.path = f }
                            else TelosFileHandler.open(f)
                        },
                        onDoubleClick = { TelosFileHandler.open(f) },
                    ).padding(8.dp),
                ) {
                    CandyCyberIcon(if (f.isDirectory) CandyIcon.Folder else CandyIcon.Settings, 20.dp)
                    Text(
                        f.name,
                        color = TelosColors.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
