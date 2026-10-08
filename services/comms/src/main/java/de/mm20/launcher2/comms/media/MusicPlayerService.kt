package de.mm20.launcher2.comms.media

import androidx.media3.common.AudioAttributes
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Plays the local music library. Media3 takes care of the notification and lock screen controls. */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var scrobbler: de.mm20.launcher2.comms.scrobble.ScrobbleTracker? = null
    private val handler = Handler(Looper.getMainLooper())

    // Nothing keeps the service (and the decoder) alive after music has been paused for a while
    private val idleStop = Runnable {
        val p = mediaSession?.player
        if (p?.isPlaying != true && p?.playWhenReady != true) pauseAllPlayersAndStopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // keeps the CPU (and for a stream the Wi-Fi) awake while playing with the screen off
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(idleStop)
                if (!isPlaying) handler.postDelayed(idleStop, IDLE_STOP_MS)
            }
        })
        handler.postDelayed(idleStop, IDLE_STOP_MS)
        mediaSession = MediaSession.Builder(this, player)
            .setBitmapLoader(AlbumArtBitmapLoader(this))
            .build()
        MusicSleepTimer.onExpire = { player.pause() }
        scrobbler = de.mm20.launcher2.comms.scrobble.ScrobbleTracker(this, player).also { it.attach() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(idleStop)
        scrobbler?.release()
        scrobbler = null
        MusicSleepTimer.onExpire = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private companion object {
        const val IDLE_STOP_MS = 5 * 60 * 1000L
    }
}
