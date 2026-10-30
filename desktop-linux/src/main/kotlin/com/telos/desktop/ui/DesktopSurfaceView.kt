package com.telos.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.DesktopFileParser
import com.telos.desktop.apps.TelosFileHandler
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.xdg.DesktopWatchService
import com.telos.desktop.xdg.XdgUserDirs
import java.io.File

data class DesktopItem(
    val file: File,
    val label: String,
    val iconName: String,
    val kind: Kind,
) {
    enum class Kind { File, Folder, DesktopShortcut, Lnk, Virtual }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopSurfaceView(
    dirs: XdgUserDirs,
    settings: TelosDesktopSettings,
    icons: LinuxIconThemeResolver,
    modifier: Modifier = Modifier,
    onEmptyRightClick: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(dirs.desktop.absolutePath) {
        val watcher = DesktopWatchService(dirs.desktop, scope) { generation++ }
        watcher.start()
        onDispose { watcher.stop() }
    }
    val items = remember(generation, settings.showDesktopIcons, settings.showHiddenFiles, settings.showLnkFiles, settings.showDesktopFiles, settings.showStandardFolders) {
        listDesktopItems(dirs, settings)
    }
    if (!settings.showDesktopIcons) {
        Box(modifier.combinedClickable(onClick = {}, onLongClick = onEmptyRightClick))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.file.absolutePath + it.label }) { item ->
            val card = RoundedCornerShape(12.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(88.dp)
                    .clip(card)
                    .background(TelosColors.card)
                    .border(1.dp, TelosColors.outline, card)
                    .combinedClickable(
                        onDoubleClick = { activate(item, onNotify) },
                        onClick = {},
                        onLongClick = onEmptyRightClick,
                    )
                    .padding(8.dp),
            ) {
                val iconFile = icons.resolve(item.iconName, 48) ?: if (item.file.isDirectory) icons.resolve("folder", 48) else icons.resolve("text-x-generic", 48)
                DiskIcon(iconFile, item.label, 48.dp)
                Text(
                    item.label,
                    color = TelosColors.onSurface,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun listDesktopItems(dirs: XdgUserDirs, settings: TelosDesktopSettings): List<DesktopItem> {
    val out = ArrayList<DesktopItem>()
    if (settings.showStandardFolders) {
        out += DesktopItem(dirs.home, "Home", "user-home", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.trash, "Trash", "user-trash", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.download, "Downloads", "folder-download", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.documents, "Documents", "folder-documents", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.pictures, "Pictures", "folder-pictures", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.music, "Music", "folder-music", DesktopItem.Kind.Virtual)
        out += DesktopItem(dirs.videos, "Videos", "folder-videos", DesktopItem.Kind.Virtual)
    }
    val files = dirs.desktop.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
    for (file in files) {
        if (!settings.showHiddenFiles && file.name.startsWith(".")) continue
        when {
            file.name.endsWith(".desktop") -> {
                if (!settings.showDesktopFiles) continue
                val parsed = DesktopFileParser.parse(file)
                out += DesktopItem(
                    file,
                    parsed?.name ?: file.nameWithoutExtension,
                    parsed?.icon ?: "application-x-executable",
                    DesktopItem.Kind.DesktopShortcut,
                )
            }
            file.name.endsWith(".lnk", true) -> {
                if (!settings.showLnkFiles) continue
                out += DesktopItem(file, file.nameWithoutExtension, "application-x-ms-dos-executable", DesktopItem.Kind.Lnk)
            }
            file.isDirectory -> out += DesktopItem(file, file.name, "folder", DesktopItem.Kind.Folder)
            else -> out += DesktopItem(file, file.name, "text-x-generic", DesktopItem.Kind.File)
        }
    }
    return out
}

private fun activate(item: DesktopItem, onNotify: (String) -> Unit) {
    when (item.kind) {
        DesktopItem.Kind.DesktopShortcut -> DesktopFileLauncher.launch(item.file)
        DesktopItem.Kind.Lnk -> {
            when (val r = DesktopFileLauncher.launchLnk(item.file)) {
                DesktopFileLauncher.LaunchLnkResult.LaunchedWithWine -> {}
                DesktopFileLauncher.LaunchLnkResult.WineMissing -> onNotify("Wine is not installed; cannot open .lnk")
                is DesktopFileLauncher.LaunchLnkResult.Failed -> onNotify(r.message)
            }
        }
        else -> TelosFileHandler.open(item.file)
    }
}
