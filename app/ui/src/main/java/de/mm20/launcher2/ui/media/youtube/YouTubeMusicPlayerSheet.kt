package de.mm20.launcher2.ui.media.youtube
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.youtube.YouTubeEntry
import de.mm20.launcher2.comms.youtube.YouTubePlayerService
import de.mm20.launcher2.comms.youtube.YouTubeQueue
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.media.video.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YouTubeMusicPlayerSheet(vm: YouTubeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val item = now
    if (item == null || item.mediaMetadata.extras?.getBoolean("audioOnly") != true) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val serialized = item.mediaMetadata.extras?.getString(YouTubePlayerService.EXTRA_QUEUE)
    val tracks by produceState(emptyList<YouTubeEntry>(), serialized) {
        value = withContext(Dispatchers.Default) {
            if ((serialized?.length ?: 0) <= 512_000) YouTubeQueue.decode(serialized) else emptyList()
        }
    }
    val index = item.mediaMetadata.extras?.getInt(YouTubePlayerService.EXTRA_QUEUE_INDEX, -1) ?: -1
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekable by remember { mutableStateOf(false) }
    LaunchedEffect(controller, item.mediaId) {
        while (isActive) {
            runCatching { controller?.let {
                position = it.currentPosition.coerceAtLeast(0)
                duration = it.duration.coerceAtLeast(0)
                seekable = it.isCurrentMediaItemSeekable
            } }
            delay(500)
        }
    }
    val entry = remember(item, duration) { YouTubeEntry(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(),
        item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString(), duration / 1000) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        YouTubeMusicPlayerContent(entry, tracks, index, position, duration, playing, seekable,
            onToggle = vm::toggle, onStop = { vm.stop(); onDismiss() },
            onSeek = { controller?.seekTo(it) },
            onTrack = { at -> tracks.getOrNull(at)?.let { vm.play(context, it, true, tracks) } },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f))
    }
}

/** Data-driven presentation; the existing controller and queue remain the playback authority. */
@Composable
internal fun YouTubeMusicPlayerContent(
    entry: YouTubeEntry, tracks: List<YouTubeEntry>, index: Int, position: Long, duration: Long,
    playing: Boolean, seekable: Boolean,
    onToggle: () -> Unit, onStop: () -> Unit, onSeek: (Long) -> Unit, onTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember(entry.url) { mutableStateOf<Float?>(null) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    val artwork: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            AsyncImage(entry.thumbnail, null, contentScale = ContentScale.Crop,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer))
        }
    }
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.title, style = MaterialTheme.typography.headlineSmall)
            Text(entry.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = dragging ?: (position.coerceAtMost(duration).coerceAtLeast(0) / 1000f),
                onValueChange = { dragging = it }, enabled = seekable && duration > 0,
                valueRange = 0f..maxOf(1f, duration / 1000f),
                onValueChangeFinished = { dragging?.let { onSeek((it * 1000).toLong().coerceIn(0, duration)) }; dragging = null })
            val time = remember(position / 1000, duration / 1000) { "${formatDuration(position)} / ${formatDuration(duration)}" }
            Text(time, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onTrack(index - 1) }, enabled = index > 0 && index <= tracks.lastIndex) {
                    Icon(painterResource(BaseR.drawable.skip_previous_24px), stringResource(I18nR.string.hc_previous))
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(if (playing) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                        stringResource(if (playing) I18nR.string.au_music_pause else I18nR.string.hc_play))
                }
                IconButton(onClick = { onTrack(index + 1) }, enabled = index >= 0 && index < tracks.lastIndex) {
                    Icon(painterResource(BaseR.drawable.skip_next_24px), stringResource(I18nR.string.hc_next))
                }
                IconButton(onClick = onStop) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.hc_stop)) }
            }
            if (tracks.isNotEmpty()) Text(stringResource(I18nR.string.au10_music_up_next), style = MaterialTheme.typography.titleMedium)
        }
    }
    BoxWithConstraints(modifier) {
        val sideBySide = maxWidth > maxHeight
        val artworkFraction = if (LocalDensity.current.fontScale > 1.3f) 0.30f else 0.42f
        Row(Modifier.fillMaxSize()) {
            if (sideBySide) Box(Modifier.weight(artworkFraction).fillMaxHeight(), contentAlignment = Alignment.Center) { artwork() }
            LazyColumn(Modifier.weight(1f - artworkFraction).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (!sideBySide) item { artwork() }
                item { controls() }
                itemsIndexed(tracks, key = { at, track -> "$at:${track.url}" }) { at, track ->
                    ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) },
                        leadingContent = { if (at == index) Icon(painterResource(BaseR.drawable.play_arrow_24px), null) else Text((at + 1).toString()) },
                        modifier = Modifier.selectable(selected = at == index, role = Role.Button, onClick = { onTrack(at) }),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
    }
}
}
