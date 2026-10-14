package de.mm20.launcher2.ui.files

import android.app.Application
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import java.io.File

internal data class FilesTab(val id: Long, val browser: FilesViewModel, val store: ViewModelStore)

internal class FilesWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val clipboard = FilesClipboard()
    var tabs by mutableStateOf<List<FilesTab>>(emptyList())
        private set
    var left by mutableStateOf(0L)
        private set
    var right by mutableStateOf(0L)
        private set
    var dual by mutableStateOf(false)
        private set
    var activeRight by mutableStateOf(false)
    private var nextId = 0L
    init { addTab() }
    fun addTab() {
        val store = ViewModelStore()
        val browser = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))[FilesViewModel::class.java]
        browser.sharedClipboard = clipboard
        browser.onFilesChanged = { tabs.filter { it.browser !== browser }.forEach { it.browser.reload() } }
        browser.archiveInUseElsewhere = { archive -> tabs.any { tab ->
            tab.browser !== browser && tab.browser.path?.let { ArchivePath.isArchive(it) && ArchivePath.archiveOf(it) == archive } == true
        } }
        val current = tabs.firstOrNull { it.id == if (activeRight) right else left }?.browser?.path
        val tab = FilesTab(++nextId, browser, store)
        tabs = tabs + tab
        if (dual && activeRight) right = tab.id else left = tab.id
        browser.open(current)
    }
    fun select(id: Long) {
        when {
            id == left -> activeRight = false
            dual && id == right -> activeRight = true
            dual && activeRight -> right = id
            else -> left = id
        }
    }
    fun close(id: Long) {
        if (tabs.size <= 1) return
        val tab = tabs.first { it.id == id }
        tabs = tabs.filterNot { it.id == id }
        if (left == id) left = tabs.firstOrNull { it.id != right }?.id ?: tabs.first().id
        if (right == id) right = tabs.firstOrNull { it.id != left }?.id ?: left
        if (tabs.size == 1) { dual = false; activeRight = false }
        tab.browser.open(null)
        tab.store.clear()
    }
    fun togglePanels() {
        if (dual) { dual = false; activeRight = false; return }
        if (tabs.size == 1) { addTab() }
        right = tabs.first { it.id != left }.id
        dual = true
    }
    override fun onCleared() {
        tabs.forEach { it.store.clear() }
        val app: Application = getApplication()
        File(app.cacheDir, "remote_open").deleteRecursively()
        File(app.cacheDir, "share_dl").deleteRecursively()
        ArchiveSessions.clearAll()
        clipboard.value = null
        super.onCleared()
    }
}

@Composable
fun FilesScreen() {
    val workspace: FilesWorkspaceViewModel = viewModel()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            workspace.tabs.forEachIndexed { index, tab ->
                val selected = tab.id == if (workspace.activeRight && workspace.dual) workspace.right else workspace.left
                FilterChip(selected = selected, onClick = { workspace.select(tab.id) }, label = {
                    Text(tab.browser.path?.let { nameOf(it) } ?: stringResource(R.string.telos_files_tab, index + 1), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                })
                if (workspace.tabs.size > 1) IconButton(onClick = { workspace.close(tab.id) }) {
                    Icon(androidx.compose.ui.res.painterResource(de.mm20.launcher2.base.R.drawable.close_24px), contentDescription = stringResource(R.string.telos_files_close_tab, index + 1))
                }
            }
            TextButton(onClick = workspace::addTab) { Text(stringResource(R.string.telos_files_new_tab)) }
            FilterChip(selected = workspace.dual, onClick = workspace::togglePanels, label = { Text(stringResource(R.string.telos_files_two_panels)) })
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val first = workspace.tabs.first { it.id == workspace.left }
            val second = workspace.tabs.firstOrNull { it.id == workspace.right }
            if (!workspace.dual || second == null) FilesBrowser(first.browser)
            else if (maxWidth >= 640.dp) Row(Modifier.fillMaxSize()) {
                FilesPane(first, !workspace.activeRight, { workspace.activeRight = false }, Modifier.weight(1f).fillMaxHeight())
                FilesPane(second, workspace.activeRight, { workspace.activeRight = true }, Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                FilesPane(first, !workspace.activeRight, { workspace.activeRight = false }, Modifier.weight(1f).fillMaxWidth())
                FilesPane(second, workspace.activeRight, { workspace.activeRight = true }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FilesPane(tab: FilesTab, active: Boolean, onActivate: () -> Unit, modifier: Modifier) {
    Box(modifier.border(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        .pointerInput(onActivate) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed }) onActivate()
                }
            }
        }) { FilesBrowser(tab.browser, handlesBack = active) }
}
