package de.mm20.launcher2.ui.media.video

import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoServices
import de.mm20.launcher2.comms.media.video.torrent.TorrentState
import de.mm20.launcher2.comms.media.video.torrent.TorrentStreamer
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays local videos, web streams, and torrents. A torrent is opened first (finding peers, reading
 * the file list), then its video files play through the local stream address.
 */
@Composable
fun VideoPlayerScreen(
    uris: List<Uri>,
    titles: List<String>,
    startIndex: Int,
    torrentSource: String?,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    inPictureInPicture: Boolean,
) {
    val context = LocalContext.current
    var resolved by remember {
        mutableStateOf<ResolvedMedia?>(
            if (torrentSource == null) ResolvedMedia(uris, titles, uris.map { it.toString() }, startIndex) else null
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val torrent by TorrentStreamer.state.collectAsStateWithLifecycle()

    if (torrentSource != null) {
        LaunchedEffect(torrentSource) {
            val config = VideoServices.config()
            runCatching { TorrentStreamer.open(context, torrentSource, config.torrentWifiOnly) }
                .onSuccess { opened ->
                    val name = TorrentStreamer.torrentName()
                    resolved = ResolvedMedia(
                        uris = opened.videos.map { Uri.parse(it.url) },
                        titles = opened.videos.map { it.name.substringBeforeLast('.') },
                        mediaIds = opened.videos.map { "torrent:$name/${it.name}" },
                        startIndex = opened.startPosition,
                    )
                }
                .onFailure { error = it.message ?: "Torrent could not be opened" }
        }
        LaunchedEffect(torrentSource) {
            while (true) {
                delay(1000)
                TorrentStreamer.refreshStatus()
            }
        }
    }

    val r = resolved
    if (r == null) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                if (error == null) CircularProgressIndicator(color = Color.White)
                Text(
                    text = error ?: torrent.message.ifBlank { "Opening…" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (error == null && torrent.stage == TorrentState.Stage.FindingPeers) {
                    Text("This can take a minute.", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                    Text(if (error != null) "Close" else "Cancel", color = Color.White)
                }
            }
        }
        return
    }
    PlayerContent(r, torrentSource != null, torrent, onClose, onPlayingChanged, inPictureInPicture)
}

private data class ResolvedMedia(
    val uris: List<Uri>,
    val titles: List<String>,
    val mediaIds: List<String>,
    val startIndex: Int,
)

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    media: ResolvedMedia,
    isTorrent: Boolean,
    torrent: TorrentState,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    inPictureInPicture: Boolean,
) {
    val context = LocalContext.current
    val uris = media.uris
    val titles = media.titles
    val startIndex = media.startIndex

    val player = remember {
        // FFmpeg software decoders (AC3, E-AC3, DTS, TrueHD, ...) take over where the phone has no decoder
        val renderers = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context, renderers).build().apply {
            val items = uris.mapIndexed { index, uri ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(media.mediaIds[index])
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(index)).build())
                    .build()
            }
            val resumeKey = Uri.parse(media.mediaIds[startIndex])
            val progress = ResumeStore.progress(context, resumeKey)
            val resumeAt = if (progress in 0.02f..0.95f) ResumeStore.position(context, resumeKey) else 0L
            setMediaItems(items, startIndex, resumeAt)
            prepare()
            playWhenReady = true
        }
    }

    // Trakt.tv: report what is played (only when the user signed in and switched it on)
    val traktScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    fun scrobble(action: String, finished: Boolean = false) {
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        if (duration <= 0) return
        val progress = if (finished) 100f else player.currentPosition * 100f / duration
        val name = item.mediaMetadata.title?.toString().orEmpty()
        val parsed = EpisodeParser.parse(cleanTitle(name) + ".x")
        val appContext = context.applicationContext
        traktScope.launch { de.mm20.launcher2.comms.media.video.trakt.Trakt.scrobble(appContext, action, parsed, progress) }
    }

    fun saveProgress() {
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        if (duration > 0) ResumeStore.save(context, Uri.parse(item.mediaId), player.currentPosition, duration)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
                scrobble(if (isPlaying) "start" else "pause")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) scrobble("stop", finished = true)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) saveProgress()
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            scrobble("stop")
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { saveProgress() }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun attachSubtitle(subtitle: Uri, fileName: String, label: String? = null) {
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        val name = fileName.lowercase()
        val mime = when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            name.endsWith(".ttml") || name.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val withSubtitle = current.buildUpon()
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitle)
                        .setMimeType(mime)
                        .setLabel(label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        val position = player.currentPosition
        player.replaceMediaItem(index, withSubtitle)
        player.seekTo(index, position)
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { subtitle ->
        if (subtitle != null) {
            val name = context.contentResolver.query(subtitle, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
            attachSubtitle(subtitle, name)
        }
    }

    var subtitleMenu by remember { mutableStateOf(false) }
    var showOnlineSubtitles by remember { mutableStateOf(false) }
    var currentFileName by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentFileName = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // Subtitles that are fetched automatically when a video has none and the user turned this on
    LaunchedEffect(currentFileName) {
        val item = player.currentMediaItem ?: return@LaunchedEffect
        if (item.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true) return@LaunchedEffect
        val config = VideoServices.config()
        if (!config.autoDownload || !config.subtitlesEnabled) return@LaunchedEffect
        runCatching {
            val search = config.subtitleSearch()
            val parsed = EpisodeParser.parse(cleanTitle(currentFileName) + ".x")
            val results = search.search(parsed.title, config.languages, parsed.season, parsed.episode, parsed.year)
            val wanted = config.languages.split(',')
            val best = wanted.firstNotNullOfOrNull { lang -> results.firstOrNull { it.language.equals(lang, true) } }
                ?: results.firstOrNull()
            if (best != null) {
                val file = search.download(context, best)
                attachSubtitle(Uri.fromFile(file), file.name, best.language.uppercase())
            }
        }
    }

    if (showOnlineSubtitles) {
        OnlineSubtitleDialog(
            rawTitle = currentFileName,
            onDismiss = { showOnlineSubtitles = false },
            onFile = { file, label ->
                attachSubtitle(Uri.fromFile(file), file.name, label)
                showOnlineSubtitles = false
            },
        )
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            player.pause()
            sleepMinutes = 0
        }
    }
    if (showMenu) {
        PlaybackMenu(
            player = player,
            playerView = playerView,
            speed = speed,
            onSpeed = { speed = it; player.setPlaybackSpeed(it) },
            sleepMinutes = sleepMinutes,
            onSleep = { sleepMinutes = it },
            onDismiss = { showMenu = false },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    playerView = this
                    attachGestures(this, player, ctx as? android.app.Activity, { speed }, { gestureHint = it })
                    setShowSubtitleButton(true)
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    controllerShowTimeoutMs = 3000
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                }
            },
            update = { view -> view.useController = !inPictureInPicture },
        )
        gestureHint?.let { hint ->
            Text(
                text = hint,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        if (isTorrent && !inPictureInPicture && (controlsVisible || torrent.downloadBytesPerSecond < 50_000)) {
            Text(
                text = "Peers ${torrent.peers} · ${torrent.downloadBytesPerSecond / 1024} KB/s · ${(torrent.progress * 100).toInt()}% downloaded",
                color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 52.dp, end = 12.dp),
            )
        }
        AnimatedVisibility(
            visible = controlsVisible && !inPictureInPicture,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { subtitleMenu = true }) {
                        Text("Subtitles…", color = Color.White)
                    }
                    DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("From a file") },
                            onClick = { subtitleMenu = false; subtitlePicker.launch("*/*") },
                        )
                        DropdownMenuItem(
                            text = { Text("Search online") },
                            onClick = { subtitleMenu = false; showOnlineSubtitles = true },
                        )
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(painterResource(R.drawable.more_vert_24px), contentDescription = "Playback options", tint = Color.White)
                }
            }
        }
    }
}

/** File name without the container extension, for subtitle searches */
internal fun cleanTitle(name: String): String =
    name.replace(Regex("\\.(mkv|mp4|avi|mov|webm|m4v|ts|mpg|mpeg|wmv|flv)$", RegexOption.IGNORE_CASE), "")

@Composable
private fun OnlineSubtitleDialog(rawTitle: String, onDismiss: () -> Unit, onFile: (java.io.File, String) -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Searching…") }
    var results by remember { mutableStateOf<List<de.mm20.launcher2.comms.media.video.SubtitleResult>>(emptyList()) }
    var config by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.VideoServicesConfig?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(rawTitle) {
        val c = VideoServices.config()
        config = c
        if (!c.subtitlesEnabled) {
            status = "Add your OpenSubtitles key in Video services (the gear icon in the video list)."
            return@LaunchedEffect
        }
        runCatching {
            val parsed = EpisodeParser.parse(cleanTitle(rawTitle) + ".x")
            c.subtitleSearch().search(parsed.title, c.languages, parsed.season, parsed.episode, parsed.year)
        }.onSuccess {
            results = it
            status = if (it.isEmpty()) "No subtitles found for \"${cleanTitle(rawTitle)}\"" else ""
        }.onFailure { status = "Search failed: " + (it.message ?: "unknown error") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column {
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results.size) { i ->
                        val r = results[i]
                        Column(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                val c = config ?: return@clickable
                                busy = true
                                status = "Downloading…"
                                scope.launch {
                                    runCatching { c.subtitleSearch().download(context, r) }
                                        .onSuccess { onFile(it, r.language.uppercase()) }
                                        .onFailure {
                                            busy = false
                                            status = "Download failed: " + (it.message ?: "unknown error")
                                        }
                                }
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(r.release.ifBlank { r.fileName }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                r.language.uppercase() + " · " + r.downloads + " downloads" + if (r.hearingImpaired) " · HI" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
