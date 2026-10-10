package de.mm20.launcher2.ui.files

import android.net.Uri
import de.mm20.launcher2.helper.ArchiveFormats
import de.mm20.launcher2.helper.ArchiveKind
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.Closeable
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

    /** "photos.tar.gz" -> "photos", "my.report.zip" -> "my.report": the folder an archive is unpacked into */
    fun baseName(name: String): String = ArchiveFormats.baseName(name)

    /** Whether Telos can open this file as a folder */
    fun canOpen(name: String): Boolean = ArchiveFormats.canOpen(name)
}

private class ArchiveItem(val path: String, val isDir: Boolean, val size: Long, val modified: Long, val encrypted: Boolean = false)

private fun SevenZArchiveEntry.isEncrypted7z(): Boolean =
    contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true

/** An input stream that turns the errors of the library into the exceptions the UI understands */
private class GuardedStream(private val src: InputStream, private val map: (IOException) -> IOException, private val onClose: () -> Unit = {}) : InputStream() {
    override fun read(): Int = try { src.read() } catch (e: IOException) { throw map(e) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = try { src.read(b, off, len) } catch (e: IOException) { throw map(e) }
    override fun available(): Int = try { src.available() } catch (e: IOException) { throw map(e) }
    override fun close() { runCatching { src.close() }; runCatching { onClose() } }
}

/** Reads the encrypted entries of a zip file (ZipCrypto and AES) with zip4j */
private class CryptZip(archive: File, password: CharArray, private val clean: (String) -> String?) : Closeable {
    private val zip = net.lingala.zip4j.ZipFile(archive, password.copyOf())
    private val headers: Map<String, FileHeader> by lazy {
        val found = LinkedHashMap<String, FileHeader>()
        for (h in zip.fileHeaders) { val name = clean(h.fileName) ?: continue; found[name] = h }
        found
    }

    private fun map(e: IOException, h: FileHeader): IOException {
        if (e is ArchiveCryptoException) return e
        if (e is ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) return ArchiveWrongPasswordException()
            // ZipCrypto only has a one byte check: a wrong password may get through and fail at the checksum
            if (e.type == ZipException.Type.CHECKSUM_MISMATCH && h.encryptionMethod == EncryptionMethod.ZIP_STANDARD) return ArchiveWrongPasswordException()
            if (e.message?.contains("password", ignoreCase = true) == true) return ArchiveWrongPasswordException()
        }
        return e
    }

    fun open(name: String): InputStream {
        val h = headers[name] ?: throw IOException("Not found in the archive")
        val stream = try { zip.getInputStream(h) } catch (e: IOException) { throw map(e, h) }
        return GuardedStream(stream, { map(it, h) })
    }

    override fun close() { runCatching { zip.close() } }
}

/** An archive file shown as a read-only folder: zip, jar, apk, 7z, tar and compressed tar, cpio, ar/deb, arj and single compressed files. */
class ArchiveFs(private val archive: File) : Fs {
    override val isRoot = false
    override val isRemote = true // copied by streaming, never as plain files

    private val items: List<ArchiveItem> by lazy { index() }
    private val stamp: Long = archive.lastModified() + archive.length()

    /** A password that is being tried, before it is kept for the session */
    private var candidate: CharArray? = null
    private val password: CharArray? get() = candidate ?: ArchiveSessions.get(archive.path)

    private val kind: ArchiveKind get() = ArchiveFormats.kindOf(archive.name) ?: ArchiveKind.Zip

    /** Whether some of the files in the archive are encrypted. A 7z with encrypted names throws [ArchivePasswordRequiredException] until the password is known. */
    val hasEncrypted: Boolean get() = (kind == ArchiveKind.Zip || kind == ArchiveKind.SevenZ) && items.any { it.encrypted }

    private fun clean(name: String): String? {
        val segments = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null // never leaves the archive
        return segments.joinToString("/").ifEmpty { null }
    }

    private fun sequentialStream(): ArchiveInputStream<*> {
        val raw = BufferedInputStream(archive.inputStream())
        val decompressed: InputStream = when (kind) {
            ArchiveKind.TarGz -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, raw)
            ArchiveKind.TarBz2 -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, raw)
            ArchiveKind.TarXz -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.XZ, raw)
            ArchiveKind.TarLzma -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.LZMA, raw)
            ArchiveKind.TarZ -> CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.Z, raw)
            else -> raw
        }
        val format = when (kind) {
            ArchiveKind.Cpio -> ArchiveStreamFactory.CPIO
            ArchiveKind.Ar -> ArchiveStreamFactory.AR
            ArchiveKind.Arj -> ArchiveStreamFactory.ARJ
            else -> ArchiveStreamFactory.TAR
        }
        return ArchiveStreamFactory().createArchiveInputStream(format, BufferedInputStream(decompressed))
    }

    /** A gz, bz2, xz, lzma or Z file: the stream of its one file */
    private fun singleStream(): InputStream {
        val raw = BufferedInputStream(archive.inputStream())
        val name = when (kind) {
            ArchiveKind.Gz -> CompressorStreamFactory.GZIP
            ArchiveKind.Bz2 -> CompressorStreamFactory.BZIP2
            ArchiveKind.Xz -> CompressorStreamFactory.XZ
            ArchiveKind.Lzma -> CompressorStreamFactory.LZMA
            else -> CompressorStreamFactory.Z
        }
        return BufferedInputStream(CompressorStreamFactory().createCompressorInputStream(name, raw))
    }

    private fun singleName(): String = ArchiveFormats.baseName(archive.name).ifEmpty { archive.name }

    /** Opens a 7z. A missing or wrong password becomes the matching [ArchiveCryptoException]. */
    private fun sevenZ(): SevenZFile {
        val pw = password
        try {
            val builder = SevenZFile.builder().setFile(archive)
            if (pw != null) builder.setPassword(pw.copyOf())
            return builder.get()
        } catch (e: PasswordRequiredException) {
            throw ArchivePasswordRequiredException()
        } catch (e: ArchiveCryptoException) {
            throw e
        } catch (e: IOException) {
            // the names of an encrypted 7z are unreadable with a wrong key
            if (pw != null) throw ArchiveWrongPasswordException()
            throw e
        }
    }

    private fun map7z(encrypted: Boolean, pw: CharArray?): (IOException) -> IOException = { e ->
        when {
            e is ArchiveCryptoException -> e
            e is PasswordRequiredException -> ArchivePasswordRequiredException()
            encrypted && pw != null -> ArchiveWrongPasswordException()
            else -> e
        }
    }

    private fun index(): List<ArchiveItem> {
        val found = LinkedHashMap<String, ArchiveItem>()
        fun add(name: String, dir: Boolean, size: Long, time: Long, encrypted: Boolean = false) {
            val path = clean(name) ?: return
            // the folders above a file exist even when the archive does not list them
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (parent !in found) found[parent] = ArchiveItem(parent, true, -1, time)
                parent = parent.substringBeforeLast('/', "")
            }
            found[path] = ArchiveItem(path, dir, if (dir) -1 else size, time, encrypted && !dir)
        }
        val k = kind
        when {
            k == ArchiveKind.SevenZ -> sevenZ().use { z ->
                for (e in z.entries) {
                    val name = e.name ?: continue
                    add(name, e.isDirectory, e.size, if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L, e.isEncrypted7z())
                }
            }
            k == ArchiveKind.Zip -> ZipFile.builder().setFile(archive).get().use { z ->
                for (e in z.entries) add(e.name, e.isDirectory, e.size, e.time, e.generalPurposeBit.usesEncryption())
            }
            k.isSingleFile -> add(singleName(), false, -1, archive.lastModified())
            else -> sequentialStream().use { s ->
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
    override fun totalSize(path: String): Long = items.filter { !it.isDir && (inner(path).isEmpty() || it.path == inner(path) || it.path.startsWith(inner(path) + "/")) }.sumOf { it.size.coerceAtLeast(0L) }

    /**
     * Unpacks everything into [target] in one pass. Reading file by file through [openRead] would
     * decompress a tar or 7z archive from its start again for every single file.
     * Throws [ArchivePasswordRequiredException] / [ArchiveWrongPasswordException] for encrypted archives.
     */
    fun extractAll(target: File, cancel: CancelFlag, progress: (Long) -> Unit) {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val pw = password
        fun destination(name: String): File? {
            val f = File(target, clean(name) ?: return null)
            return if (f.canonicalPath.startsWith(root)) f else null // zip-slip guard on top of clean()
        }
        fun write(dest: File, read: (ByteArray) -> Int) {
            dest.parentFile?.mkdirs()
            val buffer = ByteArray(64 * 1024)
            dest.outputStream().use { out ->
                while (true) {
                    if (cancel.cancelled) throw IOException("Cancelled")
                    val n = read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    progress(n.toLong())
                }
            }
        }
        val k = kind
        when {
            k == ArchiveKind.Zip -> {
                var crypt: CryptZip? = null
                try {
                    ZipFile.builder().setFile(archive).get().use { z ->
                        for (e in z.entries) {
                            if (cancel.cancelled) throw IOException("Cancelled")
                            val dest = destination(e.name) ?: continue
                            if (e.isDirectory) {
                                dest.mkdirs()
                            } else if (e.generalPurposeBit.usesEncryption()) {
                                if (pw == null) throw ArchivePasswordRequiredException()
                                val opened = crypt ?: CryptZip(archive, pw, this::clean).also { crypt = it }
                                opened.open(clean(e.name) ?: continue).use { input -> write(dest) { input.read(it) } }
                            } else {
                                z.getInputStream(e).use { input -> write(dest) { input.read(it) } }
                            }
                        }
                    }
                } finally {
                    crypt?.close()
                }
            }
            k == ArchiveKind.SevenZ -> sevenZ().use { z ->
                var e: SevenZArchiveEntry? = z.nextEntry
                while (e != null) {
                    if (cancel.cancelled) throw IOException("Cancelled")
                    val encrypted = e.isEncrypted7z()
                    if (encrypted && pw == null) throw ArchivePasswordRequiredException()
                    val guard = map7z(encrypted, pw)
                    val dest = destination(e.name)
                    if (dest != null) {
                        if (e.isDirectory) dest.mkdirs() else write(dest) { buf -> try { z.read(buf) } catch (x: IOException) { throw guard(x) } }
                    }
                    e = try { z.nextEntry } catch (x: IOException) { throw guard(x) }
                }
            }
            k.isSingleFile -> {
                val dest = destination(singleName())
                if (dest != null) singleStream().use { input -> write(dest) { input.read(it) } }
            }
            else -> sequentialStream().use { s ->
                var e: ArchiveEntry? = s.nextEntry
                while (e != null) {
                    if (cancel.cancelled) throw IOException("Cancelled")
                    val link = (e as? TarArchiveEntry)?.let { it.isSymbolicLink || it.isLink } == true ||
                        (e as? CpioArchiveEntry)?.isSymbolicLink == true
                    val dest = if (link) null else destination(e.name)
                    if (dest != null) { if (e.isDirectory) dest.mkdirs() else write(dest) { s.read(it) } }
                    e = s.nextEntry
                }
            }
        }
    }

    override fun openRead(path: String): InputStream = openInner(inner(path))

    private fun openInner(wanted: String): InputStream {
        val k = kind
        val pw = password
        when {
            k == ArchiveKind.Zip -> {
                if (items.firstOrNull { it.path == wanted }?.encrypted == true) {
                    if (pw == null) throw ArchivePasswordRequiredException()
                    val crypt = CryptZip(archive, pw, this::clean)
                    return try {
                        val stream = crypt.open(wanted)
                        GuardedStream(stream, { it }, { crypt.close() })
                    } catch (e: IOException) {
                        crypt.close(); throw e
                    }
                }
                val zip = ZipFile.builder().setFile(archive).get()
                val entry = zip.entries.asSequence().firstOrNull { clean(it.name) == wanted } ?: run { zip.close(); throw IOException("Not found in the archive") }
                val stream = zip.getInputStream(entry)
                return object : java.io.FilterInputStream(stream) { override fun close() { runCatching { super.close() }; runCatching { zip.close() } } }
            }
            k == ArchiveKind.SevenZ -> {
                val z = sevenZ()
                val guardNext = map7z(items.any { it.encrypted }, pw)
                fun next(): SevenZArchiveEntry? = try { z.nextEntry } catch (x: IOException) { z.close(); throw guardNext(x) }
                var e: SevenZArchiveEntry? = next()
                while (e != null && clean(e.name ?: "") != wanted) e = next()
                if (e == null) { z.close(); throw IOException("Not found in the archive") }
                val encrypted = e.isEncrypted7z()
                if (encrypted && pw == null) { z.close(); throw ArchivePasswordRequiredException() }
                val raw = object : InputStream() {
                    override fun read(): Int = z.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = z.read(b, off, len)
                    override fun close() { z.close() }
                }
                return GuardedStream(raw, map7z(encrypted, pw))
            }
            k.isSingleFile -> {
                if (wanted != singleName()) throw IOException("Not found in the archive")
                return singleStream()
            }
            else -> {
                val s = sequentialStream()
                var e: ArchiveEntry? = s.nextEntry
                while (e != null && clean(e.name) != wanted) e = s.nextEntry
                if (e == null) { s.close(); throw IOException("Not found in the archive") }
                return object : java.io.FilterInputStream(s) { override fun close() { s.close() } }
            }
        }
    }

    /**
     * Tries [pw] on this archive: reads the start of the first encrypted file. Throws
     * [ArchiveWrongPasswordException] or [ArchivePasswordRequiredException]; returns when the password works
     * (or the archive has nothing encrypted that could be checked).
     */
    private fun check(pw: CharArray) {
        candidate = pw
        try {
            val probe = items.firstOrNull { it.encrypted && !it.isDir && it.size != 0L } ?: return
            val limit = 16L * 1024 * 1024
            openInner(probe.path).use { s ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (total < limit) {
                    val n = s.read(buffer)
                    if (n < 0) break
                    total += n
                }
            }
        } finally {
            candidate = null
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<String, ArchiveFs>()
        fun of(archive: String): ArchiveFs {
            val file = File(archive)
            val cached = cache[archive]
            // the index is read once: a changed archive file needs a new one
            if (cached != null && cached.stamp == file.lastModified() + file.length()) return cached
            return ArchiveFs(file).also { cache[archive] = it }
        }
        fun forget(archive: String) { cache.remove(archive) }

        /** Checks [password] against [archive] without keeping it. Throws [ArchiveCryptoException] when it does not open the archive. */
        fun checkPassword(archive: File, password: CharArray) {
            ArchiveFs(archive).check(password)
        }
    }
}
