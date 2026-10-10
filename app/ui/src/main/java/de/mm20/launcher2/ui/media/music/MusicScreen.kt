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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
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
    val playing by viewModel.isPlaying.collectAsStateWithLifecycle()

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
    val reduceFlow = remember(performanceSettings) { performanceSettings.reduceAnimations }
    val reduceAnimations by reduceFlow.collectAsState(false)
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
                    GroupPage(
                        group = current,
                        currentId = nowPlaying?.mediaId,
                        isPlaying = playing,
                        reduce = reduceAnimations,
                        onBack = { group = null },
                        onShare = { shareGroup(current) },
                        onPlay = { i -> viewModel.play(current.tracks, i) },
                        onShuffle = { viewModel.play(current.tracks.shuffled(), 0) },
                        onShareTrack = { shareTrack(it) },
                    )
                } else {
                    TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, divider = {}) {
                        listOf(stringResource(R.string.au10_music_tab_home), stringResource(R.string.au_music_tab_songs), stringResource(R.string.hc_albums), stringResource(R.string.au_music_tab_artists)).forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) })
                        }
                    }
                    when {
                        loading -> MusicHomeSkeleton(reduceAnimations)
                        tracks.isEmpty() -> MusicEmptyState(stringResource(R.string.hc_no_music_found_on_this_device), Modifier.fillMaxSize())
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
                            reduceAnimations = reduceAnimations,
                        )
                        tab == 1 -> TrackList(filtered, nowPlaying?.mediaId, playing, reduceAnimations, headers = true, onPlay = { i -> viewModel.play(filtered, i) }, onShare = { shareTrack(it) })
                        tab == 2 -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 112.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(albums, key = { it.tracks.first().albumId }) { album ->
                                Column(Modifier.pressScale(reduceAnimations, onClick = { group = album }, onLongClick = { shareGroup(album) })) {
                                    Cover(album.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
                                    Text(album.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                    Text(album.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(bottom = 112.dp), modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(artists, key = { _, a -> a.title }) { _, artist ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().pressScale(reduceAnimations, onClick = { group = artist }, onLongClick = { shareGroup(artist) }).padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Cover(artist.tracks.first(), Modifier.size(56.dp).clip(CircleShape))
                                    Column(Modifier.padding(start = 16.dp)) {
                                        Text(artist.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(artist.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TrackList(
    list: List<MusicTrack>,
    currentId: String?,
    isPlaying: Boolean,
    reduce: Boolean,
    headers: Boolean,
    onPlay: (Int) -> Unit,
    onShare: (MusicTrack) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val sections = remember(list, headers) {
        if (!headers) null else list.withIndex().groupBy { (_, t) ->
            val c = t.title.trim().firstOrNull()?.uppercaseChar()
            if (c != null && c.isLetter()) c.toString() else "#"
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 112.dp), modifier = Modifier.fillMaxSize()) {
        if (header != null) item(key = "header") { header() }
        if (sections == null) {
            itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
                TrackRow(track, index, currentId, isPlaying, reduce, onPlay, onShare)
            }
        } else {
            sections.forEach { (letter, entries) ->
                stickyHeader(key = "h_$letter") {
                    Text(
                        letter,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                items(entries, key = { it.value.id }) { (index, track) ->
                    TrackRow(track, index, currentId, isPlaying, reduce, onPlay, onShare)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: MusicTrack,
    index: Int,
    currentId: String?,
    isPlaying: Boolean,
    reduce: Boolean,
    onPlay: (Int) -> Unit,
    onShare: (MusicTrack) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val active = currentId != null && track.id.toString() == currentId
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(200),
        label = "rowBg",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp)).background(bg)
            .combinedClickable(
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                onClick = { onPlay(index) },
            )
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(track, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            if (active) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    EqualizerGlyph(Color.White, animate = isPlaying && !reduce)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.au10_music_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hc_play)) },
                    onClick = { menu = false; onPlay(index) },
                )
                ShareMenuItem(onClick = { menu = false; onShare(track) })
            }
        }
    }
}

/** Album / artist page: hero with large artwork on an artwork-coloured gradient, play and shuffle, track list */
@Composable
private fun GroupPage(
    group: TrackGroup,
    currentId: String?,
    isPlaying: Boolean,
    reduce: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onShareTrack: (MusicTrack) -> Unit,
) {
    val artUrl = group.tracks.firstOrNull()?.albumArtUri?.toString()
    val tint by rememberArtworkTint(artUrl, MaterialTheme.colorScheme.primary, reduce)
    val surface = MaterialTheme.colorScheme.surface
    TrackList(group.tracks, currentId, isPlaying, reduce, headers = false, onPlay = onPlay, onShare = onShareTrack, header = {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), surface))).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(painterResource(R.drawable.share_24px), contentDescription = stringResource(R.string.menu_share))
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 12.dp, color = Color.Transparent, modifier = Modifier.size(220.dp)) {
                ArtOrNote(artUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
            }
            Text(group.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 0.dp).padding(top = 16.dp))
            Text(group.subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onPlay(0) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                    Icon(painterResource(R.drawable.play_arrow_24px), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hc_play))
                }
                FilledTonalButton(onClick = onShuffle, shape = CircleShape, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                    Icon(painterResource(R.drawable.shuffle_24px), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hc_shuffle))
                }
            }
        }
    })
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
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                        val side = minOf(maxWidth * 0.88f, maxHeight)
                        val artScale by animateFloatAsState(
                            if (isPlaying || reduceAnimations) 1f else 0.9f,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                            label = "artScale",
                        )
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            shadowElevation = 24.dp,
                            color = Color.Transparent,
                            modifier = Modifier.size(side).graphicsLayer { scaleX = artScale; scaleY = artScale },
                        ) {
                            ArtOrNote(artUrl, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Text(current.artist, style = MaterialTheme.typography.bodyLarge, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                val previewLine = lyrics?.synced?.takeIf { it.isNotEmpty() }?.let { l ->
                    l.getOrNull(l.indexOfLast { it.timeMs <= position }.coerceAtLeast(0))?.text?.ifBlank { null }
                }
                if (!showLyrics && previewLine != null) {
                    Text(
                        previewLine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = white,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { showLyrics = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, contentColor = white, modifier = Modifier.fillMaxWidth()) {
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
                            track = { state ->
                                SliderDefaults.Track(
                                    sliderState = state,
                                    modifier = Modifier.requiredHeight(8.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = white,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                                    ),
                                )
                            },
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(((dragging ?: (position.toFloat() / duration)) * duration).toLong()), style = MaterialTheme.typography.labelSmall, color = soft)
                            Text(formatDuration(current.durationMs), style = MaterialTheme.typography.labelSmall, color = soft)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            val toggleHaptic = LocalHapticFeedback.current
                            IconButton(
                                onClick = { toggleHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.toggleShuffle() },
                                modifier = Modifier.clip(CircleShape).background(if (shuffle) Color.White.copy(alpha = 0.22f) else Color.Transparent),
                            ) {
                                Icon(painterResource(R.drawable.shuffle_24px), contentDescription = stringResource(R.string.hc_shuffle), tint = if (shuffle) white else soft)
                            }
                            IconButton(onClick = { viewModel.previous() }) {
                                Icon(painterResource(R.drawable.skip_previous_24px), contentDescription = stringResource(R.string.hc_previous))
                            }
                            val corner by androidx.compose.animation.core.animateDpAsState(
                                if (isPlaying) 40.dp else 28.dp,
                                animationSpec = if (reduceAnimations) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
                                label = "playCorner",
                            )
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(80.dp),
                                shape = RoundedCornerShape(corner),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = white, contentColor = bottom),
                            ) {
                                Icon(painterResource(if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px), contentDescription = stringResource(if (isPlaying) R.string.au_music_pause else R.string.hc_play), modifier = Modifier.size(40.dp))
                            }
                            IconButton(onClick = { viewModel.next() }) {
                                Icon(painterResource(R.drawable.skip_next_24px), contentDescription = stringResource(R.string.hc_next))
                            }
                            IconButton(
                                onClick = { toggleHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.cycleRepeat() },
                                modifier = Modifier.clip(CircleShape).background(if (repeat != Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.22f) else Color.Transparent),
                            ) {
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
