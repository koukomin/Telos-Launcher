// === TELOS_PENDING_REVIEW_START: radio_mini_player ===
package de.mm20.launcher2.ui.comms

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import de.mm20.launcher2.ui.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.radio.RadioPlayerService
import de.mm20.launcher2.comms.radio.RadioRecorder
import de.mm20.launcher2.comms.radio.RadioSleepTimer
import de.mm20.launcher2.comms.radio.StreamResolver
import de.mm20.launcher2.comms.repository.RadioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RadioViewModel : ViewModel(), KoinComponent {

    private val repository: RadioRepository by inject()

    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** Media id (= station id) of the item that is loaded in the player, empty when none */
    private val _stationId = MutableStateFlow("")
    val stationId: StateFlow<String> = _stationId

    /** True while the player should be playing (also while it is still buffering) */
    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady

    /** True while a station is starting (resolving the stream) or the player is buffering */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _stationName = MutableStateFlow("")
    val stationName: StateFlow<String> = _stationName

    /** Current track announced by the stream (ICY metadata), empty when the stream sends none */
    private val _nowPlayingMetadata = MutableStateFlow("")
    val nowPlayingMetadata: StateFlow<String> = _nowPlayingMetadata

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    private val _error = MutableStateFlow<Int?>(null)
    /** String resource of the current playback error, or null */
    val error: StateFlow<Int?> = _error

    val sleepEndsAt: StateFlow<Long> = RadioSleepTimer.endsAt

    val recordingState: StateFlow<RadioRecorder.State> = RadioRecorder.state

    /**
     * Starts or stops recording the stream that is playing now. Returns a string resource to show
     * when recording could not be started, or null.
     */
    fun toggleRecording(context: Context): Int? {
        if (RadioRecorder.state.value is RadioRecorder.State.Recording) {
            RadioRecorder.stop()
            return null
        }
        val item = mediaController?.currentMediaItem ?: return R.string.au2_radio2_nothing_playing
        val url = item.localConfiguration?.uri?.toString() ?: return R.string.au2_radio2_nothing_playing
        if (!RadioRecorder.isRecordable(url)) return R.string.au2_radio2_hls
        val name = _stationName.value.ifEmpty { context.getString(R.string.au_radio_unknown_station) }
        RadioRecorder.start(context, item.mediaId, name, url)
        return null
    }

    private var currentStation: RadioStation? = null
    private var streamQueue: List<String> = emptyList()
    private var streamIndex = 0
    private var startWhenConnected = false
    private var connecting = false
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var cleared = false
    private var playJob: kotlinx.coroutines.Job? = null

    fun initialize(context: Context) {
        if (mediaController != null || connecting) return
        connecting = true
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlayerService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        this.controllerFuture = controllerFuture

        controllerFuture.addListener({
            connecting = false
            val controller = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            if (cleared) {
                // the screen was left while the service was still being connected: nobody would release this controller
                controller.release()
                return@addListener
            }
            mediaController = controller
            if (startWhenConnected) { startWhenConnected = false; startCurrentStream() }
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) _error.value = null
                    refreshPlaybackState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    refreshPlaybackState()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    refreshPlaybackState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    refreshMetadata()
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    refreshMetadata()
                }

                override fun onPlayerError(error: PlaybackException) {
                    // a station that was picked with next/previous on the notification is not the one this screen started:
                    // its streams are not in the queue, trying "the next stream" would jump back to the old station
                    if (controller.currentMediaItem?.mediaId == currentStation?.id) tryNextStream()
                }
            })

            // Sync initial state
            _isPlaying.value = controller.isPlaying
            refreshMetadata()
            refreshPlaybackState()
        }, MoreExecutors.directExecutor())
    }

    /**
     * Streams that announce the current track replace the title of the media item with it, so the
     * station name always comes from the item and the track from the player.
     */
    private fun refreshPlaybackState() {
        val controller = mediaController ?: return
        _playWhenReady.value = controller.playWhenReady && controller.currentMediaItem != null &&
            controller.playbackState != Player.STATE_IDLE && controller.playbackState != Player.STATE_ENDED
        _isLoading.value = controller.currentMediaItem != null && controller.playWhenReady &&
            controller.playbackState == Player.STATE_BUFFERING
        if (controller.isPlaying) _isLoading.value = false
    }

    private fun refreshMetadata() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem
        _stationId.value = item?.mediaId.orEmpty()
        val station = item?.mediaMetadata?.title?.toString().orEmpty()
        val track = controller.mediaMetadata.title?.toString()?.trim().orEmpty()
        _stationName.value = station
        _nowPlayingMetadata.value = if (track.isNotEmpty() && track != station) track else ""
        _isVisible.value = item != null
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    /**
     * Tap on a station row or its button: pauses/resumes it when it is the loaded one, starts it otherwise.
     * Streams are live, so pausing keeps the player and the notification and play continues with live audio.
     */
    fun toggleStation(station: RadioStation) {
        if (_stationId.value == station.id && _isVisible.value && mediaController != null) togglePlayPause()
        else playStation(station)
    }

    fun stop() {
        mediaController?.stop()
        _isVisible.value = false
        _playWhenReady.value = false
        _isLoading.value = false
        _stationId.value = ""
        RadioSleepTimer.cancel()
    }

    fun setSleepTimer(minutes: Int) = RadioSleepTimer.start(minutes)

    fun cancelSleepTimer() = RadioSleepTimer.cancel()

    // === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
    fun playStation(station: RadioStation) {
        currentStation = station
        _error.value = null
        // a second tap while the first station is still being resolved must not start both
        playJob?.cancel()
        _isLoading.value = true
        _playWhenReady.value = true
        _stationId.value = station.id
        playJob = viewModelScope.launch {
            // playlist links (.pls, .m3u) are opened to find the real stream first
            val resolved = StreamResolver.resolve(station.streamUrl)
            streamQueue = (resolved.urls + station.alternateStreams).distinct()
                .ifEmpty { listOf(station.streamUrl) }
            streamIndex = 0
            startCurrentStream()
            runCatching { repository.countClick(station.id) }
        }
    }

    private fun startCurrentStream() {
        val controller = mediaController ?: run { startWhenConnected = true; return }
        val station = currentStation ?: return
        val url = streamQueue.getOrNull(streamIndex) ?: return

        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtworkUri(station.faviconUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()

        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(station.id)
            .setMediaMetadata(metadata)
        if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)

        controller.setMediaItem(builder.build())
        controller.prepare()
        controller.play()
    }

    /** The stream failed: try the next stream of the station, or report that none works */
    private fun tryNextStream() {
        if (streamIndex + 1 < streamQueue.size) {
            streamIndex++
            startCurrentStream()
        } else {
            _error.value = R.string.au_radio_cannot_play
            _isLoading.value = false
        }
    }
    // === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===

    override fun onCleared() {
        super.onCleared()
        cleared = true
        mediaController?.release()
        mediaController = null
        if (connecting) controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
// === TELOS_PENDING_REVIEW_END: radio_mini_player ===
