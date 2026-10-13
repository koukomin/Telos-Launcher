package de.mm20.launcher2.helper

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** Bounded, cancellable local scan. Symbolic links are never followed. No files are deleted. */
object StorageAnalysis {
    data class Record(val file: File, val size: Long, val modified: Long)
    data class Result(val files: List<Record>, val folders: Int, val unreadable: Int, val truncated: Boolean) {
        val bytes: Long get() = files.sumOf { it.size }
    }
    fun scan(root: File, limit: Int = 50_000, checkpoint: () -> Unit = {}): Result {
        require(root.isDirectory && limit > 0)
        val pending = ArrayDeque<File>()
        pending.add(root.canonicalFile)
        val files = ArrayList<Record>()
        var folders = 0; var unreadable = 0; var visited = 0; var truncated = false
        while (pending.isNotEmpty() && visited < limit) {
            checkpoint()
            val dir = pending.removeLast()
            folders++; visited++
            try {
                Files.newDirectoryStream(dir.toPath()).use { children ->
                    for (child in children) {
                        checkpoint()
                        if (visited >= limit) { truncated = true; break }
                        if (Files.isSymbolicLink(child)) continue
                        val file = child.toFile()
                        if (file.isDirectory) {
                            if (pending.size + visited < limit) pending.add(file) else truncated = true
                        } else if (file.isFile) { files.add(Record(file, file.length(), file.lastModified())); visited++ }
                    }
                }
            } catch (_: java.io.IOException) { unreadable++ }
              catch (_: SecurityException) { unreadable++ }
        }
        return Result(files, folders, unreadable, truncated || pending.isNotEmpty())
    }
    /** Hash narrows candidates; a full byte comparison confirms each group, even on a hash collision. */
    fun duplicates(records: List<Record>, checkpoint: () -> Unit = {}): List<List<Record>> {
        val result = ArrayList<List<Record>>()
        for (sameSize in records.groupBy { it.size }.values.filter { it.size > 1 }) {
            val hashed = LinkedHashMap<String, MutableList<Record>>()
            for (record in sameSize) {
                checkpoint()
                if (!unchanged(record)) continue
                val hash = runCatching { digest(record.file, checkpoint) }.getOrNull() ?: continue
                if (unchanged(record)) hashed.getOrPut(hash) { ArrayList() }.add(record)
            }
            for (candidates in hashed.values.filter { it.size > 1 }) {
                val groups = ArrayList<MutableList<Record>>()
                for (record in candidates) {
                    checkpoint()
                    val group = groups.firstOrNull { unchanged(it.first()) && unchanged(record) && equalBytes(it.first().file, record.file, checkpoint) }
                    if (group == null) groups.add(arrayListOf(record)) else group.add(record)
                }
                result.addAll(groups.filter { it.size > 1 && it.all(::unchanged) })
            }
        }
        return result.sortedByDescending { group -> group.first().size * (group.size - 1) }
    }
    private fun unchanged(record: Record) = record.file.isFile && record.file.length() == record.size && record.file.lastModified() == record.modified
    private fun digest(file: File, checkpoint: () -> Unit): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { checkpoint(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun equalBytes(first: File, second: File, checkpoint: () -> Unit): Boolean { return try {
        first.inputStream().buffered(64 * 1024).use { a -> second.inputStream().buffered(64 * 1024).use { b ->
            val left = ByteArray(64 * 1024); val right = ByteArray(64 * 1024)
            while (true) {
                checkpoint()
                val count = a.read(left)
                if (count < 0) return b.read() < 0
                var offset = 0
                while (offset < count) { checkpoint(); val n = b.read(right, offset, count - offset); if (n < 0) return false; offset += n }
                for (i in 0 until count) if (left[i] != right[i]) return false
            }
            @Suppress("UNREACHABLE_CODE") false
        } }
    } catch (_: java.io.IOException) { false }
    }
}
