package de.mm20.launcher2.helper

import net.lingala.zip4j.io.outputstream.SplitOutputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Streams split ZIPs through a private sibling directory. No existing archive volume is replaced. */
object SplitZipArchive {
    const val MIN_PART_BYTES = 65_536L
    private const val MAX_ENTRIES = 50_000

    fun create(
        sources: List<File>, target: File, splitBytes: Long, password: CharArray? = null,
        checkpoint: () -> Unit = {}, progress: (Long) -> Unit = {},
    ): List<File> {
        require(splitBytes >= MIN_PART_BYTES)
        require(sources.isNotEmpty() && target.name.endsWith(".zip", ignoreCase = true))
        require(sources.map { it.name }.distinct().size == sources.size) { "Duplicate archive names" }
        val parent = target.parentFile?.canonicalFile ?: throw IOException("Missing output directory")
        val sourcePaths = sources.map { it.toPath().toAbsolutePath().normalize() }
        sourcePaths.forEach {
            require(it.fileName != null && !Files.isSymbolicLink(it)) { "Symbolic links cannot be archived" }
            require(!parent.toPath().startsWith(it.toRealPath())) { "Output is inside a source directory" }
        }
        ensureFree(parent, target.name)
        val staging = Files.createTempDirectory(parent.toPath(), ".telos-split-")
        val published = mutableListOf<Pair<Path, Any?>>()
        val secret = password?.takeIf { it.isNotEmpty() }?.copyOf()
        try {
            var entries = 0
            val buffer = ByteArray(64 * 1024)
            SplitOutputStream(staging.resolve(target.name).toFile(), splitBytes).use { parts ->
                ZipOutputStream(parts, secret).use { zip ->
                    fun add(path: Path, name: String, depth: Int) {
                        checkpoint()
                        if ('\\' in path.fileName.toString()) throw IOException("Invalid archive entry name")
                        if (parts.currentSplitFileCounter >= 10_000) throw IOException("Archive has too many parts")
                        if (++entries > MAX_ENTRIES || depth > 64) throw IOException("Archive contains too many entries")
                        if (Files.isSymbolicLink(path)) throw IOException("Symbolic links cannot be archived")
                        val directory = Files.isDirectory(path, NOFOLLOW_LINKS)
                        if (!directory && !Files.isRegularFile(path, NOFOLLOW_LINKS)) throw IOException("Not a regular file")
                        val parameters = ZipParameters().apply {
                            fileNameInZip = if (directory) "$name/" else name
                            compressionMethod = if (directory) CompressionMethod.STORE else CompressionMethod.DEFLATE
                            isEncryptFiles = !directory && secret != null
                            if (isEncryptFiles) {
                                encryptionMethod = EncryptionMethod.AES
                                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                            }
                            lastModifiedFileTime = Files.getLastModifiedTime(path, NOFOLLOW_LINKS).toMillis()
                        }
                        zip.putNextEntry(parameters)
                        if (!directory) {
                            Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                                while (true) {
                                    checkpoint()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    zip.write(buffer, 0, read)
                                    if (parts.currentSplitFileCounter >= 10_000) throw IOException("Archive has too many parts")
                                    progress(read.toLong())
                                }
                            }
                        }
                        zip.closeEntry()
                        if (directory) Files.newDirectoryStream(path).use { children ->
                            for (child in children) add(child, "$name/${child.fileName}", depth + 1)
                        }
                    }
                    sourcePaths.forEach { add(it, it.fileName.toString(), 0) }
                }
            }
            checkpoint()
            ensureFree(parent, target.name)
            val volumes = Files.newDirectoryStream(staging).use { children ->
                children.take(10_001).toList().also { if (it.size > 10_000) throw IOException("Archive has too many parts") }
            }
            // Publish the central directory last so an unfinished volume set cannot be opened as a ZIP.
            volumes.sortedBy { if (it.fileName.toString() == target.name) 1 else 0 }.forEach { part ->
                checkpoint()
                val destination = parent.toPath().resolve(part.fileName)
                val identity = ExclusiveFilePublication.copy(part.toFile(), destination.toFile(), checkpoint)
                published.add(destination to identity)
            }
            return published.map { it.first.toFile() }
        } catch (error: Exception) {
            published.forEach { (path, identity) ->
                val removed = runCatching {
                    if (!Files.exists(path, NOFOLLOW_LINKS)) true
                    else if (identity != null && Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey() == identity) {
                        Files.deleteIfExists(path)
                    } else false
                }.getOrDefault(false)
                if (!removed) error.addSuppressed(IOException("Unfinished archive part retained: ${path.fileName}"))
            }
            throw error
        } finally {
            secret?.fill('\u0000')
            Files.newDirectoryStream(staging).use { children ->
                children.forEach { runCatching { Files.deleteIfExists(it) } }
            }
            Files.deleteIfExists(staging)
        }
    }

    private fun ensureFree(parent: File, name: String) {
        val base = name.dropLast(4)
        val volumePattern = Regex(Regex.escape(base) + "\\.z[0-9]+", RegexOption.IGNORE_CASE)
        Files.newDirectoryStream(parent.toPath()).use { children ->
            if (children.any { it.fileName.toString().equals(name, true) || volumePattern.matches(it.fileName.toString()) }) {
                throw IOException("Archive or one of its volumes already exists")
            }
        }
    }
}
