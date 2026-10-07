package de.mm20.launcher2.ui.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import de.mm20.launcher2.ui.files.remote.ConnectionsRoute
import de.mm20.launcher2.ui.files.remote.RemotePath
import java.io.File

@Serializable
data object FilesRoute : NavKey

private typealias Icons = de.mm20.launcher2.base.R.drawable

private fun hasAllFilesAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/**
 * Telos Files: a file manager. The layout follows Solid Explorer (storage drawer, breadcrumbs,
 * selection bar, paste bar, tinted type icons) and MiXplorer (details such as permissions and
 * checksums, root tools).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen() {
    val vm: FilesViewModel = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    var hasAccess by remember { mutableStateOf(hasAllFilesAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = hasAllFilesAccess(context); vm.refreshVolumes(); vm.reloadConnections() }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasAccess = hasAllFilesAccess(context) }

    if (!hasAccess) {
        AccessGate(onAllow = {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else {
                legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        })
        return
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }

    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(!drawer.isOpen) {
        if (searching && vm.query.isEmpty() && vm.searchResults == null) searching = false
        else if (!vm.up()) backStack.removeLastOrNull()
        if (vm.query.isEmpty()) searching = false
    }

    val selecting = vm.selection.isNotEmpty()
    val shown = vm.visible
    val onOpenEntry: (FsEntry) -> Unit = { entry ->
        when {
            vm.selection.isNotEmpty() -> vm.toggleSelected(entry)
            entry.isDir && !RemotePath.isRemote(entry.path) && !ArchivePath.isArchive(entry.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(entry.path) &&
                de.mm20.launcher2.ui.files.vault.VaultPath.isVaultFolder(java.io.File(entry.path)) ->
                if (de.mm20.launcher2.ui.files.vault.VaultSessions.isUnlocked(entry.path)) vm.open(de.mm20.launcher2.ui.files.vault.VaultPath.build(entry.path, "/")) else dialog = FilesDialog.Unlock(entry)
            entry.isDir -> vm.open(entry.path)
            ArchivePath.canOpen(entry.name) && !RemotePath.isRemote(entry.path) && !ArchivePath.isArchive(entry.path) -> dialog = FilesDialog.Archive(entry)
            (RemotePath.isRemote(entry.path) || ArchivePath.isArchive(entry.path) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(entry.path)) -> vm.download(entry) { file ->
                FileActions.open(context, FsEntry(file.path, file.name, false, file.length(), file.lastModified()), emptyList(), false)
            }
            else -> FileActions.open(context, entry, vm.entries, vm.rootMode)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                StorageDrawer(
                    vm = vm,
                    onGo = { target -> vm.open(target); scope.launch { drawer.close() } },
                    onRoot = { scope.launch { drawer.close() }; if (vm.rootMode) vm.disableRoot() else dialog = FilesDialog.EnableRoot },
                    onManage = { scope.launch { drawer.close() }; backStack.add(ConnectionsRoute) },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    when {
                        selecting -> SelectionBar(
                            count = vm.selection.size,
                            single = vm.selectedEntries().singleOrNull(),
                            onClose = vm::clearSelection,
                            onSelectAll = vm::selectAll,
                            onCopy = { vm.copyToClipboard(false) },
                            onCut = { vm.copyToClipboard(true) },
                            onDelete = { dialog = FilesDialog.Delete(vm.selectedEntries()) },
                            onShare = { FileActions.share(context, vm.selectedEntries(), vm.rootMode) },
                            onRename = { vm.selectedEntries().singleOrNull()?.let { dialog = FilesDialog.Rename(it) } },
                            onCompress = { dialog = FilesDialog.Compress(vm.selectedEntries()) },
                            onProperties = { vm.selectedEntries().singleOrNull()?.let { dialog = FilesDialog.Properties(it) } },
                            onBookmark = { vm.selectedEntries().singleOrNull()?.let { vm.toggleBookmark(it.path); vm.clearSelection() } },
                        )
                        searching -> SearchBar(
                            query = vm.query,
                            onQuery = vm::search,
                            onClose = { searching = false; vm.clearSearch() },
                        )
                        else -> TopAppBar(
                            title = { Text(vm.path?.let { vm.connectionName(it)?.takeIf { _ -> RemotePath.isRoot(it) } ?: if (ArchivePath.isRoot(it)) nameOf(ArchivePath.archiveOf(it)) else if (de.mm20.launcher2.ui.files.vault.VaultPath.isRoot(it)) nameOf(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(it)) else nameOf(it) } ?: "Telos Files", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(painterResource(Icons.storage_24px), contentDescription = "Storage")
                                }
                            },
                            actions = {
                                if (vm.path != null) IconButton(onClick = { searching = true }) {
                                    Icon(painterResource(Icons.search_24px), contentDescription = "Search")
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(painterResource(Icons.more_vert_24px), contentDescription = "More")
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(text = { Text(if (vm.grid) "List view" else "Grid view") }, onClick = { menuOpen = false; vm.toggleGrid() })
                                        DropdownMenuItem(text = { Text("Sort by…") }, onClick = { menuOpen = false; dialog = FilesDialog.Sort })
                                        DropdownMenuItem(text = { Text(if (vm.showHidden) "Hide hidden files" else "Show hidden files") }, onClick = { menuOpen = false; vm.toggleHidden() })
                                        vm.path?.takeIf { de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it) }?.let { vp ->
                                            DropdownMenuItem(text = { Text("Lock vault") }, onClick = { menuOpen = false; vm.lockVault(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(vp)) })
                                        }
                                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; vm.reload(); vm.refreshVolumes() })
                                        vm.path?.let { p ->
                                            DropdownMenuItem(
                                                text = { Text(if (vm.isBookmarked(p)) "Remove from favorites" else "Add to favorites") },
                                                onClick = { menuOpen = false; vm.toggleBookmark(p) },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(if (vm.rootMode) "Turn root explorer off" else "Root explorer…") },
                                            onClick = { menuOpen = false; if (vm.rootMode) vm.disableRoot() else dialog = FilesDialog.EnableRoot },
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                    if (vm.path != null && !selecting) Breadcrumbs(vm) { vm.open(it) }
                    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            floatingActionButton = {
                if (vm.path != null && !selecting && vm.error == null) {
                    Box {
                        FloatingActionButton(onClick = { addMenu = true }) { Icon(painterResource(Icons.add_24px), contentDescription = "New") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { addMenu = false; dialog = FilesDialog.NewFolder })
                            DropdownMenuItem(text = { Text("New file") }, onClick = { addMenu = false; dialog = FilesDialog.NewFile })
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    vm.task?.let { TaskBar(it) }
                    val clip = vm.clipboard
                    if (clip != null && vm.path != null && vm.task == null) PasteBar(
                        count = clip.paths.size, cut = clip.cut,
                        onPaste = vm::paste, onCancel = vm::clearClipboard,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val current = vm.path
                when {
                    current == null -> HomePage(vm)
                    vm.error != null -> ErrorPage(vm.error!!, rootMode = vm.rootMode, onRoot = { dialog = FilesDialog.EnableRoot })
                    else -> Column(Modifier.fillMaxSize()) {
                        if (FileActions.isSystemPath(current, vm.rootMode)) RootBanner()
                        if (shown.isEmpty() && !vm.loading) {
                            EmptyPage(if (vm.query.isNotEmpty()) "Nothing found" else "This folder is empty")
                        } else if (vm.grid) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(112.dp),
                                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                gridItems(shown, key = { it.path }) { e ->
                                    GridCell(e, e.path in vm.selection, { onOpenEntry(e) }, { vm.toggleSelected(e) })
                                }
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                                items(shown, key = { it.path }) { e ->
                                    FileRow(e, e.path in vm.selection, vm.rootMode, { onOpenEntry(e) }, { vm.toggleSelected(e) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FilesDialogs(vm, dialog) { dialog = null }
}

// ---------------------------------------------------------------- dialogs

private sealed interface FilesDialog {
    data object NewFolder : FilesDialog
    data object NewFile : FilesDialog
    data object Sort : FilesDialog
    data object EnableRoot : FilesDialog
    data class Rename(val entry: FsEntry) : FilesDialog
    data class Delete(val entries: List<FsEntry>) : FilesDialog
    data class Compress(val entries: List<FsEntry>) : FilesDialog
    data class Properties(val entry: FsEntry) : FilesDialog
    data class Archive(val entry: FsEntry) : FilesDialog
    data class Unlock(val entry: FsEntry) : FilesDialog
}

@Composable
private fun FilesDialogs(vm: FilesViewModel, dialog: FilesDialog?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (dialog) {
        null -> {}
        FilesDialog.NewFolder -> NameDialog("New folder", "", "Create", onDismiss) { vm.newFolder(it); onDismiss() }
        FilesDialog.NewFile -> NameDialog("New file", "", "Create", onDismiss) { vm.newFile(it); onDismiss() }
        is FilesDialog.Rename -> NameDialog("Rename", dialog.entry.name, "Rename", onDismiss) { vm.rename(dialog.entry, it); onDismiss() }
        is FilesDialog.Compress -> NameDialog("Compress to zip", dialog.entries.first().name.substringBeforeLast('.'), "Compress", onDismiss) {
            vm.compress(dialog.entries, it); vm.clearSelection(); onDismiss()
        }
        is FilesDialog.Delete -> {
            val system = dialog.entries.any { FileActions.isSystemPath(it.path, vm.rootMode) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (dialog.entries.size == 1) "Delete \"${dialog.entries.first().name}\"?" else "Delete ${dialog.entries.size} items?") },
                text = {
                    Text(
                        if (system) "This is a system location. Deleting files here can stop apps or the whole phone from working, and it cannot be undone."
                        else "This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.delete(dialog.entries); onDismiss() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        FilesDialog.Sort -> SortDialog(vm, onDismiss)
        FilesDialog.EnableRoot -> RootWarningDialog(onDismiss) { vm.enableRoot { onDismiss() } }
        is FilesDialog.Archive -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialog.entry.name) },
            text = { Text("Look inside the archive, or unpack it into a folder next to it?") },
            confirmButton = { TextButton(onClick = { vm.open(ArchivePath.build(dialog.entry.path, "/")); onDismiss() }) { Text("Browse") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.extract(dialog.entry); onDismiss() }) { Text("Extract here") }
                    TextButton(onClick = { FileActions.openWith(context, dialog.entry, vm.rootMode); onDismiss() }) { Text("Open with…") }
                }
            },
        )
        is FilesDialog.Unlock -> UnlockVaultDialog(vm, dialog.entry, onDismiss)
        is FilesDialog.Properties -> PropertiesDialog(vm, dialog.entry, onDismiss)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank() && '/' !in text, onClick = { onDone(text.trim()) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SortDialog(vm: FilesViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                SortKey.values().forEach { key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.updateSort(vm.sort.copy(key = key)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = vm.sort.key == key, onClick = { vm.updateSort(vm.sort.copy(key = key)) })
                        Text(key.label, Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow("Descending", !vm.sort.ascending) { vm.updateSort(vm.sort.copy(ascending = !it)) }
                SwitchRow("Folders first", vm.sort.foldersFirst) { vm.updateSort(vm.sort.copy(foldersFirst = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RootWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Root explorer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telos Files will ask for superuser access so that you can see and change files that apps normally cannot reach, such as /system, /data and /vendor.")
                Text(
                    "Careless changes here can stop apps from working, make the phone bootloop or erase your data. " +
                        "Nobody can undo a deletion. Change only what you understand, and keep a backup.",
                    color = MaterialTheme.colorScheme.error,
                )
                Text("Your root manager (Magisk, KernelSU, ...) will ask you to allow Telos first. Root access ends when you turn it off here.")
                Row(Modifier.clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("I understand the risks")
                }
            }
        },
        confirmButton = { TextButton(enabled = understood, onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PropertiesDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf<Long?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var hashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(entry.path) {
        size = vm.sizeOf(entry)
        if (entry.isDir) count = vm.childCount(entry)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Detail("Type", entry.kind.label + if (entry.extension.isNotEmpty()) " (.${entry.extension})" else "")
                Detail("Location", parentOf(entry.path) ?: "/")
                Detail("Size", size?.let { if (it < 0) "Unknown" else formatSize(it) + if (it >= 1024) " ($it bytes)" else "" } ?: "Calculating…")
                count?.let { if (it >= 0) Detail("Contains", "$it items") }
                Detail("Modified", formatDate(entry.modified).ifEmpty { "Unknown" })
                if (entry.permissions.isNotEmpty()) Detail("Permissions", entry.permissions)
                if (entry.owner.isNotEmpty()) Detail("Owner", entry.owner)
                entry.linkTarget?.takeIf { it.isNotEmpty() }?.let { Detail("Link to", it) }
                if (!entry.isDir) {
                    HorizontalDivider()
                    Text("Checksums", style = MaterialTheme.typography.labelLarge)
                    listOf("MD5" to "MD5", "SHA-1" to "SHA-1", "SHA-256" to "SHA-256").forEach { (label, algorithm) ->
                        val value = hashes[label]
                        if (value == null) {
                            TextButton(onClick = { scope.launch { hashes = hashes + (label to vm.checksum(entry, algorithm).ifEmpty { "unavailable" }) } }) { Text("Calculate $label") }
                        } else Detail(label, value)
                    }
                }
                if (vm.rootMode) {
                    HorizontalDivider()
                    Text("Change permissions (octal, for example 644)", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(mode, { mode = it.filter { c -> c in '0'..'7' }.take(4) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = mode.length >= 3, onClick = { vm.chmod(entry, mode); onDismiss() }) { Text("Apply") }
                    }
                    if (FileActions.isSystemPath(entry.path, true)) {
                        Text("This is a system location. A wrong permission can stop the phone from starting.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (!entry.isDir) Row {
                TextButton(onClick = { FileActions.openWith(context, entry, vm.rootMode) }) { Text("Open with…") }
                TextButton(onClick = { FileActions.share(context, listOf(entry), vm.rootMode) }) { Text("Share") }
            }
        },
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int, single: FsEntry?, onClose: () -> Unit, onSelectAll: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit,
    onDelete: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onCompress: () -> Unit, onProperties: () -> Unit, onBookmark: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(Icons.close_24px), contentDescription = "Close") } },
        actions = {
            IconButton(onClick = onCopy) { Icon(painterResource(Icons.content_copy_24px), contentDescription = "Copy") }
            IconButton(onClick = onCut) { Icon(painterResource(Icons.content_cut_24px), contentDescription = "Cut") }
            IconButton(onClick = onDelete) { Icon(painterResource(Icons.delete_24px), contentDescription = "Delete") }
            Box {
                IconButton(onClick = { more = true }) { Icon(painterResource(Icons.more_vert_24px), contentDescription = "More") }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("Select all") }, onClick = { more = false; onSelectAll() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { more = false; onShare() })
                    DropdownMenuItem(text = { Text("Compress to zip") }, onClick = { more = false; onCompress() })
                    if (single != null) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { more = false; onRename() })
                        DropdownMenuItem(text = { Text("Properties") }, onClick = { more = false; onProperties() })
                        if (single.isDir) DropdownMenuItem(text = { Text("Add to favorites") }, onClick = { more = false; onBookmark() })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = {
            androidx.compose.material3.TextField(
                value = query, onValueChange = onQuery, singleLine = true, placeholder = { Text("Search in this folder") },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(Icons.arrow_back_24px), contentDescription = "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun Breadcrumbs(vm: FilesViewModel, onGo: (String?) -> Unit) {
    val p = vm.path ?: return
    val inArchive = ArchivePath.isArchive(p)
    val inVault = de.mm20.launcher2.ui.files.vault.VaultPath.isVault(p)
    val remote = RemotePath.isRemote(p)
    val volume = if (!vm.rootMode && !remote && !inArchive && !inVault) vm.volumes.firstOrNull { p == it.path || p.startsWith(it.path + "/") } else null
    val base = if (remote) "rem://" + RemotePath.idOf(p) else if (inArchive) ArchivePath.build(ArchivePath.archiveOf(p), "/").trimEnd('/') else if (inVault) de.mm20.launcher2.ui.files.vault.VaultPath.build(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(p), "/").trimEnd('/') else volume?.path ?: "/"
    val crumbs = buildList<Pair<String, String?>> {
        add("Home" to null)
        add((if (remote) vm.connectionName(p) ?: "Storage" else if (inArchive) nameOf(ArchivePath.archiveOf(p)) else if (inVault) nameOf(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(p)) else volume?.name?.substringBefore(" (") ?: "/") to base)
        var acc = base
        val rest = if (remote) RemotePath.innerOf(p) else if (inArchive) ArchivePath.innerOf(p) else if (inVault) de.mm20.launcher2.ui.files.vault.VaultPath.innerOf(p) else p.removePrefix(base)
        rest.trim('/').split('/').filter { it.isNotEmpty() }.forEach { seg -> acc = joinPath(acc, seg); add(seg to acc) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(p) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { i, (label, target) ->
            val last = i == crumbs.lastIndex
            Surface(
                shape = RoundedCornerShape(50),
                color = if (last) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !last) { onGo(target) },
            ) {
                Text(
                    label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!last) Icon(painterResource(Icons.arrow_right_24px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TaskBar(task: TaskState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title + "…", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { task.cancel.cancelled = true }) { Text("Cancel") }
            }
            if (task.progress != null) LinearProgressIndicator(progress = { task.progress }, Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PasteBar(count: Int, cut: Boolean, onPaste: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count item${if (count == 1) "" else "s"} to ${if (cut) "move" else "copy"}", Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onPaste) { Text(if (cut) "Move here" else "Paste here") }
        }
    }
}

@Composable
private fun RootBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Text(
                "System files. Changes here can break the phone.", Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun AccessGate(onAllow: () -> Unit) {
    val backStack = LocalBackStack.current
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(FileKind.Folder.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(Icons.folder_24px), contentDescription = null, tint = FileKind.Folder.color, modifier = Modifier.size(44.dp))
            }
            Text("Telos Files", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
            Text(
                "To show and manage the files on your phone, Telos needs access to all files.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = onAllow) { Text("Allow access") }
            TextButton(onClick = { backStack.removeLastOrNull() }) { Text("Not now") }
        }
    }
}

@Composable
private fun EmptyPage(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(Icons.folder_24px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
        Text(text, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorPage(error: String, rootMode: Boolean, onRoot: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(Icons.lock_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(error, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!rootMode) TextButton(onClick = onRoot) { Text("Open with root explorer…") }
    }
}

@Composable
private fun HomePage(vm: FilesViewModel) {
    val shortcuts = remember {
        listOf(
            "Downloads" to Environment.DIRECTORY_DOWNLOADS, "Camera" to Environment.DIRECTORY_DCIM, "Pictures" to Environment.DIRECTORY_PICTURES,
            "Music" to Environment.DIRECTORY_MUSIC, "Movies" to Environment.DIRECTORY_MOVIES, "Documents" to Environment.DIRECTORY_DOCUMENTS,
        ).map { (label, dir) -> label to Environment.getExternalStoragePublicDirectory(dir).path }
    }
    val colors = listOf(FileKind.Archive.color, FileKind.Image.color, FileKind.Video.color, FileKind.Audio.color, FileKind.Document.color, FileKind.Apk.color)
    val icons = listOf(Icons.download_24px, Icons.photo_24px, Icons.photo_24px, Icons.music_note_24px, Icons.videocam_24px, Icons.description_24px)
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Storage") }
        items(vm.volumes, key = { it.path }) { v -> StorageCard(v) { vm.open(v.path) } }
        item { SectionTitle("Quick access") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.chunked(3).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        chunk.forEachIndexed { col, (label, path) ->
                            val i = row * 3 + col
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(colors[i].copy(alpha = 0.14f))
                                    .clickable { if (File(path).exists() || File(path).mkdirs()) vm.open(path) }.padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(painterResource(icons[i]), contentDescription = null, tint = colors[i], modifier = Modifier.size(30.dp))
                                Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        if (vm.bookmarks.isNotEmpty()) {
            item { SectionTitle("Favorites") }
            items(vm.bookmarks, key = { "b$it" }) { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.open(b) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeTile(FileKind.Folder, null, false)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(nameOf(b), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.toggleBookmark(b) }) { Icon(painterResource(Icons.close_20px), contentDescription = "Remove") }
                }
            }
        }
        if (vm.connections.isNotEmpty()) {
            item { SectionTitle("Network and cloud") }
            items(vm.connections, key = { "c" + it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.open(RemotePath.build(c.id, "/")) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionTitle("Tools") }
        item {
            Card(
                onClick = { vm.open("/") }, enabled = vm.rootMode,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (vm.rootMode) 1f else 0.4f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Root file system", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            if (vm.rootMode) "Superuser access is on. Open / to browse everything." else "Turn root explorer on in the menu or the storage drawer.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StorageCard(v: StorageVolume, onClick: () -> Unit) {
    val used = (v.total - v.free).coerceAtLeast(0)
    val fraction = if (v.total > 0) used.toFloat() / v.total else 0f
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Icons.storage_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                    Text("${formatSize(v.free)} free of ${formatSize(v.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().padding(top = 14.dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun StorageDrawer(vm: FilesViewModel, onGo: (String?) -> Unit, onRoot: () -> Unit, onManage: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Telos Files", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        DrawerItem(Icons.home_24px, "Home", null, vm.path == null) { onGo(null) }
        Text("Storage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        vm.volumes.forEach { v ->
            DrawerItem(Icons.storage_24px, v.name, "${formatSize(v.free)} free", vm.path?.startsWith(v.path) == true && !vm.rootMode) { onGo(v.path) }
        }
        if (vm.bookmarks.isNotEmpty()) {
            Text("Favorites", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            vm.bookmarks.forEach { b -> DrawerItem(Icons.star_24px_filled, nameOf(b), null, vm.path == b) { onGo(b) } }
        }
        Text("Network and cloud", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        vm.connections.forEach { c ->
            DrawerItem(if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px, c.name, c.type.label, vm.path?.startsWith("rem://" + c.id) == true) { onGo(RemotePath.build(c.id, "/")) }
        }
        DrawerItem(Icons.add_24px, "Add or manage…", null, false, onManage)
        Text("Tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        DrawerItem(Icons.terminal_24px, if (vm.rootMode) "Root explorer: on" else "Root explorer", if (vm.rootMode) "Tap to turn off" else "Needs a rooted phone", vm.rootMode, onRoot)
        if (vm.rootMode) {
            DrawerItem(Icons.folder_24px, "Root file system (/)", "System files", vm.path == "/") { onGo("/") }
            DrawerItem(Icons.lock_24px, "Make /system writable", "Careful", false) { vm.remount("/system", true) }
            DrawerItem(Icons.lock_open_20px, "Make /system read-only", null, false) { vm.remount("/system", false) }
        }
    }
}

@Composable
private fun DrawerItem(icon: Int, label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- file items

/** The rounded square with the type icon, or a thumbnail for pictures, with a check mark when selected. */
@Composable
private fun TypeTile(kind: FileKind, thumbnail: String?, selected: Boolean, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.4f))
            .background(if (selected) MaterialTheme.colorScheme.primary else kind.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(painterResource(Icons.check_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            thumbnail != null -> AsyncImage(model = File(thumbnail), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(painterResource(kind.icon), contentDescription = null, tint = kind.color, modifier = Modifier.size(size / 2))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(e: FsEntry, selected: Boolean, rootMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeTile(e.kind, if (e.kind == FileKind.Image && e.extension != "svg" && !RemotePath.isRemote(e.path) && !ArchivePath.isArchive(e.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(e.path)) e.path else null, selected)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val detail = listOfNotNull(
                if (!e.isDir && e.size >= 0) formatSize(e.size) else null,
                formatDate(e.modified).ifEmpty { null },
                if (rootMode && e.permissions.isNotEmpty()) e.permissions else null,
            ).joinToString("  ·  ")
            if (detail.isNotEmpty()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.linkTarget != null) Text("→ ${e.linkTarget}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GridCell(e: FsEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            TypeTile(e.kind, if (e.kind == FileKind.Image && e.extension != "svg" && !RemotePath.isRemote(e.path) && !ArchivePath.isArchive(e.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(e.path)) e.path else null, selected, 72.dp)
        }
        Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}


@Composable
private fun UnlockVaultDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Unlock vault") },
        text = {
            Column {
                Text("${entry.name} is a Cryptomator vault (read only, experimental). The password is only used to unlock it and is not stored.")
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                vm.unlockVault(entry.path, password, onError = { error = it; busy = false }) { busy = false; onDismiss() }
            }) { Text(if (busy) "Unlocking…" else "Unlock") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
