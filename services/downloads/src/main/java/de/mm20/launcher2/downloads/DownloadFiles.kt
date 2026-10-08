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
