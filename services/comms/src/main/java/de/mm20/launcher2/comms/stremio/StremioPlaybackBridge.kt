package de.mm20.launcher2.comms.stremio

import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Only the main process touches account persistence and credentials, including isolated playback. */
object StremioPlaybackBridge : KoinComponent {
    private val account: StremioAccount by inject()

    fun decode(intent: Intent): Pair<WatchProgress, String>? {
        val id = intent.getStringExtra("meta_id")?.takeIf { it.length in 1..512 } ?: return null
        val video = intent.getStringExtra("video_id")?.takeIf { it.length in 1..512 } ?: return null
        val session = intent.getStringExtra("session")?.takeIf { it.length in 1..128 } ?: return null
        val duration = intent.getLongExtra("duration", 0).takeIf { it in 1..31_536_000_000L } ?: return null
        val position = intent.getLongExtra("position", 0).coerceIn(0, duration)
        val time = intent.getLongExtra("watched_at", 0).takeIf { it > 0 && it <= System.currentTimeMillis() + 60_000 } ?: return null
        val ids = intent.getStringArrayListExtra("video_ids").orEmpty()
        val safeIds = ids.takeIf { it.size <= 10_000 && it.sumOf(String::length) <= 64_000 && it.all { id -> id.length <= 512 } }.orEmpty()
        return WatchProgress(
            intent.getStringExtra("type").orEmpty().take(64), id, intent.getStringExtra("name").orEmpty().take(512),
            intent.getStringExtra("poster")?.take(4096), video, intent.getStringExtra("video_title").orEmpty().take(512),
            time, intent.getStringExtra("stream")?.take(4096), position, duration,
            intent.getLongExtra("time_watched", 0).coerceIn(0, duration * 100),
            intent.getBooleanExtra("completed", false), safeIds,
            nextVideoId = intent.getStringExtra("next_video_id")?.takeIf { it.length in 1..512 && it in safeIds },
        ) to session
    }

    suspend fun record(progress: WatchProgress, session: String): Boolean = account.recordPlayback(progress, session)
    suspend fun upload(progress: WatchProgress, session: String, force: Boolean) = account.playbackProgress(progress, session, force)
}
