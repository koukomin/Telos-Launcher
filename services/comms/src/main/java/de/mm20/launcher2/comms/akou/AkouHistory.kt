package de.mm20.launcher2.comms.akou

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AkouHistory(context: Context) {
    private val preferences = context.getSharedPreferences("akou_recognition", Context.MODE_PRIVATE).also {
        // The old AudD credential is no longer used by keyless fingerprint recognition.
        it.edit().remove("token").apply()
    }
    fun read(): List<RecognizedSong> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RecognizedSong(item.getString("title"), item.getString("artist"), item.optString("album"), item.getLong("at"))
        }
    }.getOrDefault(emptyList())
    fun add(song: RecognizedSong): List<RecognizedSong> {
        val entries = (listOf(song) + read()).take(100)
        val array = JSONArray()
        entries.forEach { entry -> array.put(JSONObject().put("title", entry.title).put("artist", entry.artist)
            .put("album", entry.album).put("at", entry.recognizedAt)) }
        preferences.edit().putString("history", array.toString()).apply()
        return entries
    }
    fun clear() { preferences.edit().remove("history").apply() }
}
