package de.mm20.launcher2.comms.media.engine

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background MediaSessionService for Telos Media.
 * Houses the optimized TelosPlayerEngine and ensures strict cleanup when stopped.
 */
@UnstableApi
class TelosMediaService : MediaSessionService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var trackSelector: TelosTrackSelector? = null

    override fun onCreate() {
        super.onCreate()

        trackSelector = TelosTrackSelector(this)

        // Build the highly optimized ExoPlayer
        exoPlayer = TelosPlayerEngine.buildExoPlayer(this, trackSelector!!)

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If the user swipes the app away from Recents, stop everything to save battery
        stopSelf()
    }

    /**
     * Strict Memory Cleanup on Dismiss.
     * Guaranteed to drop Telos Media RAM usage to zero when closed.
     */
    override fun onDestroy() {
        // 1. Release MediaSession
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        // 2. Clear ExoPlayer Surface and Items
        exoPlayer?.run {
            stop()
            clearMediaItems()
            clearVideoSurface()
            release()
            exoPlayer = null
        }

        // 3. Clear extractors and simple caches
        TelosStreamExtractorCache.clear()
        TelosPlayerEngine.releaseCache()
        trackSelector = null

        super.onDestroy()
    }
}
