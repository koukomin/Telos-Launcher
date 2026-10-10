package de.mm20.launcher2.comms.scrobble

import android.content.Context
import org.json.JSONArray

/** One service the user switched on */
internal class ScrobbleService(
    val name: String,
    private val onNowPlaying: (ScrobbleTrack) -> Unit,
    private val onScrobble: (ScrobbleTrack) -> Unit,
) {
    fun nowPlaying(t: ScrobbleTrack) = onNowPlaying(t)
    fun scrobble(t: ScrobbleTrack) = onScrobble(t)
}

/** Sends to all switched on services. Call these off the main thread. */
internal object ScrobbleSender {

    fun services(c: ScrobbleConfig): List<ScrobbleService> = buildList {
        if (c.lastfmEnabled && c.lastfmSession.isNotBlank()) {
            val api = LastFm(c.lastfmKey, c.lastfmSecret, c.lastfmSession)
            add(ScrobbleService("lastfm", api::nowPlaying, api::scrobble))
        }
        if (c.librefmEnabled && c.librefmPasswordHash.isNotBlank()) {
            val api = LibreFm(c.librefmUser, c.librefmPasswordHash)
            add(ScrobbleService("librefm", api::nowPlaying, api::scrobble))
        }
        if (c.listenbrainzEnabled && c.listenbrainzToken.isNotBlank()) {
            val api = ListenBrainz(c.listenbrainzToken, c.listenbrainzServer)
            add(ScrobbleService("listenbrainz", api::nowPlaying, api::scrobble))
        }
    }

    fun nowPlaying(context: Context, t: ScrobbleTrack) {
        for (s in services(Scrobblers.load(context))) runCatching { s.nowPlaying(t) }
    }

    /** Sends the scrobbles that could not be sent before. Returns the services that failed. */
    private fun flush(context: Context, list: List<ScrobbleService>): Set<String> {
        val old = Scrobblers.queue(context)
        if (old.length() == 0) return emptySet()
        val keep = JSONArray()
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
        return failed
    }

    /** Retries the queue only */
    fun flush(context: Context) {
        val list = services(Scrobblers.load(context))
        if (list.isNotEmpty()) flush(context, list)
    }

    fun submit(context: Context, t: ScrobbleTrack) {
        val list = services(Scrobblers.load(context))
        // earlier scrobbles that could not be sent go first
        val failed = flush(context, list)
        for (s in list) {
            if (s.name in failed || runCatching { s.scrobble(t) }.isFailure) Scrobblers.enqueue(context, s.name, t)
        }
    }
}
