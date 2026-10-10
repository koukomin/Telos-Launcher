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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.common.share.ShareMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
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
internal data class TrackGroup(val title: String, val subtitle: String, val tracks: List<MusicTrack>)

@Composable
fun MusicScreen(
    /** Telos Media: open the full player (asked by its mini player) */
    openNowPlaying: Boolean = false,
    onOpenNowPlayingConsumed: () -> Unit = {},
) {
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

    val history by viewModel.history.collectAsStateWithLifecycle()
    val performanceSettings = koinInject<de.mm20.launcher2.preferences.ui.PerformanceSettings>()
    val reduceAnimations by performanceSettings.reduceAnimations.collectAsState(false)
    val scope = rememberCoroutineScope()
    val shareFailed = stringResource(R.string.au9_share_failed)
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    fun shareTrack(t: MusicTrack) = shareAudio(context, scope, t.uri, t.title, shareFailed)
    fun shareGroup(g: TrackGroup) {
        val text = (listOf(listOf(g.title, g.subtitle).filter { it.isNotBlank() }.joinToString(" - ")) +
            g.tracks.mapIndexed { i, t -> "${i + 1}. ${t.title}" }).joinToString("\n")
        if (!ShareActions.shareText(context, text, g.title)) Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by remember { mutableStateOf<TrackGroup?>(null) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openNowPlaying) {
        if (openNowPlaying) {
            showNowPlaying = true
            onOpenNowPlayingConsumed()
        }
    }
    // inside Telos Media its shared mini player is shown instead
    val inHub = de.mm20.launcher2.ui.media.LocalInMediaHub.current

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
    BackHandler(enabled = !showNowPlaying && group != null) { group = null }

    val filtered = remember(tracks, query) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(tracks, query) { listOf(it.title, it.artist, it.album) }
    }
    val homeTracks = remember(filtered, genre) { if (genre == null) filtered else filtered.filter { it.genre.trim() == genre } }
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
                        Column(Modifier.weight(1f)) {
                            Text(current.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { shareGroup(current) }) {
                            Icon(painterResource(R.drawable.share_24px), contentDescription = stringResource(R.string.menu_share))
                        }
                    }
                    TrackList(current.tracks, nowPlaying?.mediaId, onPlay = { i -> viewModel.play(current.tracks, i) }, onShare = { shareTrack(it) })
                } else {
                    TabRow(selectedTabIndex = tab) {
                        listOf(stringResource(R.string.au10_music_tab_home), stringResource(R.string.au_music_tab_songs), stringResource(R.string.hc_albums), stringResource(R.string.au_music_tab_artists)).forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.hc_no_music_found_on_this_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        filtered.isEmpty() && query.isNotBlank() -> de.mm20.launcher2.ui.component.SearchEmptyState(query)
                        tab == 0 -> MusicHome(
                            allTracks = tracks,
                            tracks = homeTracks,
                            history = history,
                            genre = genre,
                            onGenre = { genre = it },
                            onOpenGroup = { group = it },
                            onShareGroup = { shareGroup(it) },
                            onPlay = { l, i -> viewModel.play(l, i) },
                            unknownAlbum = unknownAlbum,
                            unknownArtist = unknownArtist,
                            songsFormat = songsFormat,
                        )
                        tab == 1 -> TrackList(filtered, nowPlaying?.mediaId, onPlay = { i -> viewModel.play(filtered, i) }, onShare = { shareTrack(it) })
                        tab == 2 -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(albums) { album ->
                                Column(Modifier.combinedClickable(onLongClick = { shareGroup(album) }, onClick = { group = album })) {
                                    Cover(album.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                                    Text(album.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text(album.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(artists) { _, artist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = { shareGroup(artist) }, onClick = { group = artist }).padding(horizontal = 16.dp, vertical = 10.dp),
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

        nowPlaying?.takeIf { !inHub }?.let { np ->
            val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
            ArtTintedMiniPlayer(
                title = np.title,
                artist = np.artist,
                artUrl = tracks.firstOrNull { it.id.toString() == np.mediaId }?.albumArtUri?.toString() ?: np.artUri?.toString(),
                isPlaying = isPlaying,
                onToggle = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onClick = { showNowPlaying = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                reduceAnimations = reduceAnimations,
            )
        }

        AnimatedVisibility(
            visible = showNowPlaying && nowPlaying != null,
            enter = if (reduceAnimations) androidx.compose.animation.EnterTransition.None else slideInVertically { it },
            exit = if (reduceAnimations) androidx.compose.animation.ExitTransition.None else slideOutVertically { it },
        ) {
            NowPlayingScreen(viewModel, tracks, reduceAnimations, onClose = { showNowPlaying = false })
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(list: List<MusicTrack>, currentId: String?, onPlay: (Int) -> Unit, onShare: (MusicTrack) -> Unit) {
    var menuFor by remember { mutableStateOf<Long?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(onLongClick = { menuFor = track.id }, onClick = { onPlay(index) })
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
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
                Box {
                    IconButton(onClick = { menuFor = track.id }) {
                        Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.au10_music_more))
                    }
                    DropdownMenu(expanded = menuFor == track.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hc_play)) },
                            onClick = { menuFor = null; onPlay(index) },
                        )
                        ShareMenuItem(onClick = { menuFor = null; onShare(track) })
                    }
                }
            }
        }
    }
}

/** Shares the audio file behind [uri] (a copy in cache/share) */
internal fun shareAudio(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, uri: android.net.Uri?, title: String, failedText: String) {
    if (uri == null) return
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri) ?: "audio/*"
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "audio"
            ShareActions.shareFile(context, uri, mime, "${title.ifBlank { "audio" }}.$ext")
        }
        if (!ok) Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Cover(track: MusicTrack, modifier: Modifier) = ArtOrNote(track.albumArtUri.toString(), modifier)

@Composable
internal fun ArtOrNote(url: String?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.music_note_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingScreen(viewModel: MusicViewModel, library: List<MusicTrack>, reduceAnimations: Boolean, onClose: () -> Unit) {
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

    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val shareFailed = stringResource(R.string.au9_share_failed)
    var sheet by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf(0) }
    val libTrack = remember(library, current.mediaId) { library.firstOrNull { it.id.toString() == current.mediaId } }
    val artUrl = libTrack?.albumArtUri?.toString() ?: current.artUri?.toString()
    val tint by rememberArtworkTint(artUrl, MaterialTheme.colorScheme.primaryContainer, reduceAnimations)
    val top = tint.darkened(0.30f)
    val bottom = tint.darkened(0.85f)
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.70f)
    val related = remember(library, current.mediaId) {
        val t = libTrack
        if (t == null) emptyList() else {
            val artistMatch = library.filter { it.id != t.id && t.artist.isNotBlank() && it.artist == t.artist }
            val genreMatch = library.filter { it.id != t.id && t.genre.isNotBlank() && it.genre == t.genre && it !in artistMatch }
            (artistMatch + genreMatch).take(40)
        }
    }

    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            containerColor = bottom.copy(alpha = 0.96f),
            contentColor = white,
        ) {
            TabRow(selectedTabIndex = sheetTab, containerColor = Color.Transparent, contentColor = white) {
                listOf(R.string.au10_music_up_next, R.string.au10_music_lyrics, R.string.au10_music_related).forEachIndexed { i, res ->
                    Tab(selected = sheetTab == i, onClick = { sheetTab = i }, text = { Text(stringResource(res)) })
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 460.dp).padding(horizontal = 16.dp)) {
                when (sheetTab) {
                    0 -> if (upNext.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.au10_music_queue_empty), color = soft) }
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        items(upNext.size) { i ->
                            val q = upNext[i]
                            Column(Modifier.fillMaxWidth().clickable { viewModel.playQueueIndex(q.index) }.padding(vertical = 8.dp)) {
                                Text(q.title, style = MaterialTheme.typography.bodyLarge, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(q.artist, style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    1 -> LyricsView(lyrics, position, Modifier.fillMaxSize(), activeColor = white, inactiveColor = soft)
                    else -> if (related.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.au10_music_queue_empty), color = soft) }
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        items(related.size) { i ->
                            val r = related[i]
                            Row(Modifier.fillMaxWidth().clickable { viewModel.play(related, i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(r, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(r.title, style = MaterialTheme.typography.bodyLarge, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.artist, style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))
            .background(Color.Black.copy(alpha = 0.25f)),
    ) {
        CompositionLocalProvider(LocalContentColor provides white) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.keyboard_arrow_down_24px), contentDescription = stringResource(R.string.hc_close))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { shareAudio(context, scope, current.uri, current.title, shareFailed) }) {
                        Icon(painterResource(R.drawable.share_24px), contentDescription = stringResource(R.string.menu_share))
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showLyrics) {
                        LyricsView(lyrics, position, Modifier.fillMaxSize(), activeColor = white, inactiveColor = soft)
                    } else {
                        val side = minOf(maxWidth, maxHeight)
                        Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 16.dp, color = Color.Transparent, modifier = Modifier.size(side)) {
                            ArtOrNote(artUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Text(current.artist, style = MaterialTheme.typography.bodyLarge, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.10f), contentColor = white, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Slider(
                            value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { dragging = it },
                            onValueChangeFinished = {
                                dragging?.let { viewModel.seekTo((it * duration).toLong()) }
                                dragging = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = white,
                                activeTrackColor = white,
                                inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(((dragging ?: (position.toFloat() / duration)) * duration).toLong()), style = MaterialTheme.typography.labelSmall, color = soft)
                            Text(formatDuration(current.durationMs), style = MaterialTheme.typography.labelSmall, color = soft)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(painterResource(R.drawable.shuffle_24px), contentDescription = stringResource(R.string.hc_shuffle), tint = if (shuffle) white else soft)
                            }
                            IconButton(onClick = { viewModel.previous() }) {
                                Icon(painterResource(R.drawable.skip_previous_24px), contentDescription = stringResource(R.string.hc_previous))
                            }
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = white, contentColor = bottom),
                            ) {
                                Icon(painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = stringResource(if (isPlaying) R.string.au_music_pause else R.string.hc_play), modifier = Modifier.size(36.dp))
                            }
                            IconButton(onClick = { viewModel.next() }) {
                                Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                            }
                            IconButton(onClick = { viewModel.cycleRepeat() }) {
                                Icon(
                                    painterResource(if (repeat == Player.REPEAT_MODE_ONE) R.drawable.repeat_one_24px else R.drawable.repeat_24px),
                                    contentDescription = stringResource(R.string.hc_repeat),
                                    tint = if (repeat != Player.REPEAT_MODE_OFF) white else soft,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showLyrics = !showLyrics }, enabled = lyrics != null || showLyrics, colors = ButtonDefaults.textButtonColors(contentColor = white, disabledContentColor = soft)) {
                        Text(stringResource(if (showLyrics) R.string.au_music_show_cover else R.string.au_music_show_lyrics))
                    }
                    TextButton(onClick = { showSleep = true }, colors = ButtonDefaults.textButtonColors(contentColor = white)) {
                        Text(stringResource(if (sleepEndsAt > 0) R.string.au_music_sleep_active else R.string.au_music_sleep))
                    }
                    TextButton(onClick = {
                        val uri = current.uri
                        if (uri != null) scope.launch { editing = viewModel.readTags(context, uri) ?: TagEditor.Tags(title = current.title, artist = current.artist) }
                    }, colors = ButtonDefaults.textButtonColors(contentColor = white)) { Text(stringResource(R.string.hc_edit)) }
                }
                // handle of the bottom sheet: tap or drag up for up next, lyrics and related
                val openDescription = stringResource(R.string.au10_music_open_sheet)
                Box(
                    Modifier.fillMaxWidth().height(32.dp)
                        .clickable(onClickLabel = openDescription) { sheet = true }
                        .draggable(rememberDraggableState { }, Orientation.Vertical, onDragStopped = { v -> if (v < -300f) sheet = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(soft))
                }
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
private fun LyricsView(
    lyrics: de.mm20.launcher2.comms.media.Lyrics?,
    positionMs: Long,
    modifier: Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (lyrics == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.hc_no_lyrics_found), color = inactiveColor)
        }
        return
    }
    if (lyrics.synced.isEmpty()) {
        LazyColumn(modifier) {
            item { Text(lyrics.plain, style = MaterialTheme.typography.bodyLarge, color = activeColor) }
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
                color = if (index == currentIndex) activeColor else inactiveColor,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
