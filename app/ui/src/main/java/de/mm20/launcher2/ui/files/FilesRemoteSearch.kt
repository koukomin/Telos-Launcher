package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.files.remote.RemotePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque

/** Read-only traversal. Limits apply to visited entries, not just matching results. */
internal object FilesRemoteSearch {
    data class Result(
        val entries: List<FsEntry>,
        val partial: Boolean,
        val visited: Int,
        val unreadableFolders: Int,
        val knownFileBytes: Long,
    )

    suspend fun scan(
        fs: Fs,
        root: String,
        showHidden: Boolean,
        matches: (FsEntry) -> Boolean,
        maximumEntries: Int = 5_000,
        maximumDepth: Int = 16,
        maximumResults: Int = 300,
        timeoutMillis: Long = 60_000,
    ): Result {
        require(fs.isRemote && RemotePath.isRemote(root))
        require(root.length <= 8192)
        require(maximumEntries in 1..5_000 && maximumDepth in 1..16)
        require(maximumResults in 1..300 && timeoutMillis in 1..60_000)
        val started = System.nanoTime()
        val queue = ArrayDeque<Pair<String, Int>>()
        val seen = HashSet<String>()
        val results = ArrayList<FsEntry>()
        val connection = RemotePath.idOf(root)
        val rootInner = RemotePath.innerOf(root).trimEnd('/').ifEmpty { "/" }
        queue.add(root to 0)
        var visited = 0
        var unreadable = 0
        var bytes = 0L
        var partial = false
        fun allowed(path: String): Boolean {
            if (path.length > 8192) return false
            if (!RemotePath.isRemote(path) || RemotePath.idOf(path) != connection) return false
            val inner = RemotePath.innerOf(path)
            if (inner.split('/').any { it == "." || it == ".." } || '\u0000' in inner) return false
            return rootInner == "/" || inner == rootInner || inner.startsWith("$rootInner/")
        }
        traversal@ while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val remaining = timeoutMillis - elapsed
            if (remaining <= 0) { partial = true; break }
            val (folder, depth) = queue.removeFirst()
            if (!seen.add(folder.trimEnd('/'))) continue
            val listing = try {
                // Interrupt blocking network reads on cancellation where the protocol supports it.
                // Provider socket timeouts remain the final bound for non-interruptible drivers.
                withTimeoutOrNull(remaining) { runInterruptible(Dispatchers.IO) { fs.list(folder) } }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                unreadable++; partial = true; continue
            }
            if (listing == null) { partial = true; break }
            for (entry in listing) {
                currentCoroutineContext().ensureActive()
                if ((System.nanoTime() - started) / 1_000_000 >= timeoutMillis) { partial = true; break@traversal }
                if (visited >= maximumEntries) { partial = true; break@traversal }
                visited++
                if (!allowed(entry.path) || entry.name.length > 1024 || !isPlainName(entry.name)) { partial = true; continue }
                if (!showHidden && entry.hidden) continue
                if (entry.isLink || entry.permissions.startsWith("l")) continue
                if (!entry.isDir && entry.size >= 0) {
                    bytes = if (Long.MAX_VALUE - bytes < entry.size) Long.MAX_VALUE else bytes + entry.size
                }
                if (matches(entry)) {
                    if (results.size == maximumResults) { partial = true; break@traversal }
                    results.add(entry)
                }
                if (entry.isDir) {
                    if (depth >= maximumDepth) partial = true
                    else if (queue.size + seen.size >= maximumEntries) partial = true
                    else queue.add(entry.path to depth + 1)
                }
            }
        }
        return Result(results, partial, visited, unreadable, bytes)
    }
}
