package com.telos.desktop.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.telos.desktop.linux.Sys
import com.telos.desktop.panel.AudioControlManager
import com.telos.desktop.panel.AudioState
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class MediaTab { Audio, Video, Photos }

@Composable
fun TelosMediaPlayer() {
    var state by remember { mutableStateOf(AudioState(0.0, false, false, emptyList(), emptyList(), "", "", false)) }
    var tab by remember { mutableStateOf(MediaTab.Audio) }
    var library by remember { mutableStateOf<List<File>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    val pending = TelosFileHandler.pendingMedia
    LaunchedEffect(pending?.absolutePath) {
        pending?.let { Sys.runBg("xdg-open", it.absolutePath) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { AudioControlManager.snapshot() }
            delay(1000)
        }
    }
    LaunchedEffect(tab) {
        library = withContext(Dispatchers.IO) {
            val xdg = XdgUserDirsManager.resolve()
            val want = when (tab) {
                MediaTab.Audio -> setOf("mp3", "flac", "ogg", "wav", "m4a", "aac", "opus")
                MediaTab.Video -> setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")
                MediaTab.Photos -> setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "raw", "dng")
            }
            val roots = when (tab) {
                MediaTab.Audio -> listOf(xdg.music, xdg.download)
                MediaTab.Video -> listOf(xdg.videos, xdg.download)
                MediaTab.Photos -> listOf(xdg.pictures, xdg.download)
            }
            roots.distinct().flatMap { scanLibrary(it, want, maxDepth = 4, maxHits = 400) }
                .distinctBy { it.absolutePath }
                .sortedBy { it.name.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            CandyCyberIcon(CandyIcon.Volume, 48.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(t("dl_m_tab"), color = TelosColors.secondary, fontSize = 20.sp)
                Text(state.track.ifBlank { pending?.name ?: t("music_widget_no_data") }, color = TelosColors.onSurface)
            }
        }
        Row(Modifier.padding(top = 16.dp)) {
            Button(onClick = { AudioControlManager.prev() }) { Text(t("hc_previous")) }
            Button(onClick = { AudioControlManager.playPause() }) { Text(if (state.playing) t("music_widget_pause") else t("hc_play")) }
            Button(onClick = { AudioControlManager.next() }) { Text(t("hc_next")) }
        }
        Text("${t("comms_mute")} ${state.volume.toInt()}%", color = TelosColors.onSurface, modifier = Modifier.padding(top = 8.dp))
        Row {
            Button(onClick = { AudioControlManager.volumeDown() }) { Text("-") }
            Button(onClick = { AudioControlManager.toggleMute() }) { Text(t("comms_mute")) }
            Button(onClick = { AudioControlManager.volumeUp() }) { Text("+") }
        }
        Row(Modifier.padding(top = 12.dp)) {
            FilterChip(selected = tab == MediaTab.Audio, onClick = { tab = MediaTab.Audio }, label = { Text(t("au_music_title")) })
            FilterChip(selected = tab == MediaTab.Video, onClick = { tab = MediaTab.Video }, label = { Text(t("hc_all_videos")) })
            FilterChip(selected = tab == MediaTab.Photos, onClick = { tab = MediaTab.Photos }, label = { Text(t("hc_photos")) })
        }
        OutlinedTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(t("hc_filter")) }, singleLine = true)
        val shown = remember(library, filter) {
            if (filter.isBlank()) library else library.filter { it.name.contains(filter, true) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(shown, key = { it.absolutePath }) { f ->
                Text(
                    f.name,
                    color = TelosColors.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (tab == MediaTab.Photos) TelosFileHandler.open(f) else AudioControlManager.open(f)
                    }.padding(8.dp),
                )
            }
        }
    }
}

internal fun scanLibrary(root: File, want: Set<String>, maxDepth: Int = 2, maxHits: Int = 200): List<File> {
    if (!root.isDirectory) return emptyList()
    val out = ArrayList<File>(64)
    val queue = ArrayDeque<Pair<File, Int>>()
    queue.add(root to 0)
    while (queue.isNotEmpty() && out.size < maxHits) {
        val (dir, depth) = queue.removeFirst()
        val kids = dir.listFiles() ?: continue
        for (child in kids) {
            if (out.size >= maxHits) break
            if (child.isDirectory) {
                if (depth < maxDepth && !child.name.startsWith(".")) queue.addLast(child to depth + 1)
            } else if (child.extension.lowercase() in want) {
                out += child
            }
        }
    }
    return out.sortedBy { it.name.lowercase() }
}
