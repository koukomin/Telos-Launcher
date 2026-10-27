package de.mm20.launcher2.comms.media.video.trakt

import de.mm20.launcher2.comms.stremio.LibraryEntry
import de.mm20.launcher2.comms.stremio.WatchProgress
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Remote Trakt state stays separate from the Stremio account mirror; it is combined only for presentation. */
data class TraktLibraryState(
    val library: List<LibraryEntry> = emptyList(),
    val watching: List<WatchProgress> = emptyList(),
    val refreshedAt: Long = 0,
)

object TraktLibraryParser {
    private val imdbId = Regex("tt[0-9]{5,12}")
    private val numericId = Regex("(trakt|tmdb):([0-9]{1,18})")

    fun identity(ids: JSONObject?): String? {
        ids ?: return null
        ids.optString("imdb").takeIf { imdbId.matches(it) }?.let { return it }
        ids.optLong("trakt").takeIf { it > 0 }?.let { return "trakt:$it" }
        return ids.optLong("tmdb").takeIf { it > 0 }?.let { "tmdb:$it" }
    }

    fun identifiers(id: String): JSONObject? {
        if (imdbId.matches(id)) return JSONObject().put("imdb", id)
        val match = numericId.matchEntire(id) ?: return null
        val number = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return JSONObject().put(match.groupValues[1], number)
    }

    private fun millis(value: String?): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)

    fun watchlist(raw: String, type: String): List<LibraryEntry> {
        require(type == "movie" || type == "series")
        val array = JSONArray(raw)
        require(array.length() <= 10_000)
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val item = row.optJSONObject(if (type == "movie") "movie" else "show") ?: return@mapNotNull null
            val id = identity(item.optJSONObject("ids")) ?: return@mapNotNull null
            LibraryEntry(type, id, item.optString("title").take(512), null, millis(row.optString("listed_at")))
        }
    }

    /** A playback percentage is not proof that the content was actually watched. */
    fun playback(raw: String): List<WatchProgress> {
        val array = JSONArray(raw)
        require(array.length() <= 10_000)
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val percent = row.optDouble("progress", Double.NaN).takeIf { it.isFinite() && it in 0.0..<100.0 } ?: return@mapNotNull null
            val movie = row.optJSONObject("movie")
            val show = row.optJSONObject("show")
            val item = movie ?: show ?: return@mapNotNull null
            val id = identity(item.optJSONObject("ids")) ?: return@mapNotNull null
            val episode = row.optJSONObject("episode")
            val season = episode?.optInt("season", -1)
            val number = episode?.optInt("number", -1)
            if (movie == null && (season == null || season < 0 || number == null || number <= 0)) return@mapNotNull null
            val video = if (movie != null) id else "$id:$season:$number"
            val runtime = (episode ?: item).optLong("runtime").takeIf { it in 1..525_600 }?.times(60_000) ?: 0
            WatchProgress(if (movie != null) "movie" else "series", id, item.optString("title").take(512), null,
                video, (episode ?: item).optString("title").take(512), millis(row.optString("paused_at")), null,
                positionMs = (runtime * percent / 100).toLong(), durationMs = runtime, resumePercent = percent)
        }.sortedByDescending { it.watchedAt }
    }

    fun encode(state: TraktLibraryState, session: String): String = JSONObject().put("session", session)
        .put("at", state.refreshedAt).put("library", JSONArray().also { a -> state.library.forEach {
            a.put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("at", it.addedAt))
        } }).put("watching", JSONArray().also { a -> state.watching.forEach {
            a.put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("video", it.videoId)
                .put("title", it.videoTitle).put("at", it.watchedAt).put("position", it.positionMs).put("duration", it.durationMs)
                .put("percent", it.resumePercent ?: JSONObject.NULL))
        } }).toString()

    fun decode(raw: String, session: String): TraktLibraryState? {
        val root = JSONObject(raw)
        if (root.optString("session") != session) return null
        val library = root.optJSONArray("library") ?: JSONArray()
        val watching = root.optJSONArray("watching") ?: JSONArray()
        require(library.length() <= 10_000 && watching.length() <= 10_000)
        return TraktLibraryState((0 until library.length()).map { i ->
            val row = library.getJSONObject(i)
            LibraryEntry(row.getString("type"), row.getString("id"), row.getString("name"), null, row.optLong("at"))
        }, (0 until watching.length()).map { i ->
            val row = watching.getJSONObject(i)
            WatchProgress(row.getString("type"), row.getString("id"), row.getString("name"), null, row.getString("video"),
                row.optString("title"), row.optLong("at"), null, row.optLong("position"), row.optLong("duration"),
                resumePercent = row.optDouble("percent", Double.NaN).takeIf { it.isFinite() && it in 0.0..<100.0 })
        }, root.optLong("at"))
    }
}
