package de.mm20.launcher2.comms.stremio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A title the user saved to the library */
data class LibraryEntry(val type: String, val id: String, val name: String, val poster: String?, val addedAt: Long)

/** What was watched last of a title (the "continue watching" shelf), and which stream was used */
data class WatchProgress(
    val type: String,
    val id: String,
    val name: String,
    val poster: String?,
    val videoId: String,
    val videoTitle: String,
    val watchedAt: Long,
    /** [streamKey] of the stream that was played, so that it is offered first next time */
    val stream: String?,
)

/** A stable identity of a stream, to remember which one was played */
fun StreamItem.streamKey(): String = "$addonName|${url ?: infoHash ?: ytId ?: externalUrl.orEmpty()}"

/**
 * The library and the watch history of the Stremio space. Everything stays on this phone (filesDir/stremio_library.json);
 * nothing is synced anywhere.
 */
class StremioLibrary(context: Context) {
    private val file = File(context.applicationContext.filesDir, "stremio_library.json")
    private val lock = Any()

    private val _library = MutableStateFlow<List<LibraryEntry>>(emptyList())
    private val _watching = MutableStateFlow<List<WatchProgress>>(emptyList())

    val library: StateFlow<List<LibraryEntry>> get() = _library
    val continueWatching: StateFlow<List<WatchProgress>> get() = _watching

    init {
        synchronized(lock) {
            runCatching {
                val root = JSONObject(file.readText())
                _library.value = root.optJSONArray("library").objs().map {
                    LibraryEntry(it.optString("type"), it.optString("id"), it.optString("name"), it.optString("poster").takeIf { p -> p.isNotBlank() }, it.optLong("at"))
                }
                _watching.value = root.optJSONArray("watching").objs().map {
                    WatchProgress(
                        it.optString("type"), it.optString("id"), it.optString("name"), it.optString("poster").takeIf { p -> p.isNotBlank() },
                        it.optString("video"), it.optString("videoTitle"), it.optLong("at"), it.optString("stream").takeIf { s -> s.isNotBlank() },
                    )
                }
            }
        }
    }

    private fun JSONArray?.objs(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun save() = synchronized(lock) {
        runCatching {
            val root = JSONObject()
            root.put("library", JSONArray().also { a ->
                _library.value.forEach { a.put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("poster", it.poster.orEmpty()).put("at", it.addedAt)) }
            })
            root.put("watching", JSONArray().also { a ->
                _watching.value.forEach {
                    a.put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("poster", it.poster.orEmpty())
                        .put("video", it.videoId).put("videoTitle", it.videoTitle).put("at", it.watchedAt).put("stream", it.stream.orEmpty()))
                }
            })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        }
        Unit
    }

    fun isSaved(id: String): Boolean = _library.value.any { it.id == id }

    /** Saves the title, or removes it when it is saved already */
    fun toggle(type: String, id: String, name: String, poster: String?) {
        _library.value = if (isSaved(id)) _library.value.filterNot { it.id == id }
        else listOf(LibraryEntry(type, id, name, poster, System.currentTimeMillis())) + _library.value
        save()
    }

    fun markWatched(p: WatchProgress) {
        _watching.value = (listOf(p) + _watching.value.filterNot { it.id == p.id }).take(MAX_HISTORY)
        save()
    }

    /**
     * Brings in what a Stremio account has: titles saved there are added, titles removed there (after they were saved here)
     * are removed, and the continue watching shelf takes the newer entry of each title. [removedIds] maps the id of a title
     * removed in the account to the time of its removal.
     */
    fun mergeRemote(saved: List<LibraryEntry>, watching: List<WatchProgress>, removedIds: Map<String, Long>) {
        val current = _library.value
        val kept = current.filterNot { e -> removedIds[e.id]?.let { it > e.addedAt } == true }
        _library.value = kept + saved.filter { s -> kept.none { it.id == s.id } && removedIds[s.id] == null }
        val local = _watching.value
        val merged = ArrayList(local)
        for (w in watching) {
            val i = merged.indexOfFirst { it.id == w.id }
            if (i < 0) merged += w
            else if (w.watchedAt > merged[i].watchedAt) merged[i] = w.copy(stream = merged[i].stream.takeIf { merged[i].videoId == w.videoId })
        }
        _watching.value = merged.sortedByDescending { it.watchedAt }.take(MAX_HISTORY)
        save()
    }

    fun lastStream(id: String): String? = _watching.value.firstOrNull { it.id == id }?.stream

    fun removeProgress(id: String) {
        _watching.value = _watching.value.filterNot { it.id == id }
        save()
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}
