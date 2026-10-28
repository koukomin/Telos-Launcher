package de.mm20.launcher2.comms.media.video.trakt

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Replay decisions use exact IDs and event times; an old device must not replace a newer resume. */
internal object TraktReplayPolicy {
    private fun time(value: String) = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)
    private fun matches(ids: JSONObject?, id: String): Boolean {
        val expected = TraktLibraryParser.identifiers(id) ?: return false
        val key = expected.keys().next()
        return ids != null && ids.optString(key) == expected.optString(key)
    }
    fun latestWatchedAt(raw: String, id: String, season: Int?, episode: Int?): Long {
        val items = JSONArray(raw)
        var latest = 0L
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val meta = item.optJSONObject(if (season == null) "movie" else "show") ?: continue
            if (!matches(meta.optJSONObject("ids"), id)) continue
            if (season == null) latest = maxOf(latest, time(item.optString("last_watched_at")))
            else {
                latest = maxOf(latest, time(item.optString("reset_at")))
                val seasons = item.optJSONArray("seasons") ?: continue
                for (s in 0 until seasons.length()) {
                    val row = seasons.optJSONObject(s) ?: continue
                    if (row.optInt("number", -1) != season) continue
                    val episodes = row.optJSONArray("episodes") ?: continue
                    for (e in 0 until episodes.length()) {
                        val entry = episodes.optJSONObject(e) ?: continue
                        if (entry.optInt("number", -1) == episode) latest = maxOf(latest, time(entry.optString("last_watched_at")))
                    }
                }
            }
        }
        return latest
    }
    fun newerPlayback(raw: String, id: String, season: Int?, eventAt: Long): Boolean {
        val items = JSONArray(raw)
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            val meta = row.optJSONObject(if (season == null) "movie" else "show") ?: continue
            if (matches(meta.optJSONObject("ids"), id) && time(row.optString("paused_at")) > eventAt) return true
        }
        return false
    }
}
