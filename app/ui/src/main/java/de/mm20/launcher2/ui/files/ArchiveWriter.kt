package de.mm20.launcher2.ui.files

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

/** The archives Telos can create. RAR can only be read: its format may not be written by free software. */
enum class CompressFormat(val extension: String, val supportsPassword: Boolean) {
    Zip(".zip", true),
    SevenZ(".7z", false),
    TarGz(".tar.gz", false),
}

/** Creates zip (optionally AES-256 encrypted), 7z and tar.gz archives from local files, with progress and cancel. */
object ArchiveWriter {

    private fun copy(file: File, cancel: CancelFlag, progress: (Long) -> Unit, write: (ByteArray, Int) -> Unit) {
        file.inputStream().use { input ->
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
    fun zipEncrypted(sources: List<File>, target: File, password: CharArray, cancel: CancelFlag, progress: (Long) -> Unit) {
        require(password.isNotEmpty())
        ZipOutputStream(target.outputStream().buffered(), password.copyOf()).use { zip ->
            fun add(file: File, entryName: String) {
                if (cancel.cancelled) throw IOException("Cancelled")
                if (file.isDirectory) {
                    // a folder has no content to encrypt
                    val dir = ZipParameters()
                    dir.setFileNameInZip("$entryName/")
                    dir.setEncryptFiles(false)
                    dir.setCompressionMethod(CompressionMethod.STORE)
                    zip.putNextEntry(dir)
                    zip.closeEntry()
                    file.listFiles()?.forEach { add(it, "$entryName/${it.name}") }
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
    }

    /** A 7z archive (LZMA2). Not encrypted. */
    fun sevenZ(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        SevenZOutputFile(target).use { out ->
            fun add(file: File, entryName: String) {
                if (cancel.cancelled) throw IOException("Cancelled")
                out.putArchiveEntry(out.createArchiveEntry(file, entryName))
                if (file.isDirectory) {
                    out.closeArchiveEntry()
                    file.listFiles()?.forEach { add(it, "$entryName/${it.name}") }
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
    fun tarGz(sources: List<File>, target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        TarArchiveOutputStream(GzipCompressorOutputStream(target.outputStream().buffered())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            fun add(file: File, entryName: String) {
                if (cancel.cancelled) throw IOException("Cancelled")
                tar.putArchiveEntry(TarArchiveEntry(file, entryName))
                if (file.isDirectory) {
                    tar.closeArchiveEntry()
                    file.listFiles()?.forEach { add(it, "$entryName/${it.name}") }
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
