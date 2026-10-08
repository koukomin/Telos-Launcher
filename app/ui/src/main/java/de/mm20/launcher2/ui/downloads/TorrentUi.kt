package de.mm20.launcher2.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.TorrentFile
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.downloads.logic.SeedRules
import de.mm20.launcher2.ui.R

internal val TASK_SHAPE = RoundedCornerShape(20.dp)

/** Priorities the user can choose in the UI; libtorrent has 0 to 7 */
internal enum class FilePriority(val value: Int, val label: Int) {
    Skip(0, R.string.dl_t_prio_skip),
    Low(1, R.string.dl_t_prio_low),
    Normal(4, R.string.dl_t_prio_normal),
    High(6, R.string.dl_t_prio_high),
    Max(7, R.string.dl_t_prio_max);

    companion object {
        fun of(value: Int): FilePriority = when {
            value <= 0 -> Skip
            value < 4 -> Low
            value < 6 -> Normal
            value < 7 -> High
            else -> Max
        }
    }
}

internal fun TorrentFile.percent(): Int = if (size > 0) ((done.coerceAtMost(size) * 100) / size).toInt() else 0

/** Files of a torrent with a checkbox and a priority menu each; [onChange] gets file index to new priority */
@Composable
internal fun TorrentFileList(
    files: List<TorrentFile>,
    onChange: (Map<Int, Int>) -> Unit,
    showProgress: Boolean,
    onOpen: ((TorrentFile) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(files, key = { it.index }) { f ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = onOpen != null && f.uri != null) { onOpen?.invoke(f) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = f.wanted, onCheckedChange = { on -> onChange(mapOf(f.index to if (on) FilePriority.Normal.value else 0)) })
                Column(Modifier.weight(1f)) {
                    Text(f.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (showProgress && f.wanted) "${Formatting.size(f.size)} · ${f.percent()} %" else Formatting.size(f.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    TextButton(onClick = { menu = true }, enabled = f.wanted || true) {
                        Text(stringResource(FilePriority.of(f.priority).label))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        for (p in FilePriority.entries) {
                            DropdownMenuItem(text = { Text(stringResource(p.label)) }, onClick = { menu = false; onChange(mapOf(f.index to p.value)) })
                        }
                    }
                }
            }
        }
    }
}

/** Label of the state of a torrent: seeding, queue and copying are told apart from the plain states */
@Composable
internal fun torrentStateLabel(t: DownloadTask): String {
    val d = t.torrent
    return when {
        d != null && d.moving -> stringResource(R.string.dl_t_state_moving)
        t.state == DownloadState.Seeding -> stringResource(R.string.dl_t_state_seeding)
        d != null && d.inQueue && t.state.isActive -> stringResource(R.string.dl_t_state_queue)
        t.state == DownloadState.Connecting && d != null && d.files.isEmpty() -> stringResource(R.string.dl_t_state_metadata)
        else -> stateLabel(t)
    }
}

@Composable
internal fun TorrentSpeedHeader(tasks: List<DownloadTask>) {
    val torrents = tasks.filter { it.type == DownloadType.Torrent }
    val active = torrents.filter { it.state.isActive }
    if (active.isEmpty()) return
    val down = active.sumOf { it.speedBps }
    val up = active.sumOf { it.torrent?.uploadBps ?: 0L }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.swap_vert_24px), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.dl_t_header_torrents, active.size, active.size) + " · " +
                stringResource(R.string.dl_t_header_speed, Formatting.speed(down), Formatting.speed(up)),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun TorrentCard(task: DownloadTask, manager: DownloadManager, onOpenDetails: () -> Unit, onDelete: () -> Unit) {
    val d = task.torrent
    val failed = task.state == DownloadState.Failed
    Surface(
        shape = TASK_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(TASK_SHAPE).clickable(onClick = onOpenDetails),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (failed) R.drawable.error_24px else R.drawable.swap_vert_24px), null,
                        tint = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (failed && !task.error.isNullOrBlank()) task.error!! else torrentStateLabel(task) + torrentProgressSuffix(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                when (task.state) {
                    DownloadState.Failed -> IconButton(onClick = { manager.resume(task.id) }) {
                        Icon(painterResource(R.drawable.restart_alt_24px), stringResource(R.string.dl_retry))
                    }
                    DownloadState.Completed -> IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.dl_delete)) }
                    else -> {
                        val paused = task.state == DownloadState.Paused
                        IconButton(onClick = { if (paused) manager.resume(task.id) else manager.pause(task.id) }) {
                            Icon(
                                painterResource(if (paused) R.drawable.play_arrow_24px else R.drawable.pause_24px),
                                stringResource(if (paused) R.string.dl_resume else R.string.dl_pause),
                            )
                        }
                    }
                }
            }
            if (task.state != DownloadState.Completed && task.state != DownloadState.Failed && task.state != DownloadState.Seeding) {
                Spacer(Modifier.height(8.dp))
                if (task.totalBytes > 0) {
                    LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                } else if (task.state.isActive) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                }
            }
            if (d != null && !failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("↓ ").append(Formatting.speed(if (task.state == DownloadState.Seeding) 0 else task.speedBps))
                        append("  ↑ ").append(Formatting.speed(d.uploadBps))
                        append(" · ").append(stringResource(R.string.dl_t_ratio, SeedRules.formatRatio(d.ratio)))
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.state.isActive) {
                    Text(
                        stringResource(R.string.dl_t_peers, d.seeds, (d.peers - d.seeds).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** " · 12.3 MB / 40 MB · 2:10" for the line below the name */
internal fun torrentProgressSuffix(t: DownloadTask): String = when {
    t.state == DownloadState.Seeding -> " · " + Formatting.size(t.totalBytes)
    t.state == DownloadState.Completed -> " · " + Formatting.size(t.totalBytes)
    t.totalBytes > 0 -> " · " + (t.progress * 100).toInt() + " % · " + Formatting.size(t.downloadedBytes) + " / " + Formatting.size(t.totalBytes) +
        (t.etaSeconds?.let { " · " + Formatting.duration(it) }.orEmpty())
    else -> ""
}
