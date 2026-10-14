package de.mm20.launcher2.helper

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

/** A journal belongs to one local copy. No credentials or opened streams are kept in it. */
data class TransferEntry(
    val source: String, val destination: String, val directory: Boolean,
    val size: Long, val modified: Long, val identity: String?,
    var committed: Boolean = false, var digest: String? = null,
)
data class TransferPlan(val id: String, val destinationRoot: String, val move: Boolean, val entries: List<TransferEntry>)

/** Resumable, bounded local transfers. A move removes only byte-verified files from its original manifest. */
object ResumableFileTransfer {
    class Paused : IOException("Transfer paused")
    class LimitExceeded : IOException("Transfer exceeds safe planning limits")
    class SourceChanged : IOException("Source changed; original retained")
    class OriginalRetained : IOException("Some originals were retained")
    private val noFollow = arrayOf(NOFOLLOW_LINKS)
    private fun attributes(path: File): BasicFileAttributes = Files.readAttributes(path.toPath(), BasicFileAttributes::class.java, *noFollow)
    private fun checkPlain(path: File) {
        var current: File? = path.absoluteFile
        while (current != null) {
            if (Files.isSymbolicLink(current.toPath())) throw IOException("Symbolic links are not supported for resumable transfers")
            current = current.parentFile
        }
    }
    fun plan(sources: List<File>, destination: File, move: Boolean, checkpoint: () -> Unit = {}): TransferPlan {
        require(sources.isNotEmpty() && sources.size <= 1000)
        checkPlain(destination)
        val root = destination.canonicalFile
        require(root.isDirectory)
        val rows = ArrayList<TransferEntry>()
        val reserved = mutableSetOf<String>()
        var metadata = 0L
        fun add(source: File, target: File, depth: Int) {
            checkpoint(); checkPlain(source)
            if (depth > 64 || rows.size >= 10_000) throw LimitExceeded()
            val attrs = attributes(source)
            if (!attrs.isDirectory && !attrs.isRegularFile) throw IOException("Unsupported source file")
            metadata += 192L + (source.path.length + target.path.length) * 2L
            if (metadata > 4L * 1024 * 1024) throw LimitExceeded()
            rows += TransferEntry(source.absolutePath, target.absolutePath, attrs.isDirectory, attrs.size(), attrs.lastModifiedTime().toMillis(), attrs.fileKey()?.toString())
            if (attrs.isDirectory) Files.newDirectoryStream(source.toPath()).use { children ->
                for (child in children) add(child.toFile(), File(target, child.fileName.toString()), depth + 1)
            }
        }
        for (source in sources) {
            checkPlain(source)
            val canonical = source.canonicalFile
            require(canonical.parentFile != null && !root.toPath().startsWith(canonical.toPath()))
            if (move && canonical.parentFile == root) continue
            var name = source.name
            var suffix = 1
            while (File(root, name).exists() || name in reserved) {
                val extension = source.extension.takeIf { !source.isDirectory && it.isNotEmpty() }
                val stem = if (extension != null) source.nameWithoutExtension else source.name
                name = "$stem (${suffix++})" + (extension?.let { ".$it" } ?: "")
            }
            reserved += name
            add(canonical, File(root, name), 0)
        }
        return TransferPlan(UUID.randomUUID().toString(), root.path, move, rows)
    }
    private fun unchanged(entry: TransferEntry): Boolean = runCatching {
        val source = File(entry.source)
        checkPlain(source)
        val a = attributes(source)
        a.isRegularFile && a.size() == entry.size && a.lastModifiedTime().toMillis() == entry.modified &&
            (entry.identity == null || a.fileKey()?.toString() == entry.identity)
    }.getOrDefault(false)
    private fun digest(file: File, checkpoint: () -> Unit, buffer: ByteArray, hash: MessageDigest): String {
        hash.reset()
        file.inputStream().use { input ->
            while (true) { checkpoint(); val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        val bytes = hash.digest()
        val digits = "0123456789abcdef"
        return CharArray(bytes.size * 2).also { output ->
            bytes.forEachIndexed { index, byte ->
                val value = byte.toInt() and 255
                output[index * 2] = digits[value ushr 4]
                output[index * 2 + 1] = digits[value and 15]
            }
        }.concatToString()
    }
    fun execute(plan: TransferPlan, checkpoint: () -> Unit, saved: () -> Unit, progress: (Long, Long) -> Unit) {
        require(plan.id.matches(Regex("[0-9a-f-]{36}")))
        val root = File(plan.destinationRoot).canonicalFile
        // Execution is sequential: share scratch space across files and verification passes.
        val ioBuffer = ByteArray(128 * 1024)
        val hash = MessageDigest.getInstance("SHA-256")
        var prefixBuffer: ByteArray? = null
        fun fileDigest(file: File) = digest(file, checkpoint, ioBuffer, hash)
        var completed = 0L
        val total = plan.entries.fold(0L) { sum, item ->
            if (item.directory) sum else Math.addExact(sum, item.size)
        }
        // Check a loaded journal before it can write any path.
        require(plan.entries.size <= 10_000)
        for (entry in plan.entries) {
            val target = File(entry.destination).absoluteFile
            require(target.toPath().normalize().startsWith(root.toPath()) && target != root && entry.size >= 0)
            require(File(entry.source).isAbsolute && File(entry.source).parentFile != null)
        }
        for ((index, entry) in plan.entries.withIndex()) {
            checkpoint()
            val source = File(entry.source)
            val target = File(entry.destination)
            checkPlain(target.parentFile!!)
            if (entry.directory) {
                if (!entry.committed) {
                    val marker = File(target, ".telos-transfer-${plan.id}-$index.owner")
                    if (Files.exists(target.toPath(), NOFOLLOW_LINKS)) {
                        checkPlain(target)
                        if (!target.isDirectory || !Files.isRegularFile(marker.toPath(), NOFOLLOW_LINKS) || marker.length() != 0L)
                            throw IOException("Destination already exists")
                    } else {
                        Files.createDirectory(target.toPath())
                        java.nio.channels.FileChannel.open(marker.toPath(), java.nio.file.StandardOpenOption.WRITE,
                            java.nio.file.StandardOpenOption.CREATE_NEW).use { it.force(true) }
                    }
                    entry.committed = true; saved()
                    Files.deleteIfExists(marker.toPath())
                } else if (!target.isDirectory || Files.isSymbolicLink(target.toPath())) throw IOException("Destination changed")
                continue
            }
            val temporary = File(target.parentFile, ".telos-transfer-${plan.id}-$index.part")
            if (entry.committed) {
                checkPlain(target)
                if (!target.isFile || fileDigest(target) != entry.digest) throw IOException("Destination changed")
                checkPlain(temporary)
                Files.deleteIfExists(temporary.toPath())
                completed += entry.size; progress(completed, total)
                continue
            }
            if (entry.digest != null && target.exists()) {
                checkPlain(target)
                if (!target.isFile || target.length() != entry.size || fileDigest(target) != entry.digest) throw IOException("Destination changed")
                entry.committed = true; saved()
                checkPlain(temporary)
                Files.deleteIfExists(temporary.toPath())
                completed += entry.size; progress(completed, total)
                continue
            }
            if (!unchanged(entry)) throw SourceChanged()
            checkPlain(temporary)
            if (target.exists()) throw IOException("Destination already exists")
            var offset = if (temporary.exists()) temporary.length() else 0L
            if (offset > entry.size) throw IOException("Invalid partial file")
            // A resumed prefix must still be identical to the source, even on coarse-time filesystems.
            source.inputStream().use { input ->
                if (offset > 0) temporary.inputStream().use { partial ->
                    val a = ioBuffer
                    val b = prefixBuffer ?: ByteArray(a.size).also { prefixBuffer = it }
                    var remaining = offset
                    while (remaining > 0) {
                        checkpoint()
                        val length = minOf(a.size.toLong(), remaining).toInt()
                        fun readFully(stream: java.io.InputStream, bytes: ByteArray) {
                            var position = 0
                            while (position < length) { val read = stream.read(bytes, position, length - position); if (read <= 0) throw SourceChanged(); position += read }
                        }
                        readFully(input, a); readFully(partial, b)
                        for (i in 0 until length) if (a[i] != b[i]) throw SourceChanged()
                        remaining -= length
                    }
                }
                RandomAccessFile(temporary, "rw").use { output ->
                    output.seek(offset)
                    val bytes = ioBuffer
                    while (true) {
                        checkpoint()
                        val count = input.read(bytes)
                        if (count < 0) break
                        output.write(bytes, 0, count); offset += count
                        progress(completed + offset, total)
                    }
                    output.fd.sync()
                }
            }
            if (offset != entry.size || !unchanged(entry)) throw SourceChanged()
            entry.digest = fileDigest(temporary)
            // Persist the digest before publication, so a process interruption immediately after
            // publication can recover only an exact matching destination.
            saved()
            // CREATE_NEW closes the check/rename race on Unix and shared storage. A process
            // interruption during publication can leave a partial destination; never overwrite
            // that unknown output on resume. The verified staging and original stay available.
            ExclusiveFilePublication.copy(temporary, target, checkpoint)
            target.setLastModified(entry.modified)
            entry.committed = true; saved()
            Files.deleteIfExists(temporary.toPath())
            completed += entry.size; progress(completed, total)
        }
        if (plan.move) {
            var retained = false
            for (entry in plan.entries.asReversed()) {
                checkpoint()
                val source = File(entry.source)
                if (!Files.exists(source.toPath(), NOFOLLOW_LINKS)) continue
                try {
                    if (entry.directory) {
                        checkPlain(source)
                        val current = attributes(source)
                        if (!current.isDirectory || (entry.identity != null && current.fileKey()?.toString() != entry.identity)) throw SourceChanged()
                        // Nonempty directories are retained: never delete children added after planning.
                        Files.delete(source.toPath())
                    } else {
                        if (!unchanged(entry) || fileDigest(source) != entry.digest ||
                            fileDigest(File(entry.destination)) != entry.digest) throw SourceChanged()
                        Files.delete(source.toPath())
                    }
                } catch (e: Paused) { throw e } catch (_: IOException) { retained = true }
            }
            if (retained) throw OriginalRetained()
        }
    }
    /** Cancelling discards owned partials only; complete copies and originals are retained. */
    fun discardPartials(plan: TransferPlan) {
        if (!plan.id.matches(Regex("[0-9a-f-]{36}"))) return
        val root = File(plan.destinationRoot).canonicalFile.toPath()
        plan.entries.forEachIndexed { index, entry ->
            val parent = File(entry.destination).parentFile ?: return@forEachIndexed
            if (parent.absoluteFile.toPath().normalize().startsWith(root)) runCatching {
                checkPlain(parent)
                Files.deleteIfExists(File(parent, ".telos-transfer-${plan.id}-$index.part").toPath())
            }
        }
    }
}
