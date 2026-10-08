package de.mm20.launcher2.ui.notes

import android.content.Context
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.Backupable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One note. [remoteId] is the document of the note in the synced folder, [ncId] its id in Nextcloud Notes. */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val labels: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null,
    val ncId: String? = null,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("body", body); put("color", color)
        put("pinned", pinned); put("archived", archived); put("trashed", trashed)
        put("labels", JSONArray(labels)); put("created", createdAt); put("modified", modifiedAt)
        remoteId?.let { put("remoteId", it) }
        ncId?.let { put("ncId", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Note {
            val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            return Note(
                id = o.getString("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                color = o.optInt("color"),
                pinned = o.optBoolean("pinned"),
                archived = o.optBoolean("archived"),
                trashed = o.optBoolean("trashed"),
                labels = labels,
                createdAt = o.optLong("created", System.currentTimeMillis()),
                modifiedAt = o.optLong("modified", System.currentTimeMillis()),
                remoteId = if (o.has("remoteId")) o.getString("remoteId") else null,
                ncId = if (o.has("ncId")) o.getString("ncId") else null,
            )
        }
    }
}

/**
 * The notes of Telos Notes, one JSON file per note in the app's private storage. This is the single
 * source of truth: the sync targets only copy to and from it.
 */
class NotesStore(private val context: Context) : Backupable {

    override val group: BackupGroup = BackupGroup.Notes

    private val dir get() = File(context.filesDir, "notes").also { it.mkdirs() }
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes
    private var loaded = false
    private val lock = Any()

    private fun loadLocked() {
        if (loaded) return
        loaded = true
        _notes.value = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull {
            try { Note.fromJson(JSONObject(it.readText())) } catch (e: Exception) { null }
        }.sortedByDescending { it.modifiedAt }
    }

    fun load() = synchronized(lock) { loadLocked() }

    fun get(id: String): Note? = synchronized(lock) { loadLocked(); _notes.value.firstOrNull { it.id == id } }

    /** Saves a note as it is. Pass [touch] to set the modification time to now. */
    fun save(note: Note, touch: Boolean = true) = synchronized(lock) {
        loadLocked()
        val n = if (touch) note.copy(modifiedAt = System.currentTimeMillis()) else note
        File(dir, "${n.id}.json").writeText(n.toJson().toString())
        _notes.value = (_notes.value.filter { it.id != n.id } + n).sortedByDescending { it.modifiedAt }
    }

    /**
     * Saves a note that was edited on a copy that may be older than the stored note: the links to the
     * sync targets are taken from the stored note, a sync may have created them while the note was open.
     */
    fun saveEdited(note: Note, touch: Boolean = true) = synchronized(lock) {
        loadLocked()
        val cur = _notes.value.firstOrNull { it.id == note.id }
        save(note.copy(remoteId = cur?.remoteId ?: note.remoteId, ncId = cur?.ncId ?: note.ncId), touch)
    }

    /** Changes the stored note with [f], but only if it still has the modification time [expected] (it was not edited meanwhile). */
    fun update(id: String, expected: Long? = null, f: (Note) -> Note) = synchronized(lock) {
        loadLocked()
        val cur = _notes.value.firstOrNull { it.id == id } ?: return@synchronized
        if (expected != null && cur.modifiedAt != expected) return@synchronized
        save(f(cur), touch = false)
    }

    fun deleteForever(id: String) = synchronized(lock) {
        loadLocked()
        File(dir, "$id.json").delete()
        _notes.value = _notes.value.filter { it.id != id }
    }

    fun emptyTrash() = synchronized(lock) {
        loadLocked()
        _notes.value.filter { it.trashed }.forEach { deleteForever(it.id) }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        load()
        val out = File(toDir, "notes").also { it.mkdirs() }
        dir.listFiles().orEmpty().forEach { it.copyTo(File(out, it.name), overwrite = true) }
    }

    /** Restores the notes. A note that exists on both sides stays as the newer one. */
    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        load()
        val src = File(fromDir, "notes")
        src.listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
            val n = try { Note.fromJson(JSONObject(f.readText())) } catch (e: Exception) { return@forEach }
            // the id becomes a file name
            if (!Regex("[A-Za-z0-9_-]+").matches(n.id)) return@forEach
            val existing = get(n.id)
            // links to the sync targets of the other phone mean nothing here: the sync would treat the note as deleted there
            if (existing == null) save(n.copy(remoteId = null, ncId = null), touch = false)
            else if (existing.modifiedAt < n.modifiedAt) save(n.copy(remoteId = existing.remoteId, ncId = existing.ncId), touch = false)
        }
    }

    companion object {
        val Colors = listOf(0x00000000, 0xFFF28B82.toInt(), 0xFFFBBC04.toInt(), 0xFFFFF475.toInt(), 0xFFCCFF90.toInt(),
            0xFFA7FFEB.toInt(), 0xFFCBF0F8.toInt(), 0xFFAECBFA.toInt(), 0xFFD7AEFB.toInt(), 0xFFFDCFE8.toInt())
    }
}
