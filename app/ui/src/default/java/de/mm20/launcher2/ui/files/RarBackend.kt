package de.mm20.launcher2.ui.files

import android.os.ParcelFileDescriptor
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * "default" flavor: reads RAR (3, 4) and RAR5 with libarchive (me.zhanghai.android.libarchive, Apache-2.0
 * wrapper around libarchive, BSD-2-Clause, with its own clean-room RAR reader). Only the two RAR formats are
 * enabled. libarchive cannot decrypt RAR: encrypted archives are reported as [ArchiveRarEncryptedException].
 */
internal object RarBackend {
    const val isAvailable: Boolean = true

    fun open(file: File): RarSession = LibArchiveRar(file)
}

private class LibArchiveRar(file: File) : RarSession {
    private var archive = 0L
    private var entry = 0L

    init {
        try {
            archive = Archive.readNew()
            Archive.setCharset(archive, "UTF-8".toByteArray(Charsets.UTF_8))
            Archive.readSupportFormatRar(archive)
            Archive.readSupportFormatRar5(archive)
            Archive.readOpenFileName(archive, file.path.toByteArray(Charsets.UTF_8), 64L * 1024)
        } catch (e: ArchiveException) {
            val failure = map(e)
            release()
            throw failure
        }
    }

    private fun release() {
        val a = archive
        archive = 0L
        entry = 0L
        if (a != 0L) runCatching { Archive.free(a) }
    }

    private fun map(e: ArchiveException): IOException {
        val detail = archive.takeIf { it != 0L }?.let { a -> runCatching { Archive.errorString(a)?.toString(Charsets.UTF_8) }.getOrNull() }
        if (RarErrors.isEncryption(e.message) || RarErrors.isEncryption(detail)) return ArchiveRarEncryptedException()
        return IOException(detail ?: e.message ?: "Could not read the RAR archive", e)
    }

    override fun next(): RarEntry? {
        val e = try {
            Archive.readNextHeader(archive)
        } catch (x: ArchiveException) {
            throw map(x)
        }
        entry = e
        if (e == 0L) return null
        val name = ArchiveEntry.pathnameUtf8(e) ?: ArchiveEntry.pathname(e)?.toString(Charsets.UTF_8) ?: ""
        val type = ArchiveEntry.filetype(e)
        val isDir = type == ArchiveEntry.AE_IFDIR
        // anything that is neither a plain file nor a folder (symbolic links, hard links, devices) is left out
        val isLink = (type != 0 && type != ArchiveEntry.AE_IFREG && !isDir) || ArchiveEntry.hardlinkUtf8(e) != null || ArchiveEntry.symlinkUtf8(e) != null
        val size = if (ArchiveEntry.sizeIsSet(e)) ArchiveEntry.size(e) else -1L
        val modified = if (ArchiveEntry.mtimeIsSet(e)) ArchiveEntry.mtime(e) * 1000L else 0L
        val encrypted = ArchiveEntry.isDataEncrypted(e) || ArchiveEntry.isMetadataEncrypted(e)
        return RarEntry(name, isDir, isLink, size, modified, encrypted)
    }

    override fun copyCurrentTo(dest: File) {
        check(entry != 0L) { "No current entry" }
        val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        ParcelFileDescriptor.open(dest, mode).use { pfd ->
            try {
                Archive.readDataIntoFd(archive, pfd.fd)
            } catch (x: ArchiveException) {
                throw map(x)
            }
        }
    }

    override fun openCurrent(): InputStream {
        check(entry != 0L) { "No current entry" }
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val failure = AtomicReference<IOException?>()
        val handle = archive
        val worker = Thread({
            try {
                Archive.readDataIntoFd(handle, writeSide.fd)
            } catch (x: ArchiveException) {
                failure.set(map(x))
            } catch (x: Throwable) {
                failure.set(IOException("Could not read the RAR archive", x))
            } finally {
                runCatching { writeSide.close() }
            }
        }, "rar-reader")
        worker.isDaemon = true
        worker.start()
        return object : ParcelFileDescriptor.AutoCloseInputStream(readSide) {
            private var closed = false

            private fun checkEnd(n: Int): Int {
                if (n < 0) {
                    worker.join()
                    failure.get()?.let { throw it } // a damaged or truncated file is an error, not a short file
                }
                return n
            }

            override fun read(): Int = checkEnd(super.read())
            override fun read(b: ByteArray): Int = read(b, 0, b.size)
            override fun read(b: ByteArray, off: Int, len: Int): Int = checkEnd(super.read(b, off, len))

            override fun close() {
                if (closed) return
                closed = true
                runCatching { super.close() } // the reader thread then fails its next write and ends
                runCatching { worker.join() }
                this@LibArchiveRar.release()
            }
        }
    }

    override fun close() {
        release()
    }
}
