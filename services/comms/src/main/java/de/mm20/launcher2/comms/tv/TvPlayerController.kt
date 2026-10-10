package de.mm20.launcher2.comms.tv

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.containedScope
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.comms.media.PlaybackCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Plays live TV in the app process with Media3 (HLS, DASH and progressive streams) and heals itself:
 *
 * 1. a stream that fails, or is not ready within [TvFailover.READY_TIMEOUT_MS], is replaced by the next
 *    stream of the same channel (the preferred stream first, streams that failed in the last 24 hours last);
 * 2. when all streams failed, the catalog's streams file is checked once (conditional GET); if the
 *    catalog changed, streams that were not tried yet are used;
 * 3. only then [error] is set.
 *
 * When a channel starts and the cached catalog is older than 24 hours a quiet refresh runs in the
 * background without delaying playback. Failed streams are remembered for 24 hours in [TvSettings].
 *
 * Playing TV pauses music and radio like a video does ([PlaybackCoordinator]); when something else
 * takes the audio focus, TV pauses and the coordinator forgets TV. Audio focus and noisy-audio pausing
 * are handled by ExoPlayer. The ExoPlayer exists only between [play] and [stop], so nothing holds a wake
 * lock or a decoder while TV is closed.
 *
 * Background: with [TvSettings.keepPlayingInBackground] off, leaving the app (the activity that hosts
 * Telos Media stops) or switching the screen off pauses playback (it never stops it; the user resumes).
 * With it on, playback goes on and [TvPlaybackService] (started here) shows the notification and the
 * media session around this same ExoPlayer; only the video track is switched off while in the background.
 *
 * All functions must be called on the main thread; the flows can be collected anywhere.
 */
class TvPlayerController(
    context: Context,
    private val catalog: TvCatalog,
    private val repository: TvRepository,
    private val settings: TvSettings,
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scope = containedScope(Dispatchers.Main.immediate)

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(8_000)
        .setUserAgent(DEFAULT_USER_AGENT)

    private var exo: ExoPlayer? = null

    private val _player = MutableStateFlow<Player?>(null)
    private val _channel = MutableStateFlow<TvChannel?>(null)
    private val _playing = MutableStateFlow(false)
    private val _buffering = MutableStateFlow(false)
    private val _error = MutableStateFlow<TvPlayerError?>(null)
    private val _streamIndex = MutableStateFlow(-1)
    private val _recovering = MutableStateFlow(false)

    /** The ExoPlayer to attach to a PlayerView; null while nothing is open */
    val player: StateFlow<Player?> get() = _player
    /** The channel being played (also while it is buffering or failing over); null after [stop] */
    val currentChannel: StateFlow<TvChannel?> get() = _channel
    /** True while the picture is actually playing */
    val isPlaying: StateFlow<Boolean> get() = _playing
    /** True while loading or rebuffering, including the time a failover takes */
    val isBuffering: StateFlow<Boolean> get() = _buffering
    /** Set only after all streams and the catalog check failed; cleared by the next [play] */
    val error: StateFlow<TvPlayerError?> get() = _error
    /** Index into [TvChannel.streams] of the stream being tried or played, -1 if none */
    val streamIndex: StateFlow<Int> get() = _streamIndex
    /** True while the controller looks for another stream or asks the catalog for updates (UI: "looking for another stream") */
    val isRecovering: StateFlow<Boolean> get() = _recovering
    /** Status of the catalog download, for a Refresh button and a "last updated" text */
    val refreshStatus: StateFlow<TvRefreshStatus> get() = catalog.refreshStatus

    private var queue: List<TvChannel> = emptyList()
    private var generation = 0
    private var ordered: List<TvStream> = emptyList()
    private var preferredUrl: String? = null
    private val tried = HashSet<String>()
    private var catalogChecked = false
    private var currentUrl = ""
    private var everReady = false
    private var reconnects = 0
    private var liveWindowRetried = false
    private var coordinatorActive = false

    private val timeout = Runnable { onStreamFailed() }

    // ---- leaving the app / screen off ----
    private var inBackground = false
    private val hostActivities = HashSet<Activity>()
    private var callbacksRegistered = false
    private var screenReceiver: BroadcastReceiver? = null
    private val backgroundCheck = Runnable {
        if (hostActivities.isEmpty() && !inBackground) {
            inBackground = true
            onLeftApp()
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        private fun isHost(a: Activity) = a.javaClass.name == SettingsDeepLinkContract.ACTIVITY_CLASS_NAME
        override fun onActivityStarted(activity: Activity) {
            if (!isHost(activity)) return
            hostActivities.add(activity)
            handler.removeCallbacks(backgroundCheck)
            if (inBackground) {
                inBackground = false
                applyVideoDisabled()
            }
        }
        override fun onActivityStopped(activity: Activity) {
            if (!isHost(activity)) return
            hostActivities.remove(activity)
            // a short delay so that a rotation (stop, then start of the new activity) is not "leaving"
            if (hostActivities.isEmpty()) handler.postDelayed(backgroundCheck, LEAVE_DELAY_MS)
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    init {
        // switching the setting on while playing starts the service, switching it off ends it (the service watches it too)
        scope.launch {
            settings.keepPlayingInBackground.collect {
                if (exo != null) {
                    if (it) startServiceIfEnabled()
                    applyVideoDisabled()
                }
            }
        }
    }

    private fun onLeftApp() {
        if (exo == null) return
        if (TvBackgroundPolicy.pauseWhenLeaving(settings.keepPlayingInBackground.value)) pause() else applyVideoDisabled()
    }

    /** Video track off while in the background with background playback on (saves data), on again otherwise */
    private fun applyVideoDisabled() {
        val p = exo ?: return
        val off = TvBackgroundPolicy.videoDisabled(settings.keepPlayingInBackground.value, inBackground)
        val params = p.trackSelectionParameters
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) == off) return
        p.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, off).build()
    }

    private fun startServiceIfEnabled() {
        if (!settings.keepPlayingInBackground.value) return
        // started from the foreground; Media3 promotes it to a foreground service once the session plays
        runCatching { app.startService(Intent(app, TvPlaybackService::class.java)) }
    }

    private fun registerLeaveWatchers() {
        if (!callbacksRegistered) {
            callbacksRegistered = true
            (app as? Application)?.registerActivityLifecycleCallbacks(activityCallbacks)
            // TV is started from the foreground
            inBackground = false
        }
        if (screenReceiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (exo != null && TvBackgroundPolicy.pauseWhenLeaving(settings.keepPlayingInBackground.value)) pause()
                }
            }
            val ok = runCatching {
                ContextCompat.registerReceiver(app, r, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            }.isSuccess
            if (ok) screenReceiver = r
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        screenReceiver = null
    }

    /**
     * Plays [channel]. [list] is the list the channel was picked from, used by [next] and [previous]
     * (if it does not contain the channel, next and previous do nothing).
     */
    fun play(channel: TvChannel, list: List<TvChannel> = emptyList()) {
        queue = list
        startChannel(channel)
    }

    /** Switches to the next channel of the list given to [play], wrapping around. False if there is none. */
    fun next(): Boolean = step(1)

    /** Switches to the previous channel of the list given to [play], wrapping around. False if there is none. */
    fun previous(): Boolean = step(-1)

    /** Pauses; the picture stays. */
    fun pause() {
        exo?.pause()
    }

    /** Continues after [pause]; reconnects to the live edge. After an error it restarts the channel. */
    fun resume() {
        val channel = _channel.value ?: return
        val p = exo
        if (_error.value != null || p == null) {
            startChannel(channel)
            return
        }
        activateCoordinator()
        if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            p.seekToDefaultPosition()
            p.prepare()
        }
        p.play()
    }

    /** Stops, releases the player and the audio focus, and clears the state. */
    fun stop() {
        generation++
        handler.removeCallbacks(timeout)
        // the service drops its session when the player flow goes null, before the player is released
        _player.value = null
        exo?.let { runCatching { it.release() } }
        exo = null
        unregisterScreenReceiver()
        _channel.value = null
        _playing.value = false
        _buffering.value = false
        _recovering.value = false
        _error.value = null
        _streamIndex.value = -1
        queue = emptyList()
        deactivateCoordinator()
    }

    // ---------------------------------------------------------------------------------------

    private fun step(delta: Int): Boolean {
        val current = _channel.value ?: return false
        val list = queue
        val i = list.indexOfFirst { it.id == current.id }
        if (i < 0 || list.size < 2) return false
        startChannel(list[((i + delta) % list.size + list.size) % list.size])
        return true
    }

    private fun startChannel(channel: TvChannel) {
        handler.removeCallbacks(timeout)
        val id = ++generation
        tried.clear()
        catalogChecked = channel.isCustom
        reconnects = 0
        ordered = emptyList()
        preferredUrl = null
        currentUrl = ""
        _channel.value = channel
        _streamIndex.value = -1
        _error.value = null
        _recovering.value = false
        _playing.value = false
        if (channel.streams.isEmpty() && channel.isCustom) {
            giveUp(TvPlayerError.NO_STREAMS)
            return
        }
        activateCoordinator()
        ensurePlayer().stop()
        startServiceIfEnabled()
        _buffering.value = true
        scope.launch {
            preferredUrl = runCatching { repository.preferredStream(channel.id) }.getOrNull()
            if (generation != id) return@launch
            // the optional extra sources may have been merged into the index after this channel object was made
            val latest = if (channel.isCustom) channel else catalog.index.value?.channel(channel.id) ?: channel
            if (latest.streams.size != channel.streams.size) _channel.value = latest
            ordered = TvFailover.order(latest.streams, preferredUrl, settings.badStreams(), System.currentTimeMillis())
            tryNext(id)
        }
        scope.launch { runCatching { repository.markPlayed(channel.id) } }
        // a catalog older than 24 hours is refreshed quietly, playback does not wait for it
        if (!channel.isCustom) catalog.refreshInBackgroundIfStale()
    }

    private fun tryNext(id: Int) {
        if (generation != id) return
        when (val d = TvFailover.next(ordered, tried, catalogChecked)) {
            is TvFailover.Decision.TryStream -> startStream(d.stream)
            TvFailover.Decision.CheckCatalog -> {
                _recovering.value = true
                _buffering.value = true
                scope.launch {
                    val result = runCatching { catalog.refreshStreams() }.getOrDefault(TvRefreshResult.FAILED)
                    if (generation != id) return@launch
                    catalogChecked = true
                    if (result == TvRefreshResult.UPDATED) {
                        val fresh = _channel.value?.let { catalog.index.value?.channel(it.id) }
                        if (fresh != null) {
                            _channel.value = fresh
                            ordered = TvFailover.order(fresh.streams, preferredUrl, settings.badStreams(), System.currentTimeMillis())
                        }
                    }
                    tryNext(id)
                }
            }
            TvFailover.Decision.GiveUp -> giveUp(
                if (ordered.isEmpty()) TvPlayerError.NO_STREAMS else TvPlayerError.ALL_STREAMS_FAILED
            )
        }
    }

    private fun startStream(stream: TvStream) {
        val p = ensurePlayer()
        tried.add(stream.url)
        currentUrl = stream.url
        everReady = false
        liveWindowRetried = false
        _streamIndex.value = _channel.value?.streams?.indexOfFirst { it.url == stream.url } ?: -1
        httpFactory.setUserAgent(stream.userAgent.ifBlank { DEFAULT_USER_AGENT })
        httpFactory.setDefaultRequestProperties(
            if (stream.referrer.isNotBlank()) mapOf("Referer" to stream.referrer) else emptyMap()
        )
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setMediaId(_channel.value?.id ?: stream.url)
            // shown by the notification, the lock screen and Bluetooth displays
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(_channel.value?.name)
                    .setArtist(app.getString(I18nR.string.au15_tvbg_live))
                    .setArtworkUri(_channel.value?.logoUrl?.takeIf { it.startsWith("http") }?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .apply {
                val lower = stream.url.lowercase()
                when {
                    ".m3u8" in lower -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    ".mpd" in lower -> setMimeType(MimeTypes.APPLICATION_MPD)
                }
            }
            .build()
        _buffering.value = true
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TvFailover.READY_TIMEOUT_MS)
    }

    private fun onStreamFailed() {
        handler.removeCallbacks(timeout)
        val url = currentUrl
        if (url.isEmpty()) return
        if (everReady && reconnects < MAX_RECONNECTS) {
            // it played before: reconnect once more before treating it as dead
            reconnects++
            tried.remove(url)
        } else {
            settings.markBad(url)
        }
        runCatching { exo?.stop() }
        tryNext(generation)
    }

    private fun giveUp(reason: TvPlayerError) {
        handler.removeCallbacks(timeout)
        runCatching { exo?.stop() }
        _error.value = reason
        _recovering.value = false
        _buffering.value = false
        _playing.value = false
        deactivateCoordinator()
    }

    private fun ensurePlayer(): ExoPlayer {
        exo?.let { return it }
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val p = ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, httpFactory)))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            // ExoPlayer holds the lock only while it is playing
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.addListener(listener)
        exo = p
        registerLeaveWatchers()
        applyVideoDisabled()
        _player.value = p
        return p
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    handler.removeCallbacks(timeout)
                    _buffering.value = false
                    if (!everReady) {
                        everReady = true
                        _recovering.value = false
                        settings.clearBad(currentUrl)
                    }
                }
                Player.STATE_BUFFERING -> _buffering.value = true
                Player.STATE_ENDED -> if (currentUrl.isNotEmpty()) onStreamFailed()
                else -> _buffering.value = false
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = exo
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && p != null && !liveWindowRetried) {
                liveWindowRetried = true
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            onStreamFailed()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // something else plays now (radio, music, a video): do not fight for the focus
                handler.removeCallbacks(timeout)
                if (coordinatorActive) {
                    coordinatorActive = false
                    PlaybackCoordinator.onVideoSuperseded(app)
                }
            }
        }
    }

    private fun activateCoordinator() {
        if (coordinatorActive) return
        coordinatorActive = true
        PlaybackCoordinator.onVideoStarted(app)
    }

    private fun deactivateCoordinator() {
        if (!coordinatorActive) return
        coordinatorActive = false
        PlaybackCoordinator.onVideoStopped(app)
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Telos TV"
        private const val MAX_RECONNECTS = 3
        private const val LEAVE_DELAY_MS = 700L
    }
}
