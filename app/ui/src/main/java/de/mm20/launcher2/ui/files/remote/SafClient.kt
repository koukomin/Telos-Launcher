package de.mm20.launcher2.ui.files.remote

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A folder of another app that offers its files to the system (Storage Access Framework): the Google Drive,
 * OneDrive, Dropbox, Nextcloud or other cloud apps. They sign in with the account that is on the phone, so no
 * client ID or secret is needed. [treeUri] is the folder the user picked, it counts as the root.
 */
class SafClient(private val context: Context, treeUri: String) : RemoteClient {
    private val tree: Uri = Uri.parse(treeUri)
    private val cr get() = context.contentResolver
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    private class Child(val id: String, val name: String, val mime: String, val size: Long, val modified: Long) {
        val isDir get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun children(id: String): List<Child> {
        val out = mutableListOf<Child>()
        val q = try {
            cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ), null, null, null)
        } catch (e: SecurityException) {
            throw IOException("Access to this folder was withdrawn. Choose the folder again.")
        }
        q?.use { c ->
            while (c.moveToNext()) out += Child(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), if (c.isNull(3)) -1 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4))
        } ?: throw IOException("This app did not answer. Is it installed and signed in?")
        return out
    }

    private fun segments(path: String) = path.trim('/').split('/').filter { it.isNotEmpty() }

    /** The document id of [path], or null when there is nothing with that name */
    private fun resolve(path: String): String? {
        var id = rootId
        for (s in segments(path)) id = children(id).firstOrNull { it.name == s }?.id ?: return null
        return id
    }

    private fun parentOf(path: String) = "/" + segments(path).dropLast(1).joinToString("/")
    private fun nameOf(path: String) = segments(path).lastOrNull().orEmpty()

    override fun list(path: String): List<RemoteEntry> {
        val id = resolve(path) ?: throw IOException("This folder does not exist")
        val base = "/" + segments(path).joinToString("/")
        return children(id).map { RemoteEntry(it.name, (if (base == "/") "" else base) + "/" + it.name, it.isDir, it.size, it.modified) }
    }

    override fun mkdir(path: String): Boolean {
        val parent = resolve(parentOf(path)) ?: return false
        return DocumentsContract.createDocument(cr, docUri(parent), DocumentsContract.Document.MIME_TYPE_DIR, nameOf(path)) != null
    }

    override fun delete(path: String): Boolean {
        val id = resolve(path) ?: return false
        return DocumentsContract.deleteDocument(cr, docUri(id))
    }

    override fun rename(from: String, to: String): Boolean {
        val id = resolve(from) ?: return false
        var current = docUri(id)
        if (parentOf(from) != parentOf(to)) {
            val srcParent = resolve(parentOf(from)) ?: return false
            val dstParent = resolve(parentOf(to)) ?: return false
            current = DocumentsContract.moveDocument(cr, current, docUri(srcParent), docUri(dstParent)) ?: return false
        }
        if (nameOf(from) != nameOf(to)) return DocumentsContract.renameDocument(cr, current, nameOf(to)) != null
        return true
    }

    override fun openRead(path: String): InputStream {
        val id = resolve(path) ?: throw IOException("This file does not exist")
        return cr.openInputStream(docUri(id)) ?: throw IOException("The file could not be opened")
    }

    override fun openWrite(path: String): OutputStream {
        val existing = resolve(path)
        val uri = if (existing != null) docUri(existing) else {
            val parent = resolve(parentOf(path)) ?: throw IOException("The folder does not exist")
            val name = nameOf(path)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
            DocumentsContract.createDocument(cr, docUri(parent), mime, name) ?: throw IOException("The file could not be created")
        }
        return cr.openOutputStream(uri, "wt") ?: throw IOException("The file could not be opened for writing")
    }

    override fun copy(from: String, to: String): Boolean = runCatching {
        if (nameOf(from) != nameOf(to)) return false
        val id = resolve(from) ?: return false
        val dst = resolve(parentOf(to)) ?: return false
        DocumentsContract.copyDocument(cr, docUri(id), docUri(dst)) != null
    }.getOrDefault(false)

    override fun close() {}
}
