package de.mm20.launcher2.comms.youtube

import org.json.JSONArray
import org.json.JSONObject

/** Only durable provider addresses are passed to the service; each next stream is freshly resolved. */
object YouTubeQueue {
    fun encode(entries: List<YouTubeEntry>): String = JSONArray().apply {
        entries.take(500).forEach { put(JSONObject().put("url", it.url).put("title", it.title)
            .put("artist", it.artist).put("thumbnail", it.thumbnail.orEmpty()).put("duration", it.durationSeconds)) }
    }.toString()
    fun decode(data: String?): List<YouTubeEntry> = runCatching {
        val array = JSONArray(data ?: "[]")
        (0 until minOf(500, array.length())).map { i ->
            val item = array.getJSONObject(i)
            YouTubeEntry(item.getString("url"), item.optString("title"), item.optString("artist"),
                item.optString("thumbnail").ifBlank { null }, item.optLong("duration"))
        }
    }.getOrDefault(emptyList())
}
