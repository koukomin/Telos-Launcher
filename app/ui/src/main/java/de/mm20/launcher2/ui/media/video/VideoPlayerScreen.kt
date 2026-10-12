package de.mm20.launcher2.ui.media.video

import androidx.compose.ui.res.stringResource
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
import de.mm20.launcher2.comms.media.video.VideoPrefs
import de.mm20.launcher2.comms.media.video.SubtitleFiles
import de.mm20.launcher2.comms.media.video.SubtitleService
import de.mm20.launcher2.comms.media.video.SubtitleResult
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
            runCatching { TorrentStreamer.open(context, torrentSource, config.torrentWifiOnly, context.findActivity()) }
                .onSuccess { opened ->
                    val name = TorrentStreamer.torrentName()
                    resolved = ResolvedMedia(
                        uris = opened.videos.map { Uri.parse(it.url) },
                        titles = opened.videos.map { it.name.substringBeforeLast('.') },
                        mediaIds = opened.videos.map { "torrent:$name/${it.name}" },
                        startIndex = opened.startPosition,
                    )
                }
                .onFailure {
                    error = if (it is de.mm20.launcher2.comms.media.video.torrent.TorrentRouteNotReadyException) {
                        context.getString(R.string.au6_wgvideo_route_not_ready)
                    } else it.message ?: context.getString(R.string.au_video_torrent_failed)
                }
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
                    text = error ?: stringResource(
                        if (torrent.stage == TorrentState.Stage.FindingPeers) R.string.au_video_finding_peers else R.string.au_video_opening
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (error == null && torrent.stage == TorrentState.Stage.FindingPeers) {
                    Text(stringResource(R.string.hc_this_can_take_a_minute), color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(if (error != null) R.string.hc_close else R.string.hc_cancel), color = Color.White)
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

    // music and radio pause while the video plays; the main process decides about resuming them
    fun videoState(state: String) {
        de.mm20.launcher2.comms.media.video.PlayerBridge.send(
            context.applicationContext,
            de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_VIDEO,
        ) { putString("state", state) }
    }

    val player = remember {
        videoState("started")
        // FFmpeg software decoders (AC3, E-AC3, DTS, TrueHD, ...) take over where the phone has no decoder
        val renderers = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context, renderers)
            // addresses of network storages (rem://) are read through Telos Files
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(RemoteRoutingDataSource.factory(context)))
            // audio focus (other apps pause or duck) and pausing when the headphones are unplugged
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            setVideoChangeFrameRateStrategy(
                if (VideoPrefs.matchFrameRate(context)) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            )
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

    // web streams (not local files, not the torrent's local address) reconnect by themselves when the connection is lost
    val reconnector = remember(player) {
        if (!isTorrent && uris.any { it.scheme == "http" || it.scheme == "https" }) {
            de.mm20.launcher2.comms.media.StreamReconnector(context.applicationContext, player).also { it.attach() }
        } else null
    }
    val reconnecting by (reconnector?.reconnecting ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val waitingForNetwork by (reconnector?.waiting ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()

    // Trakt.tv: report what is played (only when the user signed in and switched it on)
    val traktScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    // last Trakt action and the item it was about, so a stop is not sent twice
    val traktState = remember { arrayOfNulls<Any>(2) }
    fun scrobble(action: String, finished: Boolean = false, forItem: MediaItem? = null) {
        val item = forItem ?: player.currentMediaItem ?: return
        if (action == "stop" && traktState[0] == "stop" && traktState[1] == item.mediaId) return
        val duration = player.duration
        if (duration <= 0 && !finished) return
        val progress = if (finished || duration <= 0) 100f else (player.currentPosition * 100f / duration).coerceIn(0f, 100f)
        traktState[0] = action
        traktState[1] = item.mediaId
        val name = item.mediaMetadata.title?.toString().orEmpty()
        val parsedName = cleanTitle(name) + ".x"
        val appContext = context.applicationContext
        if (de.mm20.launcher2.base.ProcessInfo.isolatedPlayer) {
            // the Trakt login lives in the main process
            de.mm20.launcher2.comms.media.video.PlayerBridge.send(appContext, de.mm20.launcher2.comms.media.video.PlayerBridge.ACTION_SCROBBLE) {
                putString("action", action); putString("name", parsedName); putFloat("progress", progress)
            }
            return
        }
        val parsed = EpisodeParser.parse(parsedName)
        traktScope.launch { runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.scrobble(appContext, action, parsed, progress) } }
    }

    // the item that was saved last: when the player moves on by itself, this one has been played to the end
    var lastSavedId by remember { mutableStateOf<String?>(null) }
    var lastSavedDuration by remember { mutableStateOf(0L) }

    fun saveProgress() {
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        if (duration > 0) {
            ResumeStore.save(context, Uri.parse(item.mediaId), player.currentPosition, duration)
            lastSavedId = item.mediaId
            lastSavedDuration = duration
        }
    }

    // notification with play / pause / previous / next (also on the lock screen and Bluetooth)
    DisposableEffect(player) {
        VideoSession.attach(context, player)
        onDispose { VideoSession.detach(context, player) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlayingChanged(isPlaying)
                if (isPlaying) videoState("started")
                // the pause while the connection is restored is not a pause by the user
                if (isPlaying || reconnector?.reconnecting?.value != true) scrobble(if (isPlaying) "start" else "pause")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) scrobble("stop", finished = true)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Trakt: the episode that ended is done, the next one starts
                    traktState[1]?.let { id -> if (id != mediaItem?.mediaId) {
                        val prev = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }.firstOrNull { it.mediaId == id }
                        if (prev != null) scrobble("stop", finished = true, forItem = prev)
                    } }
                    scrobble("start")
                    // player.currentMediaItem is the next one now: the episode that ended counts as watched
                    lastSavedId?.let { id -> if (id != mediaItem?.mediaId) ResumeStore.save(context, Uri.parse(id), lastSavedDuration, lastSavedDuration) }
                    saveProgress()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            scrobble("stop")
            player.removeListener(listener)
            reconnector?.release()
            player.release()
            videoState("stopped")
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { saveProgress() }
    // there is no service behind the player: when the screen is left (and it is not the picture-in-picture
    // window), sound must not go on in the background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause(); videoState("stopped") }

    var playbackError by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackError = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) playbackError = false
            }
        }
        player.addListener(errorListener)
        onDispose { player.removeListener(errorListener) }
    }
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

    val scope = rememberCoroutineScope()
    var subFile by remember { mutableStateOf<java.io.File?>(null) }
    var subLabel by remember { mutableStateOf<String?>(null) }
    var subDelay by remember { mutableStateOf(0L) }
    var subStyle by remember { mutableStateOf(VideoPrefs.subtitleStyle(context)) }
    var matchFps by remember { mutableStateOf(VideoPrefs.matchFrameRate(context)) }

    /** Loads a subtitle file into the player; the delay starts at zero */
    fun loadSubtitleFile(file: java.io.File, label: String?) {
        subFile = file
        subLabel = label
        subDelay = 0L
        attachSubtitle(Uri.fromFile(file), file.name, label)
    }

    fun changeDelay(ms: Long) {
        val file = subFile ?: return
        subDelay = ms
        scope.launch {
            val shifted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SubtitleFiles.shifted(context, file, ms) }
            attachSubtitle(Uri.fromFile(shifted), shifted.name, subLabel)
        }
    }

    var currentFileName by remember { mutableStateOf(titles.getOrNull(startIndex).orEmpty()) }
    var currentVideoKey by remember { mutableStateOf<String?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { subtitle ->
        if (subtitle != null) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val name = context.contentResolver.query(subtitle, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
                        // a subtitle file is small: a huge file picked by mistake must not fill the memory
                        val bytes = context.contentResolver.openInputStream(subtitle)!!.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                if (out.size() > 6_000_000) error("The subtitle file is too big")
                            }
                            out.toByteArray()
                        }
                        val lang = VideoServices.config().languages.substringBefore(',')
                        SubtitleFiles.import(context, name, bytes, lang, currentVideoKey)
                    }
                }.onSuccess { loadSubtitleFile(it, null) }
                    .onFailure { toast(context, context.getString(R.string.au_video_subtitle_failed)) }
            }
        }
    }

    var subtitleMenu by remember { mutableStateOf(false) }
    var showOnlineSubtitles by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        var shownId = player.currentMediaItem?.mediaId
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // putting a subtitle into the item (replaceMediaItem) reports a transition to the same video:
                // the subtitle file and its delay must survive that
                if (mediaItem?.mediaId == shownId) return
                shownId = mediaItem?.mediaId
                currentFileName = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                subFile = null
                subDelay = 0L
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // The subtitle used last time for this video comes back without asking the internet; otherwise,
    // when the user turned it on, one is fetched from the sources (in their order)
    LaunchedEffect(currentFileName) {
        val item = player.currentMediaItem ?: return@LaunchedEffect
        val config = VideoServices.config()
        val query = SubtitleService.queryFor(context, item.localConfiguration?.uri, currentFileName, config.languages)
        val key = SubtitleFiles.videoKey(currentFileName, query.byteSize)
        currentVideoKey = key
        if (item.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true) return@LaunchedEffect
        SubtitleFiles.lastFor(context, key)?.let { loadSubtitleFile(it, null); return@LaunchedEffect }
        if (!config.autoDownload || !SubtitleService.available(context, config)) return@LaunchedEffect
        runCatching {
            val outcome = SubtitleService.search(context, config, query)
            val best = outcome.results.firstOrNull { it.language in query.languages } ?: outcome.results.firstOrNull()
            if (best != null) {
                val file = SubtitleFiles.fetch(context, SubtitleService.provider(context, config, best.provider)!!, best, key)
                loadSubtitleFile(file, best.language.uppercase())
            }
        }
    }

    if (showOnlineSubtitles) {
        OnlineSubtitleDialog(
            rawTitle = currentFileName,
            uri = player.currentMediaItem?.localConfiguration?.uri,
            videoKey = currentVideoKey,
            onDismiss = { showOnlineSubtitles = false },
            onFile = { file, label ->
                loadSubtitleFile(file, label)
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
            subDelayMs = subDelay,
            onSubDelay = { changeDelay(it) },
            hasExternalSub = subFile != null,
            subStyle = subStyle,
            onSubStyle = { subStyle = it; VideoPrefs.setSubtitleStyle(context, it) },
            matchFps = matchFps,
            onMatchFps = {
                matchFps = it
                VideoPrefs.setMatchFrameRate(context, it)
                player.setVideoChangeFrameRateStrategy(
                    if (it) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                )
            },
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
                    resizeMode = VideoPrefs.resizeMode(ctx)
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
            update = { view ->
                view.useController = !inPictureInPicture
                view.subtitleView?.apply {
                    val box = subStyle.edge == 3
                    setStyle(
                        androidx.media3.ui.CaptionStyleCompat(
                            subStyle.color,
                            if (box) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            when (subStyle.edge) {
                                1 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                2 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                            },
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                    setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subStyle.scale)
                }
            },
        )
        if (reconnecting && !inPictureInPicture) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(if (waitingForNetwork) R.string.au16_reconnect_waiting else R.string.au16_reconnect_label),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else if (playbackError && !inPictureInPicture) {
            val remote = player.currentMediaItem?.localConfiguration?.uri?.let { RemoteVideo.isRemote(it) } == true
            Text(
                text = stringResource(if (remote) R.string.vn_network_open_failed else R.string.au_video_playback_error),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
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
                text = stringResource(
                    R.string.au_video_torrent_stats, torrent.peers, torrent.downloadBytesPerSecond / 1024, (torrent.progress * 100).toInt()
                ),
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
                    Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back), tint = Color.White)
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
                        Text(stringResource(R.string.hc_subtitles_2), color = Color.White)
                    }
                    DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hc_from_a_file)) },
                            onClick = { subtitleMenu = false; subtitlePicker.launch("*/*") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hc_search_online)) },
                            onClick = { subtitleMenu = false; showOnlineSubtitles = true },
                        )
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(painterResource(R.drawable.more_vert_24px), contentDescription = stringResource(R.string.hc_playback_options), tint = Color.White)
                }
            }
        }
    }
}

/** File name without the container extension, for subtitle searches */
internal fun cleanTitle(name: String): String =
    name.replace(Regex("\\.(mkv|mp4|avi|mov|webm|m4v|ts|mpg|mpeg|wmv|flv)$", RegexOption.IGNORE_CASE), "")

@Composable
private fun OnlineSubtitleDialog(
    rawTitle: String,
    uri: Uri?,
    videoKey: String?,
    onDismiss: () -> Unit,
    onFile: (java.io.File, String) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.getString(R.string.vn_searching)) }
    var results by remember { mutableStateOf<List<SubtitleResult>>(emptyList()) }
    var config by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.VideoServicesConfig?>(null) }
    var query by remember { mutableStateOf<de.mm20.launcher2.comms.media.video.SubtitleQuery?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searchAll by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(rawTitle, searchAll) {
        busy = true
        status = context.getString(R.string.vn_searching)
        val c = VideoServices.config()
        config = c
        if (!SubtitleService.available(context, c)) {
            status = context.getString(R.string.vn_no_sources)
            busy = false
            return@LaunchedEffect
        }
        runCatching {
            val q = SubtitleService.queryFor(context, uri, rawTitle, c.languages)
            query = q
            SubtitleService.search(context, c, q, all = searchAll)
        }.onSuccess { o ->
            results = o.results
            source = o.source
            status = when {
                o.results.isEmpty() && o.errors.isNotEmpty() -> context.getString(R.string.vn_some_sources_failed, o.errors.joinToString("; "))
                o.results.isEmpty() -> context.getString(R.string.vn_no_subtitles_found, cleanTitle(rawTitle))
                o.errors.isNotEmpty() -> context.getString(R.string.vn_some_sources_failed, o.errors.joinToString("; "))
                else -> ""
            }
        }.onFailure { status = (it.message ?: context.getString(R.string.au_video_unknown_error)) }
        busy = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hc_subtitles)) },
        text = {
            Column {
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results.size) { i ->
                        val r = results[i]
                        Column(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                val c = config ?: return@clickable
                                val p = SubtitleService.provider(context, c, r.provider) ?: return@clickable
                                busy = true
                                status = context.getString(R.string.vn_downloading)
                                scope.launch {
                                    runCatching { SubtitleFiles.fetch(context, p, r, videoKey) }
                                        .onSuccess { onFile(it, r.language.uppercase()) }
                                        .onFailure {
                                            busy = false
                                            status = (it.message ?: context.getString(R.string.au_video_unknown_error))
                                        }
                                }
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(r.release.ifBlank { r.fileName }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    r.language.uppercase(),
                                    r.downloads.takeIf { it > 0 }?.let { context.getString(R.string.au_video_downloads_count, it) },
                                    if (r.hearingImpaired) context.getString(R.string.au_video_hearing_impaired_short) else null,
                                    if (r.hashMatch) context.getString(R.string.vn_exact_match) else null,
                                    r.provider.replace('_', ' '),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy && !searchAll, onClick = { searchAll = true }) { Text(stringResource(R.string.vn_search_all_sources)) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_close)) } },
    )
}

/** The activity behind a context, which may be wrapped */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
