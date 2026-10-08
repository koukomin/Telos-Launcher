// === TELOS_PENDING_REVIEW_START: radio_mini_player ===
package de.mm20.launcher2.ui.comms

import android.content.ComponentName
import android.content.Context
import android.net.Uri
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

    private val _stationName = MutableStateFlow("")
    val stationName: StateFlow<String> = _stationName

    /** Current track announced by the stream (ICY metadata), empty when the stream sends none */
    private val _nowPlayingMetadata = MutableStateFlow("")
    val nowPlayingMetadata: StateFlow<String> = _nowPlayingMetadata

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val sleepEndsAt: StateFlow<Long> = RadioSleepTimer.endsAt

    private var currentStation: RadioStation? = null
    private var streamQueue: List<String> = emptyList()
    private var streamIndex = 0
    private var startWhenConnected = false
    private var connecting = false
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var cleared = false

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
        }, MoreExecutors.directExecutor())
    }

    /**
     * Streams that announce the current track replace the title of the media item with it, so the
     * station name always comes from the item and the track from the player.
     */
    private fun refreshMetadata() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem
        val station = item?.mediaMetadata?.title?.toString().orEmpty()
        val track = controller.mediaMetadata.title?.toString()?.trim().orEmpty()
        _stationName.value = station.ifEmpty { "Unknown Station" }
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

    fun stop() {
        mediaController?.stop()
        _isVisible.value = false
        RadioSleepTimer.cancel()
    }

    fun setSleepTimer(minutes: Int) = RadioSleepTimer.start(minutes)

    fun cancelSleepTimer() = RadioSleepTimer.cancel()

    // === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
    fun playStation(station: RadioStation) {
        currentStation = station
        _error.value = null
        viewModelScope.launch {
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
            _error.value = "This station cannot be played right now"
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
