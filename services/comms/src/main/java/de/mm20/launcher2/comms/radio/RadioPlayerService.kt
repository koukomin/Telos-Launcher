// === TELOS_PENDING_REVIEW_START: sms_and_radio_engine ===
package de.mm20.launcher2.comms.radio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var lastRecordedTitle = ""

    override fun onCreate() {
        super.onCreate()

        // Configure audio attributes for media playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                recordTrack()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastRecordedTitle = ""
            }
        })

        RadioSleepTimer.onExpire = { player?.pause() }

        mediaSession = MediaSession.Builder(this, exo).build()
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // MediaSessionService handles Foreground service lifecycle and notification
    // seamlessly on Android 14+ through the Media3 framework.
    override fun onDestroy() {
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
