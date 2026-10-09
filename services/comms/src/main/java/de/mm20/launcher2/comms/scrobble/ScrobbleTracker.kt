package de.mm20.launcher2.comms.scrobble

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.util.concurrent.Executors

/**
 * Follows the music player and reports to the scrobbling services that are switched on: the track
 * that is playing right now, and a scrobble once half of the track (at most 4 minutes) was played.
 * Scrobbles that cannot be sent (no network) are kept and sent later.
 */
class ScrobbleTracker(private val context: Context, private val player: Player) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()

    private var trackKey: String? = null
    private var track: ScrobbleTrack? = null
    private var playedMs = 0L
    private var lastTick = 0L
    private var nowPlayingSent = false
    private var scrobbled = false

    private val tick = object : Runnable {
        override fun run() {
            accumulate()
            check()
            if (player.isPlaying) handler.postDelayed(this, 5000)
        }
    }

    fun attach() {
        player.addListener(this)
        loadTrack(player.currentMediaItem)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
        network.shutdown()
    }

    private fun loadTrack(item: MediaItem?) {
        trackKey = item?.mediaId
        val m = item?.mediaMetadata
        track = if (item != null && !m?.title.isNullOrBlank() && !m?.artist.isNullOrBlank()) {
            ScrobbleTrack(
                artist = m?.artist.toString(),
                title = m?.title.toString(),
                album = m?.albumTitle?.toString().orEmpty(),
                durationSeconds = (player.duration.takeIf { it > 0 } ?: 0L).div(1000).toInt(),
            )
        } else null
        playedMs = 0
        lastTick = 0
        nowPlayingSent = false
        scrobbled = false
    }

    // the saved logins are decrypted with the Keystore: not on every 5 s tick on the main thread
    private var cachedConfig: ScrobbleConfig? = null
    private var cachedAt = 0L

    private fun config(): ScrobbleConfig {
        val now = System.currentTimeMillis()
        cachedConfig?.let { if (now - cachedAt < 30_000L) return it }
        return Scrobblers.load(context).also { cachedConfig = it; cachedAt = now }
    }

    private fun accumulate() {
        val now = System.currentTimeMillis()
        if (lastTick != 0L && player.isPlaying) playedMs += now - lastTick
        lastTick = if (player.isPlaying) now else 0L
    }

    private fun check() {
        val t = track ?: return
        if (!config().any) return
        // the duration is known once playback started
        val durationSeconds = if (t.durationSeconds > 0) t.durationSeconds else (player.duration.takeIf { it > 0 } ?: 0L).div(1000).toInt()
        val withDuration = if (durationSeconds != t.durationSeconds) t.copy(durationSeconds = durationSeconds).also { track = it } else t
        if (!nowPlayingSent && player.isPlaying) {
            nowPlayingSent = true
            send { it.nowPlaying(withDuration) }
        }
        val threshold = minOf(240_000L, withDuration.durationSeconds * 500L)
        if (!scrobbled && withDuration.durationSeconds >= 30 && playedMs >= threshold) {
            scrobbled = true
            val stamped = withDuration.copy(timestampSeconds = System.currentTimeMillis() / 1000 - playedMs / 1000)
            network.execute { de.mm20.launcher2.base.contained("scrobble") { submit(stamped) } }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        accumulate()
        // the threshold may have been reached since the last tick
        if (!isPlaying) check()
        handler.removeCallbacks(tick)
        if (isPlaying) {
            lastTick = System.currentTimeMillis()
            handler.postDelayed(tick, 1500)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        accumulate()
        check()
        loadTrack(mediaItem)
        if (player.isPlaying) {
            lastTick = System.currentTimeMillis()
            handler.postDelayed(tick, 1500)
        }
    }

    // ---- sending (off the main thread) ----

    private fun send(action: (Service) -> Unit) {
        val config = config()
        network.execute {
            for (s in services(config)) runCatching { action(s) }
        }
    }

    /** One service the user switched on */
    private class Service(
        val name: String,
        private val onNowPlaying: (ScrobbleTrack) -> Unit,
        private val onScrobble: (ScrobbleTrack) -> Unit,
    ) {
        fun nowPlaying(t: ScrobbleTrack) = onNowPlaying(t)
        fun scrobble(t: ScrobbleTrack) = onScrobble(t)
    }

    private fun services(c: ScrobbleConfig): List<Service> = buildList {
        if (c.lastfmEnabled && c.lastfmSession.isNotBlank()) {
            val api = LastFm(c.lastfmKey, c.lastfmSecret, c.lastfmSession)
            add(Service("lastfm", api::nowPlaying, api::scrobble))
        }
        if (c.librefmEnabled && c.librefmPasswordHash.isNotBlank()) {
            val api = LibreFm(c.librefmUser, c.librefmPasswordHash)
            add(Service("librefm", api::nowPlaying, api::scrobble))
        }
        if (c.listenbrainzEnabled && c.listenbrainzToken.isNotBlank()) {
            val api = ListenBrainz(c.listenbrainzToken, c.listenbrainzServer)
            add(Service("listenbrainz", api::nowPlaying, api::scrobble))
        }
    }

    private fun submit(t: ScrobbleTrack) {
        val config = Scrobblers.load(context)
        val list = services(config)
        // earlier scrobbles that could not be sent go first
        val old = Scrobblers.queue(context)
        val keep = org.json.JSONArray()
        // a service that failed once (offline) is not asked again for the rest of this round
        val failed = mutableSetOf<String>()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            val s = list.firstOrNull { it.name == o.optString("s") }
            if (s == null || s.name in failed) { keep.put(o); continue } // service switched off or offline: keep it for later
            val past = ScrobbleTrack(o.optString("a"), o.optString("t"), o.optString("b"), o.optInt("l"), o.optLong("ts"))
            if (runCatching { s.scrobble(past) }.isFailure) { failed += s.name; keep.put(o) }
        }
        Scrobblers.setQueue(context, keep)
        for (s in list) {
            if (s.name in failed || runCatching { s.scrobble(t) }.isFailure) Scrobblers.enqueue(context, s.name, t)
        }
    }
}
