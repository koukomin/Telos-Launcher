package de.mm20.launcher2.ui.notes

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import de.mm20.launcher2.comms.remote.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Where the sync runs. The settings are kept in the app's private preferences. */
class NotesSyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences("telos_notes_sync", Context.MODE_PRIVATE)
    var folderUri: String?
        get() = prefs.getString("folder", null)
        set(v) = prefs.edit().putString("folder", v).apply()
    var ncUrl: String
        get() = prefs.getString("nc_url", "") ?: ""
        set(v) = prefs.edit().putString("nc_url", v).apply()
    var ncUser: String
        get() = prefs.getString("nc_user", "") ?: ""
        set(v) = prefs.edit().putString("nc_user", v).apply()
    /**
     * The Nextcloud password is stored encrypted with the Android Keystore ([SecretBox]). A plain value written
     * by an older version is encrypted on the first read and the plain value is deleted. If the Keystore is not
     * usable the password is not written to disk at all.
     */
    var ncPassword: String
        get() {
            val legacy = prefs.getString("nc_pass", null)
            if (legacy != null) {
                if (legacy.isNotEmpty()) {
                    val enc = try { SecretBox.encrypt(legacy) } catch (e: Exception) { null }
                    if (enc != null) prefs.edit().putString("nc_pass_enc", enc).remove("nc_pass").apply()
                    else return legacy
                } else prefs.edit().remove("nc_pass").apply()
            }
            val enc = prefs.getString("nc_pass_enc", null).orEmpty()
            return if (enc.isEmpty()) "" else SecretBox.decrypt(enc)
        }
        set(v) {
            val enc = if (v.isEmpty()) "" else try { SecretBox.encrypt(v) } catch (e: Exception) { return }
            prefs.edit().remove("nc_pass").putString("nc_pass_enc", enc).apply()
        }
    var lastSync: Long
        get() = prefs.getLong("last", 0)
        set(v) = prefs.edit().putLong("last", v).apply()
    val nextcloudReady get() = ncUrl.isNotBlank() && ncUser.isNotBlank() && ncPassword.isNotBlank()
}

private const val MAX_DOC_BYTES = 8 * 1024 * 1024

data class SyncResult(val uploaded: Int, val downloaded: Int, val error: String? = null, val skippedDeletes: Int = 0)

/**
 * Two way sync of the local notes with a folder of Markdown files (any Storage Access Framework folder:
 * an Obsidian vault, Logseq, Syncthing, a cloud provider's folder) and with the Nextcloud Notes app.
 * Whichever side was changed last wins, there is no merge inside a note.
 */
class NotesSync(private val context: Context, private val store: NotesStore) {

    val settings = NotesSyncSettings(context)
    private val http = OkHttpClient()
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    /** Reads a document, null if it is larger than [MAX_DOC_BYTES] (a huge file must not take the app down with an OutOfMemoryError). */
    private fun readDoc(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { NotesImport.readLimited(it, MAX_DOC_BYTES) }?.toString(Charsets.UTF_8)

    suspend fun syncAll(): SyncResult = syncMutex.withLock { doSyncAll() }

    private suspend fun doSyncAll(): SyncResult = withContext(Dispatchers.IO) {
        var up = 0; var down = 0; var skipped = 0; var err: String? = null
        settings.folderUri?.let {
            try { syncFolder(Uri.parse(it)).also { r -> up += r.uploaded; down += r.downloaded; skipped += r.skippedDeletes } } catch (e: Exception) { err = e.message ?: "folder" }
        }
        if (settings.nextcloudReady) {
            try { syncNextcloud().also { r -> up += r.uploaded; down += r.downloaded } } catch (e: Exception) { err = e.message ?: "nextcloud" }
        }
        settings.lastSync = System.currentTimeMillis()
        SyncResult(up, down, err, skipped)
    }

    // ---- folder ----------------------------------------------------------------------------

    private class RemoteFile(val docId: String, val name: String, val modified: Long)

    /** Whether the root of the folder can still be queried. An empty listing of an unreachable folder must not delete notes. */
    private fun rootReachable(tree: Uri): Boolean = try {
        context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
            it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
        } ?: false
    } catch (e: Exception) { false }

    private fun listFolder(tree: Uri): List<RemoteFile> {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = mutableListOf<RemoteFile>()
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
            ?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = c.getString(1)
                if (name.endsWith(".md", true)) out += RemoteFile(c.getString(0), name, c.getLong(2))
            }
        } ?: error("folder")
        return out
    }

    private fun fileName(n: Note): String {
        val base = n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { n.id }
            .replace(Regex("[\\\\/:*?\"<>|\n\r]"), " ").trim().take(60)
        return "$base.md"
    }

    private fun syncFolder(tree: Uri): SyncResult {
        val cr = context.contentResolver
        // a failed listing throws, the folder is then left alone
        val listed = listFolder(tree)
        // notes deleted for good here: their file goes too, and is not imported again
        val pending = store.pendingRemoteDeletes()
        for (rf in listed.filter { it.docId in pending }) {
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId)) } catch (e: Exception) { continue }
            store.clearRemoteDelete(rf.docId)
        }
        val reachable = listed.isNotEmpty() || rootReachable(tree)
        // (a file that is already gone needs no deleting)
        pending.filter { id -> listed.none { it.docId == id } }.forEach { if (reachable) store.clearRemoteDelete(it) }
        val files = listed.filter { it.docId !in pending }.associateBy { it.docId }
        var up = 0; var down = 0
        val seen = mutableSetOf<String>()
        val createdIds = mutableSetOf<String>() // documents created by this run, they are not in [files]
        // Notes that were written to the folder before are matched by the document id.
        for (n in store.notes.value.filter { !it.trashed }) {
            val rf = n.remoteId?.let { files[it] }
            if (rf != null) {
                seen += rf.docId
                if (rf.modified > n.modifiedAt + 1000) {
                    val text = readDoc(DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId)) ?: continue
                    val parsed = NotesImport.parseMarkdown(rf.name.removeSuffix(".md"), text)
                    store.update(n.id, expected = n.modifiedAt) { it.copy(title = parsed.title, body = parsed.body, labels = parsed.labels, pinned = parsed.pinned, modifiedAt = rf.modified) }
                    down++
                } else if (n.modifiedAt > rf.modified + 1000) {
                    write(tree, DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId), n); up++
                    // writing sets the modification time of the file to now: without this the next sync would read the file back
                    store.update(n.id, expected = n.modifiedAt) { it.copy(modifiedAt = maxOf(it.modifiedAt, System.currentTimeMillis())) }
                }
            } else if (n.remoteId == null) {
                // (a note whose document is gone was deleted in the folder, that is handled below)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val created = DocumentsContract.createDocument(cr, parentUri, "text/markdown", fileName(n)) ?: continue
                write(tree, created, n)
                val docId = DocumentsContract.getDocumentId(created)
                createdIds += docId
                store.update(n.id, expected = n.modifiedAt) { it.copy(remoteId = docId, modifiedAt = maxOf(it.modifiedAt, System.currentTimeMillis())) }
                up++
            }
        }
        // Files that are new in the folder
        val known = store.notes.value.mapNotNull { it.remoteId }.toSet()
        for (rf in files.values) {
            if (rf.docId in known || rf.docId in seen) continue
            val text = readDoc(DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId)) ?: continue
            val p = NotesImport.parseMarkdown(rf.name.removeSuffix(".md"), text)
            store.save(p.copy(remoteId = rf.docId, modifiedAt = rf.modified, createdAt = rf.modified), touch = false)
            down++
        }
        // Notes that were deleted in the folder go to the trash here, but never because of an empty or failed listing
        val decision = NotesSyncLogic.folderDeletions(store.notes.value, files.keys, createdIds, true, reachable)
        for (id in decision.toTrash) store.update(id) { it.copy(trashed = true, remoteId = null) }
        return SyncResult(up, down, skippedDeletes = decision.skipped)
    }

    private fun write(tree: Uri, doc: Uri, n: Note) {
        context.contentResolver.openOutputStream(doc, "wt")?.use { it.write(NotesImport.toMarkdown(n).toByteArray()) }
    }

    // ---- Nextcloud Notes -------------------------------------------------------------------

    private fun ncBase() = settings.ncUrl.trimEnd('/') + "/index.php/apps/notes/api/v1/notes"

    private fun ncRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", Credentials.basic(settings.ncUser, settings.ncPassword))

    private fun syncNextcloud(): SyncResult {
        val json = "application/json".toMediaType()
        val remote: JSONArray = http.newCall(ncRequest(ncBase()).build()).execute().use {
            if (!it.isSuccessful) error("Nextcloud ${it.code}")
            JSONArray(it.body!!.string())
        }
        // notes deleted for good here are deleted on the server first
        for (id in store.pendingNcDeletes()) {
            val ok = http.newCall(ncRequest("${ncBase()}/$id").delete().build()).execute().use { it.isSuccessful || it.code == 404 }
            if (ok) store.clearNcDelete(id)
        }
        val pendingNc = store.pendingNcDeletes()
        val remoteById = (0 until remote.length()).filter { remote.getJSONObject(it).getLong("id").toString() !in pendingNc }.associate { val o = remote.getJSONObject(it); o.getLong("id").toString() to o }
        var up = 0; var down = 0
        for (n in store.notes.value) {
            val tag = n.ncId
            val r = tag?.let { remoteById[it] }
            val body = JSONObject().apply {
                put("title", n.title); put("content", n.body); put("favorite", n.pinned)
                put("category", n.labels.firstOrNull().orEmpty()); put("modified", n.modifiedAt / 1000)
            }.toString().toRequestBody(json)
            if (r == null) {
                if (tag != null) { // deleted on the server
                    store.update(n.id) { it.copy(trashed = true, ncId = null) }; continue
                }
                if (n.trashed) continue
                http.newCall(ncRequest(ncBase()).post(body).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val created = JSONObject(resp.body!!.string())
                        val id = created.getLong("id").toString()
                        // the server sets the modification time: take it over, or the next sync reads the note back
                        store.update(n.id, expected = n.modifiedAt) { it.copy(ncId = id, modifiedAt = maxOf(it.modifiedAt, created.optLong("modified") * 1000)) }; up++
                    }
                }
            } else {
                val rm = r.getLong("modified") * 1000
                if (n.trashed) {
                    // a trashed note stays on the server until it is deleted for good
                } else if (rm > n.modifiedAt + 1000) {
                    val category = r.optString("category")
                    store.update(n.id, expected = n.modifiedAt) {
                        it.copy(title = r.optString("title"), body = r.optString("content"), pinned = r.optBoolean("favorite"),
                            // Nextcloud has one category per note: the other labels of the note stay when it is still the first
                            labels = if (category.isBlank()) emptyList() else if (it.labels.firstOrNull() == category) it.labels else listOf(category),
                            modifiedAt = rm)
                    }
                    down++
                } else if (n.modifiedAt > rm + 1000) {
                    http.newCall(ncRequest("${ncBase()}/$tag").put(body).build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val newMod = runCatching { JSONObject(resp.body!!.string()).optLong("modified") * 1000 }.getOrDefault(0)
                            store.update(n.id, expected = n.modifiedAt) { it.copy(modifiedAt = maxOf(it.modifiedAt, newMod)) }
                            up++
                        }
                    }
                }
            }
        }
        val known = store.notes.value.mapNotNull { it.ncId }.toSet()
        for ((id, r) in remoteById) {
            if (id in known) continue
            store.save(Note(
                title = r.optString("title"), body = r.optString("content"), pinned = r.optBoolean("favorite"),
                labels = r.optString("category").let { if (it.isBlank()) emptyList() else listOf(it) },
                modifiedAt = r.getLong("modified") * 1000, ncId = id,
            ), touch = false)
            down++
        }
        return SyncResult(up, down)
    }
}
