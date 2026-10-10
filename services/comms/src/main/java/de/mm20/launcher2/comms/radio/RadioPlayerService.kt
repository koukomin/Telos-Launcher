// === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
package de.mm20.launcher2.comms.radio

import de.mm20.launcher2.base.containedScope
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.mm20.launcher2.comms.media.PlaybackCoordinator
import de.mm20.launcher2.comms.media.StreamReconnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RadioPlayerService : MediaSessionService(), KoinComponent {

    private val repository: RadioRepository by inject()
    private val scope = containedScope(Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var lastRecordedTitle = ""
    private var scrobbler: de.mm20.launcher2.comms.scrobble.RadioScrobbleTracker? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnector: StreamReconnector? = null
    private var reconnectMirror: kotlinx.coroutines.Job? = null

    // Nothing keeps the service (and the decoder) alive after the radio has been paused for a while
    private val idleStop: Runnable = Runnable {
        if (reconnecting.value) {
            handler.postDelayed(idleStop, IDLE_STOP_MS) // the connection is being restored, the user did not stop
        } else if (PlaybackCoordinator.isWaiting(this, PlaybackCoordinator.KIND_RADIO)) {
            handler.postDelayed(idleStop, IDLE_STOP_MS) // a video paused the radio, it may continue
        } else if (player?.isPlaying != true && player?.playWhenReady != true) pauseAllPlayersAndStopSelf()
    }

    override fun onCreate() {
        super.onCreate()

        // Configure audio attributes for media playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Streams are requested with an app specific user agent. Android's default "Dalvik/..."
        // agent is rejected by some stations (same fix as Transistor 4.3.8, for example Live365).
        val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // keeps the CPU (and for a stream the Wi-Fi) awake while playing with the screen off
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exo

        // keeps trying to reconnect when the stream is lost, until the user pauses or stops
        val rc = StreamReconnector(this, exo, isLive = { true }).also { it.attach() }
        reconnector = rc
        reconnectMirror = scope.launch {
            kotlinx.coroutines.flow.combine(rc.reconnecting, rc.waiting) { r, w -> r to w }.collect { (r, w) ->
                _reconnecting.value = r
                _waiting.value = w
            }
        }

        exo.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                recordTrack()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastRecordedTitle = ""
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    PlaybackCoordinator.onFocusLoss(this@RadioPlayerService, PlaybackCoordinator.KIND_RADIO)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(idleStop)
                if (!isPlaying) handler.postDelayed(idleStop, IDLE_STOP_MS)
            }
        })

        PlaybackCoordinator.register(
            PlaybackCoordinator.KIND_RADIO,
            PlaybackCoordinator.Participant(
                isPlaying = { exo.isPlaying || exo.playWhenReady },
                pause = { exo.pause() },
                resume = {
                    // a live stream: reconnect to the station instead of playing stale buffered audio
                    if (exo.mediaItemCount > 0) {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                        exo.play()
                    }
                },
            )
        )
        scrobbler = de.mm20.launcher2.comms.scrobble.RadioScrobbleTracker(this, exo).also { it.attach() }
        RadioSleepTimer.onExpire = { player?.pause() }
        handler.postDelayed(idleStop, IDLE_STOP_MS)

        mediaSession = MediaSession.Builder(this, stationSwitchingPlayer(exo)).build()
    }

    /**
     * Next / previous on the notification, lock screen, headset buttons and Android Auto move to the
     * neighbouring station of the collection (as in Transistor), because only one station is queued.
     */
    private fun stationSwitchingPlayer(exo: ExoPlayer): Player = object : ForwardingPlayer(exo) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun stop() {
            reconnector?.cancel()
            super.stop()
        }

        override fun seekToNext() = skipStation(1)
        override fun seekToNextMediaItem() = skipStation(1)
        override fun seekToPrevious() = skipStation(-1)
        override fun seekToPreviousMediaItem() = skipStation(-1)
    }

    private fun skipStation(step: Int) {
        val exo = player ?: return
        val currentId = exo.currentMediaItem?.mediaId ?: return
        scope.launch {
            val stations = runCatching { repository.observeFavorites().first() }.getOrNull().orEmpty()
            if (stations.size < 2) return@launch
            val index = stations.indexOfFirst { it.id == currentId }
            val target = stations[Math.floorMod(index + step, stations.size)]
            val url = StreamResolver.resolve(target.streamUrl).urls.firstOrNull() ?: target.streamUrl
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(target.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(target.name)
                        .setArtworkUri(target.faviconUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                        .build()
                )
                .apply { if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            withContext(Dispatchers.Main) {
                exo.setMediaItem(item)
                exo.prepare()
                exo.play()
            }
        }
    }

    /**
     * Streams announce the current track in ICY metadata. The player then replaces the title of the
     * media item (the station name) with the track. Each new track goes into the history.
     */
    private fun recordTrack() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val stationName = item.mediaMetadata.title?.toString().orEmpty()
        val track = p.mediaMetadata.title?.toString()?.trim().orEmpty()
        if (track.isEmpty() || track == stationName || track == lastRecordedTitle) return
        lastRecordedTitle = track
        val stationId = item.mediaId
        scope.launch {
            runCatching { repository.addHistory(stationId, stationName, track) }
        }
    }

    // The notification (and with it the foreground state) stays while the stream is reconnecting,
    // although the player is idle after the error
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || reconnecting.value)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // MediaSessionService handles Foreground service lifecycle and notification
    // seamlessly on Android 14+ through the Media3 framework.
    companion object {
        const val USER_AGENT = "Telos Radio"
        private const val IDLE_STOP_MS = 5 * 60 * 1000L

        private val _reconnecting = MutableStateFlow(false)
        /** True while the radio stream is lost and being reconnected */
        val reconnecting: StateFlow<Boolean> = _reconnecting

        private val _waiting = MutableStateFlow(false)
        /** True while reconnecting and the device is offline */
        val waiting: StateFlow<Boolean> = _waiting
    }

    override fun onDestroy() {
        PlaybackCoordinator.unregister(PlaybackCoordinator.KIND_RADIO)
        handler.removeCallbacks(idleStop)
        reconnectMirror?.cancel()
        reconnector?.release()
        reconnector = null
        _reconnecting.value = false
        _waiting.value = false
        scrobbler?.release()
        scrobbler = null
        RadioSleepTimer.onExpire = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
// === TELOS_PENDING_REVIEW_END: sms_and_radio_engine ===
