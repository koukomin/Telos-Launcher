package de.mm20.launcher2.comms.youtube

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Local library contains durable provider URLs; signed stream URLs are resolved again at playback. */
class YouTubeLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("youtube_library", Context.MODE_PRIVATE)
    init {
        // Preserve the library written by the first implementation on main.
        val legacy = context.getSharedPreferences("telos_youtube_library", Context.MODE_PRIVATE).getString("lib", null)
        if (!preferences.contains("favorites") && legacy != null) runCatching {
            val data = JSONObject(legacy)
            fun converted(array: JSONArray?): JSONArray = JSONArray().apply {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    item.put("thumbnail", item.optString("thumb")); item.put("duration", item.optLong("dur")); put(item)
                }
            }
            val lists = JSONObject()
            val array = data.optJSONArray("playlists")
            if (array != null) for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                lists.put(item.optString("name"), converted(item.optJSONArray("songs")))
            }
            preferences.edit().putString("favorites", converted(data.optJSONArray("favorites")).toString())
                .putString("playlists", lists.toString()).apply()
        }
    }
    val favorites = MutableStateFlow(readEntries("favorites"))
    val playlists = MutableStateFlow(readPlaylists())
    val lastMusic = MutableStateFlow(readEntries("last_music").firstOrNull())
    val lastVideo = MutableStateFlow(readEntries("last_video").firstOrNull())

    fun remember(entry: YouTubeEntry, music: Boolean) {
        val target = if (music) lastMusic else lastVideo
        target.value = entry
        preferences.edit().putString(if (music) "last_music" else "last_video", encode(listOf(entry)).toString()).apply()
    }
    fun favorite(entry: YouTubeEntry) {
        val old = favorites.value
        favorites.value = if (old.any { it.url == entry.url }) old.filterNot { it.url == entry.url } else old + entry
        preferences.edit().putString("favorites", encode(favorites.value).toString()).apply()
    }
    fun add(name: String, entry: YouTubeEntry) {
        val clean = name.trim().take(120)
        if (clean.isEmpty()) return
        val entries = playlists.value[clean].orEmpty()
        playlists.value = playlists.value + (clean to (entries + entry).distinctBy { it.url })
        savePlaylists()
    }
    fun remove(name: String, entry: YouTubeEntry) {
        playlists.value = playlists.value + (name to playlists.value[name].orEmpty().filterNot { it.url == entry.url })
        savePlaylists()
    }
    fun delete(name: String) { playlists.value = playlists.value - name; savePlaylists() }
    private fun savePlaylists() {
        val data = JSONObject()
        playlists.value.forEach { (name, entries) -> data.put(name, encode(entries)) }
        preferences.edit().putString("playlists", data.toString()).apply()
    }
    private fun readEntries(key: String) = runCatching { decode(JSONArray(preferences.getString(key, "[]"))) }.getOrDefault(emptyList())
    private fun readPlaylists(): Map<String, List<YouTubeEntry>> = runCatching {
        val data = JSONObject(preferences.getString("playlists", "{}") ?: "{}")
        data.keys().asSequence().associateWith { decode(data.getJSONArray(it)) }
    }.getOrDefault(emptyMap())
    private fun encode(entries: List<YouTubeEntry>) = JSONArray().apply {
        entries.forEach { entry -> put(JSONObject().apply {
            put("url", entry.url); put("title", entry.title); put("artist", entry.artist)
            put("thumbnail", entry.thumbnail); put("duration", entry.durationSeconds)
        }) }
    }
    private fun decode(array: JSONArray): List<YouTubeEntry> = (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        YouTubeEntry(item.getString("url"), item.getString("title"), item.optString("artist"),
            item.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" }, item.optLong("duration"))
    }
}
