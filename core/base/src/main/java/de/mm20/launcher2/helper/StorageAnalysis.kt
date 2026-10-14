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
        // Reused only within this invocation: concurrent scans never share mutable buffers.
        val workspace = DuplicateWorkspace()
        for (sameSize in records.groupBy { it.size }.values.filter { it.size > 1 }) {
            val hashed = LinkedHashMap<String, MutableList<Record>>()
            for (record in sameSize) {
                checkpoint()
                if (!unchanged(record)) continue
                val hash = try { digest(record.file, workspace, checkpoint) }
                    catch (_: java.io.IOException) { continue }
                    catch (_: SecurityException) { continue }
                if (unchanged(record)) hashed.getOrPut(hash) { ArrayList() }.add(record)
            }
            for (candidates in hashed.values.filter { it.size > 1 }) {
                val groups = ArrayList<MutableList<Record>>()
                for (record in candidates) {
                    checkpoint()
                    val group = groups.firstOrNull { unchanged(it.first()) && unchanged(record) && equalBytes(it.first().file, record.file, workspace, checkpoint) }
                    if (group == null) groups.add(arrayListOf(record)) else group.add(record)
                }
                result.addAll(groups.filter { it.size > 1 && it.all(::unchanged) })
            }
        }
        return result.sortedByDescending { group -> group.first().size * (group.size - 1) }
    }
    private fun unchanged(record: Record) = record.file.isFile && record.file.length() == record.size && record.file.lastModified() == record.modified
    private class DuplicateWorkspace {
        val hash: MessageDigest by lazy { MessageDigest.getInstance("SHA-256") }
        val hashBuffer: ByteArray by lazy { ByteArray(64 * 1024) }
        val compareLeft: ByteArray by lazy { ByteArray(64 * 1024) }
        val compareRight: ByteArray by lazy { ByteArray(64 * 1024) }
    }
    private fun digest(file: File, workspace: DuplicateWorkspace, checkpoint: () -> Unit): String {
        val hash = workspace.hash
        hash.reset()
        file.inputStream().use { input ->
            val buffer = workspace.hashBuffer
            while (true) { checkpoint(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        val digest = hash.digest()
        val hex = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (i in digest.indices) {
            val value = digest[i].toInt() and 0xff
            hex[i * 2] = digits[value ushr 4]
            hex[i * 2 + 1] = digits[value and 0xf]
        }
        return String(hex)
    }
    private fun equalBytes(first: File, second: File, workspace: DuplicateWorkspace, checkpoint: () -> Unit): Boolean { return try {
        first.inputStream().use { a -> second.inputStream().use { b ->
            val left = workspace.compareLeft; val right = workspace.compareRight
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
