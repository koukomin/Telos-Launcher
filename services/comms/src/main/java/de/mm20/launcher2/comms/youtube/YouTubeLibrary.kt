package de.mm20.launcher2.comms.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class YouTubePlaylist(val id: String, val name: String, val songs: List<YouTubeEntry>)

/** Local favorites and playlists for YouTube Music. Not a Google account library. */
class YouTubeLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_youtube_library", Context.MODE_PRIVATE)

    fun favorites(): List<YouTubeEntry> = read().optJSONArray("favorites").toEntries()

    fun isFavorite(url: String): Boolean = favorites().any { it.url == url }

    fun toggleFavorite(entry: YouTubeEntry): Boolean {
        val cur = favorites().toMutableList()
        val on = cur.none { it.url == entry.url }
        if (on) cur.add(0, entry) else cur.removeAll { it.url == entry.url }
        save(cur, playlists())
        return on
    }

    fun playlists(): List<YouTubePlaylist> {
        val arr = read().optJSONArray("playlists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            YouTubePlaylist(o.optString("id"), o.optString("name"), o.optJSONArray("songs").toEntries())
        }
    }

    fun createPlaylist(name: String, first: YouTubeEntry? = null): YouTubePlaylist {
        val p = YouTubePlaylist(UUID.randomUUID().toString(), name.trim().ifBlank { "Playlist" }, listOfNotNull(first))
        save(favorites(), playlists() + p)
        return p
    }

    fun addToPlaylist(id: String, entry: YouTubeEntry) {
        save(favorites(), playlists().map { p ->
            if (p.id != id || p.songs.any { it.url == entry.url }) p
            else p.copy(songs = p.songs + entry)
        })
    }

    fun deletePlaylist(id: String) {
        save(favorites(), playlists().filter { it.id != id })
    }

    private fun read(): JSONObject = runCatching { JSONObject(prefs.getString("lib", "{}")!!) }.getOrDefault(JSONObject())

    private fun save(fav: List<YouTubeEntry>, lists: List<YouTubePlaylist>) {
        val f = JSONArray(); fav.forEach { f.put(it.toJson()) }
        val p = JSONArray()
        lists.forEach { pl ->
            val songs = JSONArray(); pl.songs.forEach { songs.put(it.toJson()) }
            p.put(JSONObject().put("id", pl.id).put("name", pl.name).put("songs", songs))
        }
        prefs.edit().putString("lib", JSONObject().put("favorites", f).put("playlists", p).toString()).apply()
    }

    private fun JSONArray?.toEntries(): List<YouTubeEntry> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { o ->
            val j = optJSONObject(o) ?: return@mapNotNull null
            youtubeEntryFromJson(j)
        }
    }
}

fun YouTubeEntry.toJson(): JSONObject = JSONObject()
    .put("url", url).put("title", title).put("artist", artist)
    .put("thumb", thumbnail.orEmpty()).put("dur", durationSeconds)

fun youtubeEntryFromJson(o: JSONObject): YouTubeEntry = YouTubeEntry(
    o.optString("url"), o.optString("title"), o.optString("artist"),
    o.optString("thumb").ifBlank { null }, o.optLong("dur"),
)
