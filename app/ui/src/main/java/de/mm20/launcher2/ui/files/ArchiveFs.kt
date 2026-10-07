package de.mm20.launcher2.ui.files

import android.net.Uri
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** The address of something inside an archive file: arc://<archive path, encoded>!/<path inside> */
object ArchivePath {
    private const val PREFIX = "arc://"
    fun isArchive(p: String) = p.startsWith(PREFIX)
    fun archiveOf(p: String): String = Uri.decode(p.removePrefix(PREFIX).substringBefore('!'))
    fun innerOf(p: String): String = "/" + p.substringAfter('!', "").trim('/')
    fun isRoot(p: String) = isArchive(p) && innerOf(p) == "/"
    fun build(archive: String, inner: String) = PREFIX + Uri.encode(archive) + "!/" + inner.trim('/')

    /** Whether Telos can open this file as a folder */
    fun canOpen(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".7z") || n.endsWith(".tar") ||
            n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.bz2") || n.endsWith(".tar.xz") || n.endsWith(".epub") ||
            n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx") || n.endsWith(".odt") || n.endsWith(".ods")
    }
}

private class ArchiveItem(val path: String, val isDir: Boolean, val size: Long, val modified: Long)

/** An archive file shown as a read-only folder: zip, jar, apk, 7z, tar and compressed tar. */
class ArchiveFs(private val archive: File) : Fs {
    override val isRoot = false
    override val isRemote = true // copied by streaming, never as plain files

    private val items: List<ArchiveItem> by lazy { index() }

    private val kind: String get() {
        val n = archive.name.lowercase()
        return when {
            n.endsWith(".7z") -> "7z"
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tar.gz"
            n.endsWith(".tar.bz2") -> "tar.bz2"
            n.endsWith(".tar.xz") -> "tar.xz"
            n.endsWith(".tar") -> "tar"
            else -> "zip"
        }
    }

    private fun clean(name: String): String? {
        val segments = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null // never leaves the archive
        return segments.joinToString("/").ifEmpty { null }
    }

    private fun tarStream(): ArchiveInputStream<*> {
        val raw = BufferedInputStream(archive.inputStream())
        val decompressed: InputStream = when (kind) {
            "tar.gz" -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, raw)
            "tar.bz2" -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, raw)
            "tar.xz" -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ, raw)
            else -> raw
        }
        return ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, BufferedInputStream(decompressed))
    }

    private fun index(): List<ArchiveItem> {
        val found = LinkedHashMap<String, ArchiveItem>()
        fun add(name: String, dir: Boolean, size: Long, time: Long) {
            val path = clean(name) ?: return
            // the folders above a file exist even when the archive does not list them
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (parent !in found) found[parent] = ArchiveItem(parent, true, -1, time)
                parent = parent.substringBeforeLast('/', "")
            }
            found[path] = ArchiveItem(path, dir, if (dir) -1 else size, time)
        }
        when (kind) {
            "7z" -> SevenZFile.builder().setFile(archive).get().use { z ->
                var e = z.nextEntry
                while (e != null) { add(e.name, e.isDirectory, e.size, e.lastModifiedDate?.time ?: 0); e = z.nextEntry }
            }
            "zip" -> ZipFile.builder().setFile(archive).get().use { z ->
                for (e in z.entries) add(e.name, e.isDirectory, e.size, e.time)
            }
            else -> tarStream().use { s ->
                var e: ArchiveEntry? = s.nextEntry
                while (e != null) { add(e.name, e.isDirectory, e.size, e.lastModifiedDate?.time ?: 0); e = s.nextEntry }
            }
        }
        return found.values.toList()
    }

    private fun inner(path: String) = ArchivePath.innerOf(path).trim('/')

    override fun list(path: String): List<FsEntry> {
        val dir = inner(path)
        return items.filter { it.path.substringBeforeLast('/', "") == dir && it.path != dir }.map {
            FsEntry(ArchivePath.build(archive.path, it.path), it.path.substringAfterLast('/'), it.isDir, it.size, it.modified)
        }
    }

    override fun mkdir(parent: String, name: String) = false
    override fun createFile(parent: String, name: String) = false
    override fun rename(path: String, newName: String) = false
    override fun delete(path: String) = false
    override fun copy(src: String, dstDir: String, newName: String) = false
    override fun move(src: String, dstDir: String, newName: String) = false
    override fun chmod(path: String, mode: String) = false
    override fun exists(path: String) = inner(path).isEmpty() || items.any { it.path == inner(path) }
    override fun totalSize(path: String): Long = items.filter { !it.isDir && (inner(path).isEmpty() || it.path == inner(path) || it.path.startsWith(inner(path) + "/")) }.sumOf { it.size }

    override fun openRead(path: String): InputStream {
        val wanted = inner(path)
        when (kind) {
            "zip" -> {
                val zip = ZipFile.builder().setFile(archive).get()
                val entry = zip.entries.asSequence().firstOrNull { clean(it.name) == wanted } ?: run { zip.close(); throw IOException("Not found in the archive") }
                val stream = zip.getInputStream(entry)
                return object : java.io.FilterInputStream(stream) { override fun close() { runCatching { super.close() }; runCatching { zip.close() } } }
            }
            "7z" -> {
                val z = SevenZFile.builder().setFile(archive).get()
                var e = z.nextEntry
                while (e != null && clean(e.name) != wanted) e = z.nextEntry
                if (e == null) { z.close(); throw IOException("Not found in the archive") }
                return object : InputStream() {
                    override fun read(): Int = z.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = z.read(b, off, len)
                    override fun close() { z.close() }
                }
            }
            else -> {
                val s = tarStream()
                var e = s.nextEntry
                while (e != null && clean(e.name) != wanted) e = s.nextEntry
                if (e == null) { s.close(); throw IOException("Not found in the archive") }
                return object : java.io.FilterInputStream(s) { override fun close() { s.close() } }
            }
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<String, ArchiveFs>()
        fun of(archive: String): ArchiveFs = cache.getOrPut(archive) { ArchiveFs(File(archive)) }
        fun forget(archive: String) { cache.remove(archive) }
    }
}
