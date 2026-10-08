package de.mm20.launcher2.ui.downloads

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.AfterFinish
import de.mm20.launcher2.downloads.DownloadEvent
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.logic.BlockReason
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.search.GreekFold
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import de.mm20.launcher2.ui.media.MediaSearchBar
import org.koin.compose.koinInject

private enum class Filter { All, Active, Queued, Completed, Failed, Torrents }

private fun Filter.matches(t: DownloadTask) = when (this) {
    Filter.All -> true
    Filter.Active -> t.state.isActive
    Filter.Queued -> t.state == DownloadState.Queued || t.state == DownloadState.Paused
    Filter.Completed -> t.state == DownloadState.Completed
    Filter.Failed -> t.state == DownloadState.Failed
    Filter.Torrents -> false
}

@Composable
fun DownloadsScreen(initialUrls: List<String> = emptyList()) {
    val context = LocalContext.current
    val manager: DownloadManager = koinInject()
    val backStack = LocalBackStack.current
    val tasks by manager.tasks.collectAsState()
    val settings by manager.settings.values.collectAsState()
    val blocked by manager.blockReason.collectAsState()

    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(initialUrls.isNotEmpty()) }
    var addUrls by rememberSaveable { mutableStateOf(initialUrls.joinToString("\n")) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DownloadTask?>(null) }
    var menu by remember { mutableStateOf(false) }

    // links that arrive while the screen is open (the app is opened again by a share)
    LaunchedEffect(initialUrls) {
        if (initialUrls.isNotEmpty()) {
            addUrls = initialUrls.joinToString("\n")
            showAdd = true
        }
    }

    // after finish: open or share a finished file while this screen is shown
    LaunchedEffect(manager) {
        manager.events.collect { e ->
            if (e is DownloadEvent.Completed) {
                val ok = when (manager.settings.current.afterFinish) {
                    AfterFinish.Open -> openDownload(context, e.task)
                    AfterFinish.Share -> shareDownload(context, e.task)
                    AfterFinish.Nothing -> true
                }
                if (!ok) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val counts = remember(tasks) { Filter.entries.associateWith { f -> tasks.count { f.matches(it) } } }
    val visible by remember(tasks, filter, query) {
        derivedStateOf {
            tasks.filter { filter.matches(it) }
                .filter { query.isBlank() || GreekFold.contains(it.displayName, query) || it.url.contains(query, true) }
                .sortedWith(
                    compareByDescending<DownloadTask> { it.state.isActive }
                        .thenByDescending { it.priority }
                        .thenByDescending { it.createdAt }
                )
        }
    }

    MediaFrame(
        stringResource(R.string.dl_title),
        askNotifications = true,
        guardKey = "telos_downloads_app://downloads",
        actions = {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(painterResource(R.drawable.search_24px), stringResource(R.string.dl_search))
            }
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.dl_pause_all)) }, onClick = { menu = false; manager.pauseAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.dl_resume_all)) }, onClick = { menu = false; manager.resumeAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.dl_clear_completed)) }, onClick = { menu = false; manager.clearFinished(false) })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dl_settings)) },
                    onClick = { menu = false; backStack.add(DownloadsSettingsRoute) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(searching) {
                    MediaSearchBar(query, { query = it }, stringResource(R.string.dl_search))
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (f in Filter.entries) {
                        val label = when (f) {
                            Filter.All -> R.string.dl_tab_all
                            Filter.Active -> R.string.dl_tab_active
                            Filter.Queued -> R.string.dl_tab_queued
                            Filter.Completed -> R.string.dl_tab_completed
                            Filter.Failed -> R.string.dl_tab_failed
                            Filter.Torrents -> R.string.dl_tab_torrents
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(label) + if (f != Filter.Torrents && (counts[f] ?: 0) > 0) " ${counts[f]}" else "") },
                        )
                    }
                }
                if (blocked != null) BlockBanner(blocked!!)
                SpeedHeader(tasks)
                if (filter == Filter.Torrents) {
                    EmptyState(R.string.dl_torrents_title, R.string.dl_torrents_text)
                } else if (visible.isEmpty()) {
                    if (tasks.isEmpty()) EmptyState(R.string.dl_empty_title, R.string.dl_empty_text)
                    else EmptyState(R.string.dl_empty_filter_title, R.string.dl_empty_filter_text)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { t ->
                            SwipeableTaskCard(
                                task = t,
                                manager = manager,
                                onOpenDetails = { detailId = t.id },
                                onDelete = { deleting = t },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { addUrls = ""; showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(painterResource(R.drawable.add_24px), null) },
                text = { Text(stringResource(R.string.dl_add)) },
            )
        }
    }

    if (showAdd) {
        AddDownloadSheet(
            initialText = addUrls,
            manager = manager,
            onDismiss = { showAdd = false },
        )
    }
    detailId?.let { id ->
        val t = tasks.firstOrNull { it.id == id }
        if (t == null) detailId = null else TaskDetailSheet(t, manager, onDismiss = { detailId = null }, onDelete = { deleting = t })
    }
    deleting?.let { t ->
        DeleteDialog(t, onDismiss = { deleting = null }) { withFile ->
            if (detailId == t.id) detailId = null
            manager.remove(t.id, withFile)
            deleting = null
        }
    }
}

@Composable
private fun BlockBanner(reason: BlockReason) {
    val text = when (reason) {
        BlockReason.Offline -> R.string.dl_block_offline
        BlockReason.WifiOnly -> R.string.dl_block_wifi
        BlockReason.LowBattery -> R.string.dl_block_battery
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (reason == BlockReason.Offline) R.drawable.wifi_off_24px else R.drawable.schedule_24px), null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SpeedHeader(tasks: List<DownloadTask>) {
    val active = tasks.filter { it.state.isActive }
    if (active.isEmpty()) return
    val speed = active.sumOf { it.speedBps }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.speed_24px), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.dl_header_active, active.size, active.size) + " · " + Formatting.speed(speed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EmptyState(title: Int, text: Int) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.download_24px), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.size(20.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SwipeableTaskCard(task: DownloadTask, manager: DownloadManager, onOpenDetails: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> togglePause(manager, task)
                else -> {}
            }
            false // the card springs back, the action decides what changes
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background(if (toEnd) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val paused = task.state == DownloadState.Paused || task.state == DownloadState.Failed
                Icon(
                    painterResource(if (toEnd) (if (paused) R.drawable.play_arrow_24px else R.drawable.pause_24px) else R.drawable.delete_24px),
                    null,
                )
            }
        },
    ) {
        TaskCard(task, manager, onOpenDetails, onDelete)
    }
}

private fun togglePause(manager: DownloadManager, t: DownloadTask) {
    when (t.state) {
        DownloadState.Paused, DownloadState.Failed -> manager.resume(t.id)
        DownloadState.Completed -> {}
        else -> manager.pause(t.id)
    }
}

@Composable
private fun TaskCard(task: DownloadTask, manager: DownloadManager, onOpenDetails: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val failed = task.state == DownloadState.Failed
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenDetails),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (failed) R.drawable.error_24px else categoryIcon(task.category)), null,
                    tint = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(2.dp))
                Text(
                    if (failed && !task.error.isNullOrBlank()) task.error!! else stateLabel(task) + " · " + progressText(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.state != DownloadState.Completed && task.state != DownloadState.Failed) {
                    Spacer(Modifier.size(8.dp))
                    if (task.totalBytes > 0) {
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    } else if (task.state.isActive) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // ring with the main action in the middle
            when (task.state) {
                DownloadState.Completed -> IconButton(onClick = {
                    if (!openDownload(context, task)) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show()
                }) { Icon(painterResource(R.drawable.open_in_new_24px), stringResource(R.string.dl_open)) }
                DownloadState.Failed -> IconButton(onClick = { manager.resume(task.id) }) {
                    Icon(painterResource(R.drawable.restart_alt_24px), stringResource(R.string.dl_retry))
                }
                else -> Box(contentAlignment = Alignment.Center) {
                    if (task.totalBytes > 0) CircularProgressIndicator(progress = { task.progress }, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    else if (task.state.isActive) CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    IconButton(onClick = { togglePause(manager, task) }, modifier = Modifier.size(44.dp)) {
                        val paused = task.state == DownloadState.Paused
                        Icon(
                            painterResource(if (paused) R.drawable.play_arrow_24px else R.drawable.pause_24px),
                            stringResource(if (paused) R.string.dl_resume else R.string.dl_pause),
                            Modifier.size(20.dp),
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert_24px), null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (task.state == DownloadState.Completed) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.dl_open)) }, onClick = {
                            menu = false
                            if (!openDownload(context, task)) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.dl_share)) }, onClick = {
                            menu = false
                            if (!shareDownload(context, task)) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show()
                        })
                    }
                    if (task.state == DownloadState.Paused) DropdownMenuItem(text = { Text(stringResource(R.string.dl_resume)) }, onClick = { menu = false; manager.resume(task.id) })
                    if (task.state.isActive || task.state == DownloadState.Queued) DropdownMenuItem(text = { Text(stringResource(R.string.dl_pause)) }, onClick = { menu = false; manager.pause(task.id) })
                    if (failed) DropdownMenuItem(text = { Text(stringResource(R.string.dl_retry)) }, onClick = { menu = false; manager.resume(task.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.dl_copy_link)) }, onClick = { menu = false; copyLink(context, task.url) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.dl_details)) }, onClick = { menu = false; onOpenDetails() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.dl_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(task: DownloadTask, onDismiss: () -> Unit, onConfirm: (deleteFile: Boolean) -> Unit) {
    val done = task.state == DownloadState.Completed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dl_delete_title)) },
        text = { Text(stringResource(if (done) R.string.dl_delete_text_done else R.string.dl_delete_text_partial, task.displayName)) },
        confirmButton = {
            Row {
                if (done) TextButton(onClick = { onConfirm(true) }) { Text(stringResource(R.string.dl_delete_with_file)) }
                TextButton(onClick = { onConfirm(false) }) { Text(stringResource(if (done) R.string.dl_delete_keep_file else R.string.dl_delete)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dl_cancel)) } },
    )
}
