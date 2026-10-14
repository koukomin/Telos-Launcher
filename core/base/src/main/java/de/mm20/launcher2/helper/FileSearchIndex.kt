package de.mm20.launcher2.helper

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

data class IndexedFile(val path: String, val name: String, val directory: Boolean, val size: Long,
    val modified: Long, val hidden: Boolean, val symbolicLink: Boolean)

data class FileSearchFilter(val extensions: Set<String> = emptySet(), val minimumBytes: Long? = null,
    val maximumBytes: Long? = null, val modifiedAfter: Long? = null, val modifiedBefore: Long? = null) {
    fun matches(file: IndexedFile): Boolean =
        (extensions.isEmpty() || !file.directory && file.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions) &&
            (minimumBytes == null || !file.directory && file.size >= minimumBytes) &&
            (maximumBytes == null || !file.directory && file.size <= maximumBytes) &&
            (modifiedAfter == null && modifiedBefore == null || file.modified > 0) &&
            (modifiedAfter == null || file.modified >= modifiedAfter) &&
            (modifiedBefore == null || file.modified <= modifiedBefore)
}

data class FileSearchSnapshot(val files: List<IndexedFile>, val limited: Boolean, val unreadable: Int, val estimatedBytes: Long = 0)

/** A bounded metadata index; file contents and symlink targets are never read. */
object FileSearchIndex {
    private data class Cached(val timestamp: Long, val snapshot: FileSearchSnapshot)
    private val cache = LinkedHashMap<String, Cached>(4, 0.75f, true)
    private const val MaximumEntries = 50_000
    private const val MaximumMetadataBytes = 16L * 1024 * 1024

    fun invalidate(folder: String) {
        val changed = File(folder).canonicalFile.toPath()
        synchronized(cache) { cache.keys.removeAll { key ->
            val root = File(key).toPath(); changed.startsWith(root) || root.startsWith(changed)
        } }
    }

    fun read(folder: File, checkpoint: () -> Unit = {}): FileSearchSnapshot {
        val root = folder.canonicalFile.toPath()
        val key = root.toString(); val now = System.currentTimeMillis()
        synchronized(cache) { cache[key]?.takeIf { now - it.timestamp < 60_000 }?.let { return it.snapshot } }
        val result = scan(folder, checkpoint); checkpoint()
        synchronized(cache) {
            cache[key] = Cached(System.currentTimeMillis(), result)
            while (cache.size > 4 || cache.values.sumOf { it.snapshot.files.size } > MaximumEntries ||
                cache.values.sumOf { it.snapshot.estimatedBytes } > MaximumMetadataBytes) cache.remove(cache.keys.first())
        }
        return result
    }

    internal fun scan(folder: File, checkpoint: () -> Unit = {}, limit: Int = MaximumEntries,
        memoryLimitBytes: Long = MaximumMetadataBytes): FileSearchSnapshot {
        require(limit > 0 && memoryLimitBytes > 0)
        val root = folder.canonicalFile.toPath(); require(Files.isDirectory(root))
        val entries = ArrayList<IndexedFile>()
        var limited = false; var unreadable = 0; var estimatedBytes = 0L
        fun add(path: Path, attributes: BasicFileAttributes): FileVisitResult {
            checkpoint()
            if (path != root) {
                val bytes = 128L + 2L * (path.toString().length + path.fileName.toString().length)
                if (bytes > memoryLimitBytes - estimatedBytes) { limited = true; return FileVisitResult.TERMINATE }
                estimatedBytes += bytes
                entries += IndexedFile(path.toString(), path.fileName.toString(), attributes.isDirectory,
                    if (attributes.isDirectory) -1 else attributes.size(), attributes.lastModifiedTime().toMillis(),
                    root.relativize(path).any { it.toString().startsWith('.') }, attributes.isSymbolicLink)
            }
            if (entries.size >= limit) { limited = true; return FileVisitResult.TERMINATE }
            return FileVisitResult.CONTINUE
        }
        Files.walkFileTree(root, emptySet(), 64, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = add(dir, attrs)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isDirectory) limited = true
                return add(file, attrs)
            }
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                checkpoint(); unreadable++; return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                checkpoint(); if (exc != null) unreadable++; return FileVisitResult.CONTINUE
            }
        })
        return FileSearchSnapshot(entries, limited, unreadable, estimatedBytes)
    }
}
