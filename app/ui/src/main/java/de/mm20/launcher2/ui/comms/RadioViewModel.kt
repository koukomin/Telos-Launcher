// === TELOS_PENDING_REVIEW_START: radio_mini_player ===
package de.mm20.launcher2.ui.comms

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.mm20.launcher2.comms.radio.RadioPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import com.google.common.util.concurrent.MoreExecutors

class RadioViewModel : ViewModel(), KoinComponent {

    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _stationName = MutableStateFlow("")
    val stationName: StateFlow<String> = _stationName

    private val _nowPlayingMetadata = MutableStateFlow("")
    val nowPlayingMetadata: StateFlow<String> = _nowPlayingMetadata

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    fun initialize(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, RadioPlayerService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _stationName.value = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Station"
                    _nowPlayingMetadata.value = mediaItem?.mediaMetadata?.subtitle?.toString() ?: ""
                    _isVisible.value = mediaItem != null
                }
            })
            
            // Sync initial state
            mediaController?.let {
                _isPlaying.value = it.isPlaying
                val currentItem = it.currentMediaItem
                _stationName.value = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown Station"
                _nowPlayingMetadata.value = currentItem?.mediaMetadata?.subtitle?.toString() ?: ""
                _isVisible.value = currentItem != null
            }
        }, MoreExecutors.directExecutor())
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
    }

    // === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
    fun playStation(station: de.mm20.launcher2.comms.model.RadioStation) {
        val controller = mediaController ?: return
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(station.name)
            .setSubtitle(station.streamUrl)
            .setArtworkUri(android.net.Uri.parse(station.faviconUrl))
            .build()
            
        val item = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.id)
            .setMediaMetadata(metadata)
            .build()
            
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }
    // === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}
// === TELOS_PENDING_REVIEW_END: radio_mini_player ===
