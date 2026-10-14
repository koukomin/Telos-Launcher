package de.mm20.launcher2.ui.files

import de.mm20.launcher2.helper.ExclusiveFilePublication
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** The archives Telos can create. RAR can only be read: its format may not be written by free software. */
enum class CompressFormat(val extension: String, val supportsPassword: Boolean) {
    Zip(".zip", true),
    SevenZ(".7z", false),
    TarGz(".tar.gz", false),
}

/** Creates zip (optionally AES-256 encrypted), 7z and tar.gz archives from local files, with progress and cancel. */
object ArchiveWriter {

    fun tarGz(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) =
        staged(sources, target, cancel) { output -> tarGzContent(sources, output, cancel, progress) }


    fun sevenZ(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) =
        staged(sources, target, cancel) { output -> sevenZContent(sources, output, cancel, progress) }


    fun zipEncrypted(sources: List<File>, target: File, password: CharArray, cancel: CancelFlag, progress: (Long) -> Unit) =
        staged(sources, target, cancel) { output -> zipEncryptedContent(sources, output, password, cancel, progress) }


    /** All writers own their staging files; failures never delete a pre-existing destination. */
    private fun staged(sources: List<File>, target: File, cancel: CancelFlag, write: (File) -> Unit) {
        require(sources.isNotEmpty())
        require(sources.map { it.name }.distinct().size == sources.size) { "Duplicate archive names" }
        val parent = target.parentFile?.canonicalFile ?: throw IOException("Missing output directory")
        sources.forEach { source ->
            require(source.name.isNotEmpty() && !Files.isSymbolicLink(source.toPath())) { "Invalid archive source" }
            require(!parent.toPath().startsWith(source.toPath().toRealPath())) { "Output is inside a source directory" }
        }
        val destination = parent.toPath().resolve(target.name)
        if (Files.exists(destination, NOFOLLOW_LINKS)) throw IOException("Archive already exists")
        val staging = Files.createTempDirectory(parent.toPath(), ".telos-archive-")
        val output = staging.resolve(target.name)
        try {
            write(output.toFile())
            if (cancel.cancelled) throw IOException("Cancelled")
            ExclusiveFilePublication.copy(output.toFile(), destination.toFile()) {
                if (cancel.cancelled) throw IOException("Cancelled")
            }
        } finally {
            runCatching { Files.deleteIfExists(output) }
            runCatching { Files.deleteIfExists(staging) }
        }
    }

    private fun copy(file: File, cancel: CancelFlag, progress: (Long) -> Unit, write: (ByteArray, Int) -> Unit) {
        Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (cancel.cancelled) throw IOException("Cancelled")
                val n = input.read(buffer)
                if (n < 0) break
                write(buffer, n)
                progress(n.toLong())
            }
        }
    }

    /** A zip file whose files are encrypted with AES-256 (never the weak ZipCrypto) */
    private fun zipEncryptedContent(sources: List<File>, target: File, password: CharArray, cancel: CancelFlag, progress: (Long) -> Unit) {
        require(password.isNotEmpty())
        val secret = password.copyOf()
        try {
        ZipOutputStream(target.outputStream().buffered(), secret).use { zip ->
            var entries = 0
            fun add(file: File, entryName: String, depth: Int = 0) {
                if ('\\' in file.name || file.name == "." || file.name == "..") throw IOException("Invalid archive entry name")
                if (++entries > 50_000 || depth > 64) throw IOException("Archive contains too many entries")
                if (Files.isSymbolicLink(file.toPath())) throw IOException("Symbolic links cannot be archived")
                if (!Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) throw IOException("Not a regular file")
                if (cancel.cancelled) throw IOException("Cancelled")
                if (file.isDirectory) {
                    // a folder has no content to encrypt
                    val dir = ZipParameters()
                    dir.setFileNameInZip("$entryName/")
                    dir.setEncryptFiles(false)
                    dir.setCompressionMethod(CompressionMethod.STORE)
                    zip.putNextEntry(dir)
                    zip.closeEntry()
                    Files.newDirectoryStream(file.toPath()).use { children ->
                        for (child in children) add(child.toFile(), "$entryName/${child.fileName}", depth + 1)
                    }
                } else {
                    val params = ZipParameters()
                    params.setFileNameInZip(entryName)
                    params.setCompressionMethod(CompressionMethod.DEFLATE)
                    params.setEncryptFiles(true)
                    params.setEncryptionMethod(EncryptionMethod.AES)
                    params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
                    params.setLastModifiedFileTime(file.lastModified())
                    zip.putNextEntry(params)
                    copy(file, cancel, progress) { buffer, n -> zip.write(buffer, 0, n) }
                    zip.closeEntry()
                }
            }
            sources.forEach { add(it, it.name) }
        }
        } finally { secret.fill('\u0000') }
    }

    /** A 7z archive (LZMA2). Not encrypted. */
    private fun sevenZContent(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        SevenZOutputFile(target).use { out ->
            var entries = 0
            fun add(file: File, entryName: String, depth: Int = 0) {
                if ('\\' in file.name || file.name == "." || file.name == "..") throw IOException("Invalid archive entry name")
                if (++entries > 50_000 || depth > 64) throw IOException("Archive contains too many entries")
                if (Files.isSymbolicLink(file.toPath())) throw IOException("Symbolic links cannot be archived")
                if (!Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) throw IOException("Not a regular file")
                if (cancel.cancelled) throw IOException("Cancelled")
                out.putArchiveEntry(out.createArchiveEntry(file, entryName))
                if (file.isDirectory) {
                    out.closeArchiveEntry()
                    Files.newDirectoryStream(file.toPath()).use { children ->
                        for (child in children) add(child.toFile(), "$entryName/${child.fileName}", depth + 1)
                    }
                } else {
                    copy(file, cancel, progress) { buffer, n -> out.write(buffer, 0, n) }
                    out.closeArchiveEntry()
                }
            }
            sources.forEach { add(it, it.name) }
            out.finish()
        }
    }

    /** A tar archive compressed with gzip. Not encrypted. */
    private fun tarGzContent(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        TarArchiveOutputStream(GzipCompressorOutputStream(target.outputStream().buffered())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            var entries = 0
            fun add(file: File, entryName: String, depth: Int = 0) {
                if ('\\' in file.name || file.name == "." || file.name == "..") throw IOException("Invalid archive entry name")
                if (++entries > 50_000 || depth > 64) throw IOException("Archive contains too many entries")
                if (Files.isSymbolicLink(file.toPath())) throw IOException("Symbolic links cannot be archived")
                if (!Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) throw IOException("Not a regular file")
                if (cancel.cancelled) throw IOException("Cancelled")
                tar.putArchiveEntry(TarArchiveEntry(file, entryName))
                if (file.isDirectory) {
                    tar.closeArchiveEntry()
                    Files.newDirectoryStream(file.toPath()).use { children ->
                        for (child in children) add(child.toFile(), "$entryName/${child.fileName}", depth + 1)
                    }
                } else {
                    copy(file, cancel, progress) { buffer, n -> tar.write(buffer, 0, n) }
                    tar.closeArchiveEntry()
                }
            }
            sources.forEach { add(it, it.name) }
            tar.finish()
        }
    }
}
