package de.mm20.launcher2.ui.media.video

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media notification and lock screen / Bluetooth controls (play, pause, previous, next) for Telos Video.
 * The player belongs to the player screen, which hands it over with [VideoSession.attach]; the service only
 * wraps it in a MediaSession, so closing the screen also ends the notification. Leaving the screen pauses
 * the video (see VideoPlayerScreen); the notification then offers play, which continues with sound only.
 */
open class VideoPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        VideoSession.service = this
        VideoSession.player?.let { open(it) }
    }

    internal fun open(player: Player) {
        if (session?.player === player) return
        close()
        session = runCatching {
            MediaSession.Builder(this, player)
                .setId("telos_video")
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this, 0,
                        packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
        }.getOrNull()
        session?.let { runCatching { addSession(it) } }
    }

    internal fun close() {
        session?.let {
            runCatching { removeSession(it) }
            runCatching { it.release() } // the player is released by the player screen
        }
        session = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        if (VideoSession.service === this) VideoSession.service = null
        close()
        super.onDestroy()
    }
}

/** Same service inside the isolated `:player` process (see IsolatedVideoPlayerActivity) */
class IsolatedVideoPlaybackService : VideoPlaybackService()

/** Process-wide handover between the player screen and the service (both live in the same process) */
object VideoSession {
    @Volatile internal var player: Player? = null
    @Volatile internal var service: VideoPlaybackService? = null

    fun attach(context: android.content.Context, player: Player) {
        this.player = player
        val app = context.applicationContext
        val cls = if (de.mm20.launcher2.base.ProcessInfo.isolatedPlayer) IsolatedVideoPlaybackService::class.java
        else VideoPlaybackService::class.java
        val running = service
        if (running != null) running.open(player) else runCatching { app.startService(Intent(app, cls)) }
    }

    fun detach(context: android.content.Context, player: Player) {
        if (this.player !== player) return
        this.player = null
        val s = service
        s?.close()
        s?.stopSelf()
    }
}
