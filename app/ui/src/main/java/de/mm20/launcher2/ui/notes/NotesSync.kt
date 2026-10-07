package de.mm20.launcher2.ui.notes

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
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
    var ncPassword: String
        get() = prefs.getString("nc_pass", "") ?: ""
        set(v) = prefs.edit().putString("nc_pass", v).apply()
    var lastSync: Long
        get() = prefs.getLong("last", 0)
        set(v) = prefs.edit().putLong("last", v).apply()
    val nextcloudReady get() = ncUrl.isNotBlank() && ncUser.isNotBlank() && ncPassword.isNotBlank()
}

data class SyncResult(val uploaded: Int, val downloaded: Int, val error: String? = null)

/**
 * Two way sync of the local notes with a folder of Markdown files (any Storage Access Framework folder:
 * an Obsidian vault, Logseq, Syncthing, a cloud provider's folder) and with the Nextcloud Notes app.
 * Whichever side was changed last wins, there is no merge inside a note.
 */
class NotesSync(private val context: Context, private val store: NotesStore) {

    val settings = NotesSyncSettings(context)
    private val http = OkHttpClient()

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        var up = 0; var down = 0; var err: String? = null
        settings.folderUri?.let {
            try { syncFolder(Uri.parse(it)).also { r -> up += r.uploaded; down += r.downloaded } } catch (e: Exception) { err = e.message ?: "folder" }
        }
        if (settings.nextcloudReady) {
            try { syncNextcloud().also { r -> up += r.uploaded; down += r.downloaded } } catch (e: Exception) { err = e.message ?: "nextcloud" }
        }
        settings.lastSync = System.currentTimeMillis()
        SyncResult(up, down, err)
    }

    // ---- folder ----------------------------------------------------------------------------

    private class RemoteFile(val docId: String, val name: String, val modified: Long)

    private fun listFolder(tree: Uri): List<RemoteFile> {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = mutableListOf<RemoteFile>()
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = c.getString(1)
                if (name.endsWith(".md", true)) out += RemoteFile(c.getString(0), name, c.getLong(2))
            }
        }
        return out
    }

    private fun fileName(n: Note): String {
        val base = n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { n.id }
            .replace(Regex("[\\\\/:*?\"<>|\n\r]"), " ").trim().take(60)
        return "$base.md"
    }

    private fun syncFolder(tree: Uri): SyncResult {
        val cr = context.contentResolver
        val files = listFolder(tree).associateBy { it.docId }
        var up = 0; var down = 0
        val seen = mutableSetOf<String>()
        // Notes that were written to the folder before are matched by the document id.
        for (n in store.notes.value.filter { !it.trashed }) {
            val rf = n.remoteId?.let { files[it] }
            if (rf != null) {
                seen += rf.docId
                if (rf.modified > n.modifiedAt + 1000) {
                    val text = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId))?.use { it.readBytes().toString(Charsets.UTF_8) } ?: continue
                    val parsed = NotesImport.parseMarkdown(rf.name.removeSuffix(".md"), text)
                    store.save(n.copy(title = parsed.title, body = parsed.body, labels = parsed.labels, pinned = parsed.pinned, modifiedAt = rf.modified), touch = false)
                    down++
                } else if (n.modifiedAt > rf.modified + 1000) {
                    write(tree, DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId), n); up++
                }
            } else {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val created = DocumentsContract.createDocument(cr, parentUri, "text/markdown", fileName(n)) ?: continue
                write(tree, created, n)
                store.save(n.copy(remoteId = DocumentsContract.getDocumentId(created)), touch = false)
                up++
            }
        }
        // Files that are new in the folder
        val known = store.notes.value.mapNotNull { it.remoteId }.toSet()
        for (rf in files.values) {
            if (rf.docId in known || rf.docId in seen) continue
            val text = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, rf.docId))?.use { it.readBytes().toString(Charsets.UTF_8) } ?: continue
            val p = NotesImport.parseMarkdown(rf.name.removeSuffix(".md"), text)
            store.save(p.copy(remoteId = rf.docId, modifiedAt = rf.modified, createdAt = rf.modified), touch = false)
            down++
        }
        // Notes that were deleted in the folder go to the trash here
        for (n in store.notes.value) {
            if (n.remoteId != null && n.remoteId !in files && !n.trashed) {
                store.save(n.copy(trashed = true, remoteId = null), touch = false)
            }
        }
        return SyncResult(up, down)
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
        val remoteById = (0 until remote.length()).associate { val o = remote.getJSONObject(it); o.getLong("id").toString() to o }
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
                    store.save(n.copy(trashed = true, ncId = null), touch = false); continue
                }
                if (n.trashed) continue
                http.newCall(ncRequest(ncBase()).post(body).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val id = JSONObject(resp.body!!.string()).getLong("id")
                        store.save(n.copy(ncId = id.toString()), touch = false); up++
                    }
                }
            } else {
                val rm = r.getLong("modified") * 1000
                if (n.trashed) {
                    http.newCall(ncRequest("${ncBase()}/$tag").delete().build()).execute().close()
                    store.deleteForever(n.id)
                } else if (rm > n.modifiedAt + 1000) {
                    store.save(n.copy(title = r.optString("title"), body = r.optString("content"), pinned = r.optBoolean("favorite"),
                        labels = r.optString("category").let { if (it.isBlank()) emptyList() else listOf(it) }, modifiedAt = rm), touch = false)
                    down++
                } else if (n.modifiedAt > rm + 1000) {
                    http.newCall(ncRequest("${ncBase()}/$tag").put(body).build()).execute().close(); up++
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
