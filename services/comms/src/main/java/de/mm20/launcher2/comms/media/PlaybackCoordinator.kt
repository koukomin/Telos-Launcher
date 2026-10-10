package de.mm20.launcher2.comms.media

import android.content.Context
import android.os.SystemClock
import de.mm20.launcher2.comms.media.video.VideoPrefs

/**
 * Pauses music and radio while a video plays and (optionally) continues them afterwards. Main process,
 * main thread only. The video player process reports through PlayerBridge.ACTION_VIDEO.
 *
 * A pause because of a video is remembered in a small marker (which service, when). The paused player
 * itself keeps the track and position (music) or the station (radio, which reconnects to the live stream).
 * The marker expires after [EXPIRE_MS].
 */
object PlaybackCoordinator {
    const val KIND_MUSIC = "music"
    const val KIND_RADIO = "radio"
    private const val PREFS = "playback_video_marker"
    private const val EXPIRE_MS = 30 * 60 * 1000L
    private const val FOCUS_WINDOW_MS = 5000L

    class Participant(
        val isPlaying: () -> Boolean,
        val pause: () -> Unit,
        val resume: () -> Unit,
    )

    private val participants = HashMap<String, Participant>()
    private val lastFocusLoss = HashMap<String, Long>()
    private var videoActive = false

    fun register(kind: String, participant: Participant) {
        participants[kind] = participant
    }

    fun unregister(kind: String) {
        participants.remove(kind)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mark(context: Context, kind: String) {
        prefs(context).edit().putString("kind", kind).putLong("at", System.currentTimeMillis()).apply()
    }

    private fun markedKind(context: Context): String? {
        val p = prefs(context)
        val kind = p.getString("kind", null) ?: return null
        if (System.currentTimeMillis() - p.getLong("at", 0L) > EXPIRE_MS) return null
        return kind
    }

    /** True while a service should stay alive because it waits for the end of a video */
    fun isWaiting(context: Context, kind: String): Boolean = markedKind(context) == kind

    /** The service lost audio focus (the video grabs it before the broadcast may arrive) */
    fun onFocusLoss(context: Context, kind: String) {
        lastFocusLoss[kind] = SystemClock.elapsedRealtime()
        if (videoActive) mark(context, kind)
    }

    fun onVideoStarted(context: Context) {
        videoActive = true
        val now = SystemClock.elapsedRealtime()
        // a snapshot: pausing a player may re-enter the coordinator while it is iterated
        for ((kind, p) in participants.entries.map { it.key to it.value }) {
            if (p.isPlaying()) {
                runCatching { p.pause() }
                mark(context, kind)
            } else if (now - (lastFocusLoss[kind] ?: -FOCUS_WINDOW_MS * 2) < FOCUS_WINDOW_MS) {
                mark(context, kind)
            }
        }
    }

    /**
     * The video (live TV) lost audio focus to something else that now plays: forget the pause marker
     * instead of resuming, so the one that took over is not disturbed.
     */
    fun onVideoSuperseded(context: Context) {
        videoActive = false
        prefs(context).edit().clear().apply()
    }

    fun onVideoStopped(context: Context) {
        videoActive = false
        val kind = markedKind(context)
        prefs(context).edit().clear().apply()
        if (kind == null || !VideoPrefs.resumeAfterVideo(context)) return
        participants[kind]?.let { runCatching { it.resume() } }
    }
}
