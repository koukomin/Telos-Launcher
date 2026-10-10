package de.mm20.launcher2.ui.media.music

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Activity
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import de.mm20.launcher2.comms.media.TagEditor
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.media.MusicTrack
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.ui.R
import kotlinx.serialization.Serializable

@Serializable
data object MusicRoute : NavKey

/** A group of tracks shown on its own page (an album or an artist) */
private data class TrackGroup(val title: String, val subtitle: String, val tracks: List<MusicTrack>)

@Composable
fun MusicScreen() {
    val viewModel: MusicViewModel = viewModel()
    val context = LocalContext.current

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadLibrary(context)
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.connect(context)
        if (hasPermission) viewModel.loadLibrary(context)
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by remember { mutableStateOf<TrackGroup?>(null) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
    BackHandler(enabled = !showNowPlaying && group != null) { group = null }

    val filtered = remember(tracks, query) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(tracks, query) { listOf(it.title, it.artist, it.album) }
    }
    val unknownAlbum = stringResource(R.string.au_music_unknown_album)
    val unknownArtist = stringResource(R.string.au_music_unknown_artist)
    val songsFormat = stringResource(R.string.au_music_songs_count)
    val albums = remember(filtered, unknownAlbum) {
        filtered.groupBy { it.albumId }.values
            .map { list -> TrackGroup(list.first().album.ifBlank { unknownAlbum }, list.first().artist, list.sortedBy { it.trackNumber }) }
            .sortedBy { it.title.lowercase() }
    }
    val artists = remember(filtered, unknownArtist, songsFormat) {
        filtered.groupBy { it.artist.ifBlank { unknownArtist } }
            .map { (name, list) -> TrackGroup(name, songsFormat.format(list.size), list.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.title.lowercase() }
    }

    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    de.mm20.launcher2.ui.media.MediaFrame(stringResource(R.string.au_music_title), askNotifications = true, guardKey = "telos_music_app://music", actions = {
        IconButton(onClick = { backStack.add(de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsRoute) }) {
            Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.hc_scrobbling))
        }
    }) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.hc_allow_access_to_your_music_to_build_the), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { permissionLauncher.launch(permission) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.hc_allow)) }
                }
            } else {
                de.mm20.launcher2.ui.media.MediaSearchBar(query, { query = it }, stringResource(R.string.tsm_search_music))
                val current = group
                if (current != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        IconButton(onClick = { group = null }) {
                            Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                        }
                        Column {
                            Text(current.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TrackList(current.tracks, nowPlaying?.mediaId, onPlay = { i -> viewModel.play(current.tracks, i) })
                } else {
                    TabRow(selectedTabIndex = tab) {
                        listOf(stringResource(R.string.au_music_tab_songs), stringResource(R.string.hc_albums), stringResource(R.string.au_music_tab_artists)).forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.hc_no_music_found_on_this_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        filtered.isEmpty() && query.isNotBlank() -> de.mm20.launcher2.ui.component.SearchEmptyState(query)
                        tab == 0 -> TrackList(filtered, nowPlaying?.mediaId, onPlay = { i -> viewModel.play(filtered, i) })
                        tab == 1 -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(albums) { album ->
                                Column(Modifier.clickable { group = album }) {
                                    Cover(album.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                                    Text(album.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text(album.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(artists) { _, artist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { group = artist }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Cover(artist.tracks.first(), Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)))
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(artist.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(artist.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        nowPlaying?.let { np ->
            val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clickable { showNowPlaying = true },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtOrNote(np.artUri?.toString(), Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(np.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(np.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = stringResource(if (isPlaying) R.string.au_music_pause else R.string.hc_play))
                    }
                    IconButton(onClick = { viewModel.next() }) {
                        Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying && nowPlaying != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(viewModel, onClose = { showNowPlaying = false })
        }
    }
    }
}

@Composable
private fun TrackList(list: List<MusicTrack>, currentId: String?, onPlay: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(index) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentId != null && track.id.toString() == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Cover(track: MusicTrack, modifier: Modifier) = ArtOrNote(track.albumArtUri.toString(), modifier)

@Composable
private fun ArtOrNote(url: String?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.music_note_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NowPlayingScreen(viewModel: MusicViewModel, onClose: () -> Unit) {
    val np by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatMode.collectAsStateWithLifecycle()
    val current = np ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TagEditor.Tags?>(null) }
    var pendingTags by remember { mutableStateOf<TagEditor.Tags?>(null) }
    var pendingCover by remember { mutableStateOf<android.net.Uri?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val uri = current.uri
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            pendingTags?.let { viewModel.saveTags(context, uri, it) }
            pendingCover?.let { viewModel.saveCover(context, uri, it) }
        }
        pendingTags = null
        pendingCover = null
    }
    fun requestWrite(onGranted: () -> Unit) {
        val uri = current.uri ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
            writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else onGranted()
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image ->
        val uri = current.uri
        if (image != null && uri != null) {
            pendingCover = image
            requestWrite { viewModel.saveCover(context, uri, image) }
        }
    }
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.sleepEndsAt.collectAsStateWithLifecycle()
    var showSleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = current.durationMs.coerceAtLeast(1L)

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text(stringResource(R.string.hc_sleep_timer)) },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton(onClick = {
                            viewModel.setSleepTimer(minutes)
                            showSleep = false
                        }) { Text(stringResource(R.string.hc_stop_in_minutes, minutes)) }
                    }
                    if (sleepEndsAt > 0) {
                        TextButton(onClick = {
                            viewModel.setSleepTimer(0)
                            showSleep = false
                        }) { Text(stringResource(R.string.hc_turn_timer_off)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text(stringResource(R.string.hc_close)) } },
        )
    }

    editing?.let { tags ->
        TagEditDialog(
            tags = tags,
            onSave = { newTags ->
                val uri = current.uri
                editing = null
                if (uri != null) {
                    pendingTags = newTags
                    requestWrite { viewModel.saveTags(context, uri, newTags) }
                }
            },
            onPickCover = { coverPicker.launch("image/*") },
            onDismiss = { editing = null },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.keyboard_arrow_down_24px), contentDescription = stringResource(R.string.hc_close))
                }
            }
            if (showLyrics) {
                LyricsView(lyrics, position, Modifier.fillMaxWidth().weight(1f))
            } else {
                ArtOrNote(current.artUri?.toString(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
            }
            Spacer(Modifier.height(24.dp))
            Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Slider(
                value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { viewModel.seekTo((it * duration).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(((dragging ?: (position.toFloat() / duration)) * duration).toLong()), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(current.durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        painterResource(R.drawable.shuffle_24px), contentDescription = stringResource(R.string.hc_shuffle),
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(painterResource(R.drawable.skip_previous_24px), contentDescription = stringResource(R.string.hc_previous))
                }
                FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = stringResource(if (isPlaying) R.string.au_music_pause else R.string.hc_play))
                }
                IconButton(onClick = { viewModel.next() }) {
                    Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                }
                IconButton(onClick = { viewModel.cycleRepeat() }) {
                    Icon(
                        painterResource(if (repeat == Player.REPEAT_MODE_ONE) R.drawable.repeat_one_24px else R.drawable.repeat_24px),
                        contentDescription = stringResource(R.string.hc_repeat),
                        tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showLyrics = !showLyrics }, enabled = lyrics != null || showLyrics) {
                    Text(stringResource(if (showLyrics) R.string.au_music_show_cover else R.string.au_music_show_lyrics))
                }
                TextButton(onClick = { showSleep = true }) {
                    Text(stringResource(if (sleepEndsAt > 0) R.string.au_music_sleep_active else R.string.au_music_sleep))
                }
                TextButton(onClick = {
                    val uri = current.uri
                    if (uri != null) scope.launch { editing = viewModel.readTags(context, uri) ?: TagEditor.Tags(title = current.title, artist = current.artist) }
                }) { Text(stringResource(R.string.hc_edit)) }
            }
        }
    }
}

@Composable
private fun TagEditDialog(
    tags: TagEditor.Tags,
    onSave: (TagEditor.Tags) -> Unit,
    onPickCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(tags.title) }
    var artist by remember { mutableStateOf(tags.artist) }
    var album by remember { mutableStateOf(tags.album) }
    var albumArtist by remember { mutableStateOf(tags.albumArtist) }
    var genre by remember { mutableStateOf(tags.genre) }
    var year by remember { mutableStateOf(tags.year) }
    var track by remember { mutableStateOf(tags.trackNumber) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hc_edit_tags)) },
        text = {
            LazyColumn {
                item {
                    @Composable
                    fun field(label: String, value: String, onChange: (String) -> Unit) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onChange,
                            label = { Text(label) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                    Column {
                        field(stringResource(R.string.au_music_field_title), title) { title = it }
                        field(stringResource(R.string.au_music_field_artist), artist) { artist = it }
                        field(stringResource(R.string.au_music_field_album), album) { album = it }
                        field(stringResource(R.string.au_music_field_album_artist), albumArtist) { albumArtist = it }
                        field(stringResource(R.string.au_music_field_genre), genre) { genre = it }
                        field(stringResource(R.string.au_music_field_year), year) { year = it }
                        field(stringResource(R.string.au_music_field_track), track) { track = it }
                        TextButton(onClick = onPickCover) { Text(stringResource(R.string.hc_change_cover)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(TagEditor.Tags(title, artist, album, albumArtist, genre, year, track))
            }) { Text(stringResource(R.string.hc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) } },
    )
}

@Composable
private fun LyricsView(lyrics: de.mm20.launcher2.comms.media.Lyrics?, positionMs: Long, modifier: Modifier) {
    if (lyrics == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.hc_no_lyrics_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (lyrics.synced.isEmpty()) {
        LazyColumn(modifier) {
            item { Text(lyrics.plain, style = MaterialTheme.typography.bodyLarge) }
        }
        return
    }
    val currentIndex = lyrics.synced.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(modifier, state = listState) {
        itemsIndexed(lyrics.synced) { index, line ->
            Text(
                text = line.text.ifBlank { "…" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (index == currentIndex) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
