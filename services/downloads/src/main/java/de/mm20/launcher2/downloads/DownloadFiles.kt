package de.mm20.launcher2.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import de.mm20.launcher2.downloads.logic.FileNames
import java.io.Closeable
import java.io.IOException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Random access writer of a download file; positional writes are safe from several connections at once. */
interface DownloadSink : Closeable {
    fun writeAt(position: Long, buffer: ByteArray, offset: Int, length: Int)
    fun truncate(size: Long)
    fun sync()
}

class CreatedFile(val uri: String, val name: String)

/**
 * Where downloads go and how they are read back. Three kinds of location, all scoped storage safe:
 * a folder chosen with the Storage Access Framework (tree uri), Downloads/Telos of the Downloads
 * collection (MediaStore, Android 10 and newer), and below Android 10 the app's own Downloads folder.
 */
class DownloadFiles(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Creates the empty file for a download. A taken name gets a number. */
    fun create(treeUri: String?, name: String, mimeType: String?): CreatedFile {
        val safeName = FileNames.sanitize(name).ifBlank { "download" }
        val mime = mimeType?.substringBefore(';')?.trim()?.ifBlank { null } ?: "application/octet-stream"
        if (treeUri != null) {
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(resolver, parent, mime, safeName)
                ?: throw DownloadException(ErrorKind.Storage, "The folder can not be written", false)
            return CreatedFile(doc.toString(), displayName(doc.toString()) ?: safeName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Telos")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                // an unfinished file would be removed after 7 days; downloads can wait longer
                put(MediaStore.MediaColumns.DATE_EXPIRES, System.currentTimeMillis() / 1000 + 60L * 24 * 3600)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw DownloadException(ErrorKind.Storage, "Can not create the file in Downloads", false)
            return CreatedFile(uri.toString(), displayName(uri.toString()) ?: safeName)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Telos")
        dir.mkdirs()
        val unique = FileNames.unique(safeName) { File(dir, it).exists() }
        val file = File(dir, unique)
        if (!file.createNewFile()) throw DownloadException(ErrorKind.Storage, "Can not create the file", false)
        return CreatedFile(Uri.fromFile(file).toString(), unique)
    }

    fun openSink(uri: String): DownloadSink {
        val u = Uri.parse(uri)
        return try {
            if (u.scheme == "file") {
                val raf = RandomAccessFile(File(u.path!!), "rw")
                ChannelSink(raf.channel, raf)
            } else {
                val pfd = resolver.openFileDescriptor(u, "rw")
                    ?: throw DownloadException(ErrorKind.Storage, "Can not open the file", false)
                ChannelSink(ParcelFileDescriptor.AutoCloseOutputStream(pfd).channel, null)
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException(ErrorKind.Storage, "Can not open the file: ${e.message}", false, e)
        }
    }

    fun openInput(uri: String): InputStream? {
        val u = Uri.parse(uri)
        return try {
            if (u.scheme == "file") File(u.path!!).inputStream() else resolver.openInputStream(u)
        } catch (e: Exception) {
            null
        }
    }

    fun exists(uri: String): Boolean = length(uri) >= 0

    /** Size of the file, -1 if it does not exist */
    fun length(uri: String): Long {
        val u = Uri.parse(uri)
        return try {
            if (u.scheme == "file") {
                File(u.path!!).let { if (it.exists()) it.length() else -1 }
            } else {
                resolver.openFileDescriptor(u, "r")?.use { it.statSize } ?: -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    fun displayName(uri: String): String? {
        val u = Uri.parse(uri)
        if (u.scheme == "file") return File(u.path!!).name
        return try {
            resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The file is complete: it becomes visible to other apps */
    fun finish(uri: String) {
        val u = Uri.parse(uri)
        if (u.scheme == "content" && u.authority == MediaStore.AUTHORITY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                resolver.update(u, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    putNull(MediaStore.MediaColumns.DATE_EXPIRES)
                }, null, null)
            } catch (_: Exception) {
            }
        }
    }

    fun delete(uri: String) {
        val u = Uri.parse(uri)
        try {
            when {
                u.scheme == "file" -> File(u.path!!).delete()
                DocumentsContract.isDocumentUri(context, u) -> DocumentsContract.deleteDocument(resolver, u)
                else -> resolver.delete(u, null, null)
            }
        } catch (_: Exception) {
        }
    }

    // ---- torrents: libtorrent writes real files, so they live in a staging folder first

    /**
     * The folder libtorrent writes the data of a torrent into. It is in the storage of the app (not
     * reachable with the Storage Access Framework or MediaStore, which libtorrent can not use); the finished
     * files are copied to the folder the user chose by [copyTorrentFile].
     */
    fun torrentStagingDir(taskId: String): File {
        val base = context.getExternalFilesDir("torrents") ?: File(context.filesDir, "torrents")
        return File(base, taskId).also { it.mkdirs() }
    }

    /** The folder yt-dlp writes into (partial files stay here, so a paused media download resumes) */
    fun mediaStagingDir(taskId: String): File {
        val base = context.getExternalFilesDir("media") ?: File(context.filesDir, "media")
        return File(base, taskId).also { it.mkdirs() }
    }

    /** resume data and the .torrent file of a task; private to the app and part of nothing else */
    fun torrentMetaDir(taskId: String): File = File(File(context.filesDir, "downloads"), "torrents/$taskId").also { it.mkdirs() }

    /** Free bytes of the volume of the staging folder */
    fun stagingFreeBytes(): Long = try {
        (context.getExternalFilesDir("torrents") ?: context.filesDir).usableSpace
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /**
     * Copies a finished file of a torrent into the chosen folder, keeping the folder structure of the torrent
     * ([segments], last one is the file name). A folder chosen with the Storage Access Framework gets sub folders
     * made with DocumentsContract; the Downloads collection gets Download/Telos/<folders> (Android 10 and newer);
     * below Android 10 the app's own Downloads/Telos folder is used.
     * [cancelled] is checked while copying, [progress] gets the bytes copied.
     */
    fun copyTorrentFile(
        treeUri: String?, segments: List<String>, source: File, mimeType: String?,
        cancelled: () -> Boolean, progress: (Long) -> Unit,
    ): CreatedFile {
        if (segments.isEmpty()) throw DownloadException(ErrorKind.Storage, "Empty file name", false)
        val mime = mimeType ?: "application/octet-stream"
        val dirs = segments.dropLast(1)
        val name = segments.last()
        try {
            if (treeUri != null) {
                val tree = Uri.parse(treeUri)
                var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                for (d in dirs) parent = childDirectory(tree, parent, d)
                val doc = DocumentsContract.createDocument(resolver, parent, mime, name)
                    ?: throw DownloadException(ErrorKind.Storage, "The folder can not be written", false)
                try {
                    resolver.openOutputStream(doc, "w")!!.use { out -> copyStream(source, out, cancelled, progress) }
                } catch (e: Exception) {
                    delete(doc.toString())
                    throw e
                }
                return CreatedFile(doc.toString(), displayName(doc.toString()) ?: name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relative = (listOf(Environment.DIRECTORY_DOWNLOADS, "Telos") + dirs).joinToString("/")
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw DownloadException(ErrorKind.Storage, "Can not create the file in Downloads", false)
                try {
                    resolver.openOutputStream(uri, "w")!!.use { out -> copyStream(source, out, cancelled, progress) }
                } catch (e: Exception) {
                    delete(uri.toString())
                    throw e
                }
                finish(uri.toString())
                return CreatedFile(uri.toString(), displayName(uri.toString()) ?: name)
            }
            var dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Telos")
            for (d in dirs) dir = File(dir, d)
            dir.mkdirs()
            val unique = FileNames.unique(name) { File(dir, it).exists() }
            val target = File(dir, unique)
            try {
                target.outputStream().use { out -> copyStream(source, out, cancelled, progress) }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            return CreatedFile(Uri.fromFile(target).toString(), unique)
        } catch (e: DownloadException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException(ErrorKind.Storage, "Can not copy ${source.name}: ${e.message}", false, e)
        }
    }

    private fun copyStream(source: File, out: java.io.OutputStream, cancelled: () -> Boolean, progress: (Long) -> Unit) {
        source.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            var total = 0L
            while (true) {
                if (cancelled()) throw kotlinx.coroutines.CancellationException("Stopped while copying")
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                total += n
                progress(total)
            }
        }
    }

    /** The sub folder [name] of [parent] in a tree, created when it is not there */
    private fun childDirectory(tree: Uri, parent: Uri, name: String): Uri {
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR && c.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Can not create the folder $name")
    }

    private class ChannelSink(private val channel: FileChannel, private val raf: RandomAccessFile?) : DownloadSink {
        override fun writeAt(position: Long, buffer: ByteArray, offset: Int, length: Int) {
            val bb = ByteBuffer.wrap(buffer, offset, length)
            var pos = position
            while (bb.hasRemaining()) pos += channel.write(bb, pos)
        }

        override fun truncate(size: Long) {
            channel.truncate(size)
        }

        override fun sync() {
            try { channel.force(false) } catch (_: Exception) {}
        }

        override fun close() {
            try { channel.close() } finally { raf?.close() }
        }
    }
}
