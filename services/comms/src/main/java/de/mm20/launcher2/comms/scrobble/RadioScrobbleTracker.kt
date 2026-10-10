package de.mm20.launcher2.comms.scrobble

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.util.concurrent.Executors

/** Works out artist and title of the track a radio station announces in its stream metadata. */
object RadioTitle {
    /**
     * Returns artist to title, or null when the announcement is not a track: empty, the station name
     * (jingle, ident, ad break) or without a recognisable artist.
     */
    fun parse(title: String?, artist: String?, stationName: String): Pair<String, String>? {
        val t = title?.trim().orEmpty()
        val station = stationName.trim()
        if (t.isEmpty() || t.equals(station, ignoreCase = true)) return null
        val a = artist?.trim().orEmpty()
        if (a.isNotEmpty() && !a.equals(station, ignoreCase = true)) return a to t
        for (separator in listOf(" - ", " – ", " — ")) {
            val i = t.indexOf(separator)
            if (i <= 0) continue
            val artistPart = t.substring(0, i).trim()
            val titlePart = t.substring(i + separator.length).trim()
            if (artistPart.isEmpty() || titlePart.isEmpty()) return null
            if (artistPart.equals(station, ignoreCase = true) || titlePart.equals(station, ignoreCase = true)) return null
            return artistPart to titlePart
        }
        return null
    }
}

/**
 * Follows the radio player: "now playing" when a station announces a track, and a scrobble once that
 * track was heard for a minute (a stream has no known length). The same track is never sent twice
 * in a row. Nothing happens unless radio scrobbling is switched on and a service is signed in.
 */
class RadioScrobbleTracker(private val context: Context, private val player: Player) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor()

    private var key: String? = null
    private var track: ScrobbleTrack? = null
    private var playedMs = 0L
    private var lastTick = 0L
    private var nowPlayingSent = false
    private var scrobbled = false
    private var lastScrobbledKey: String? = null
    private var lastScrobbledAt = 0L

    private var cachedConfig: ScrobbleConfig? = null
    private var cachedAt = 0L

    // decrypting the logins is not done on every tick of the main thread
    private fun config(): ScrobbleConfig {
        val now = System.currentTimeMillis()
        cachedConfig?.let { if (now - cachedAt < 30_000L) return it }
        return Scrobblers.load(context).also { cachedConfig = it; cachedAt = now }
    }

    private val tick = object : Runnable {
        override fun run() {
            accumulate()
            check()
            if (player.isPlaying) handler.postDelayed(this, 5000)
        }
    }

    fun attach() {
        player.addListener(this)
        update(player.mediaMetadata)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
        network.shutdown()
    }

    private fun accumulate() {
        val now = System.currentTimeMillis()
        if (lastTick != 0L && player.isPlaying) playedMs += now - lastTick
        lastTick = if (player.isPlaying) now else 0L
    }

    private fun update(metadata: MediaMetadata) {
        val station = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val parsed = RadioTitle.parse(metadata.title?.toString(), metadata.artist?.toString(), station)
        if (parsed == null) {
            key = null
            track = null
            return
        }
        val newKey = "${player.currentMediaItem?.mediaId}|${parsed.first}|${parsed.second}"
        if (newKey == key) return // the station repeats its announcement
        key = newKey
        track = ScrobbleTrack(parsed.first, parsed.second, "", 0, player = "Telos Radio")
        playedMs = 0
        lastTick = if (player.isPlaying) System.currentTimeMillis() else 0L
        nowPlayingSent = false
        scrobbled = false
        if (player.isPlaying) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 1500)
        }
    }

    private fun check() {
        val t = track ?: return
        val cfg = config()
        if (!cfg.radioEnabled || !cfg.any) return
        if (!nowPlayingSent && player.isPlaying) {
            nowPlayingSent = true
            network.execute {
                de.mm20.launcher2.base.contained("scrobble") {
                    ScrobbleSender.nowPlaying(context, t)
                    ScrobbleSender.flush(context)
                }
            }
        }
        if (!scrobbled && playedMs >= MIN_PLAYED_MS) {
            scrobbled = true
            val now = System.currentTimeMillis()
            if (key == lastScrobbledKey && now - lastScrobbledAt < REPEAT_WINDOW_MS) return
            lastScrobbledKey = key
            lastScrobbledAt = now
            val stamped = t.copy(timestampSeconds = now / 1000 - playedMs / 1000)
            network.execute { de.mm20.launcher2.base.contained("scrobble") { ScrobbleSender.submit(context, stamped) } }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        accumulate()
        update(mediaMetadata)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // another station: the old announcement does not count any more
        key = null
        track = null
        update(player.mediaMetadata)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        accumulate()
        if (!isPlaying) check()
        handler.removeCallbacks(tick)
        if (isPlaying) {
            lastTick = System.currentTimeMillis()
            handler.postDelayed(tick, 1500)
        }
    }

    private companion object {
        const val MIN_PLAYED_MS = 60_000L
        const val REPEAT_WINDOW_MS = 10 * 60 * 1000L
    }
}
