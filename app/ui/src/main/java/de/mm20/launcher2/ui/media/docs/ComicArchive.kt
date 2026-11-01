package de.mm20.launcher2.ui.media.docs

import de.mm20.launcher2.ui.files.ArchiveRarUnavailableException
import de.mm20.launcher2.ui.files.RarBackend
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.Locale

/** Pages of a comic archive (CBZ zip, CBR rar, CBT tar, CB7 7z), as files in [outDir]. */
internal object ComicArchive {
    private val images = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "jxl")

    fun isComic(name: String) = DocumentTypes.ext(name) in setOf("cbz", "cbr", "cbt", "cb7")

    /** First image of a comic, for Files thumbnails, without unpacking the whole archive. */
    fun firstImageBytes(file: File, name: String, maxBytes: Int): ByteArray? = runCatching {
        when (DocumentTypes.ext(name)) {
            "cbz" -> zipFirst(file, maxBytes)
            "cbr" -> rarFirst(file, maxBytes)
            "cbt" -> tarFirst(file, maxBytes)
            "cb7" -> sevenFirst(file, maxBytes)
            else -> null
        }
    }.getOrNull()

    fun extract(file: File, name: String, outDir: File): List<File> {
        outDir.mkdirs()
        val pages = when (DocumentTypes.ext(name)) {
            "cbz" -> zip(file, outDir)
            "cbr" -> rar(file, outDir)
            "cbt" -> tar(file, outDir)
            "cb7" -> seven(file, outDir)
            else -> emptyList()
        }
        return pages.sortedWith(compareBy(naturalName) { it.name })
    }

    private fun zipFirst(file: File, maxBytes: Int): ByteArray? {
        ZipFile(file).use { z ->
            val entry = z.entries().toList().filter { !it.isDirectory && isImage(it.name) }
                .minWithOrNull(compareBy(naturalName) { it.name }) ?: return null
            if (entry.size > maxBytes) return null
            return z.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun rarFirst(file: File, maxBytes: Int): ByteArray? {
        if (!RarBackend.isAvailable) return null
        RarBackend.open(file).use { session ->
            while (true) {
                val e = session.next() ?: break
                if (e.isDir || e.isLink || e.encrypted || !isImage(e.path)) continue
                if (e.size > maxBytes) return null
                return session.openCurrent().use { it.readBytes() }
            }
        }
        return null
    }

    private fun tarFirst(file: File, maxBytes: Int): ByteArray? {
        TarArchiveInputStream(file.inputStream().buffered()).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                if (e.isDirectory || !isImage(e.name)) continue
                if (e.size > maxBytes) return null
                return tar.readBytes()
            }
        }
        return null
    }

    private fun sevenFirst(file: File, maxBytes: Int): ByteArray? {
        SevenZFile(file).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || !isImage(e.name)) continue
                if (e.size > maxBytes) return null
                return z.getInputStream(e).readBytes()
            }
        }
        return null
    }

    private val naturalName = Comparator<String> { a, b ->
        val x = a.lowercase(Locale.ROOT)
        val y = b.lowercase(Locale.ROOT)
        val rx = Regex("(\\d+)|(\\D+)")
        val ax = rx.findAll(x).map { it.value }.toList()
        val ay = rx.findAll(y).map { it.value }.toList()
        val n = minOf(ax.size, ay.size)
        for (i in 0 until n) {
            val da = ax[i].toIntOrNull()
            val db = ay[i].toIntOrNull()
            val c = if (da != null && db != null) da.compareTo(db) else ax[i].compareTo(ay[i])
            if (c != 0) return@Comparator c
        }
        ax.size.compareTo(ay.size)
    }

    private fun zip(file: File, outDir: File): List<File> {
        val out = ArrayList<File>()
        ZipFile(file).use { z ->
            val entries = z.entries().toList().filter { e -> !e.isDirectory && isImage(e.name) }
            for (e in entries) {
                val dest = safeDest(outDir, e.name) ?: continue
                z.getInputStream(e).use { input -> dest.outputStream().use { input.copyTo(it) } }
                out += dest
            }
        }
        return out
    }

    private fun rar(file: File, outDir: File): List<File> {
        if (!RarBackend.isAvailable) throw ArchiveRarUnavailableException()
        val out = ArrayList<File>()
        RarBackend.open(file).use { session ->
            while (true) {
                val e = session.next() ?: break
                if (e.isDir || e.isLink || e.encrypted || !isImage(e.path)) continue
                val dest = safeDest(outDir, e.path) ?: continue
                session.copyCurrentTo(dest)
                out += dest
            }
        }
        return out
    }

    private fun tar(file: File, outDir: File): List<File> {
        val out = ArrayList<File>()
        TarArchiveInputStream(file.inputStream().buffered()).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                if (e.isDirectory || !isImage(e.name)) continue
                val dest = safeDest(outDir, e.name) ?: continue
                dest.outputStream().use { tar.copyTo(it) }
                out += dest
            }
        }
        return out
    }

    private fun seven(file: File, outDir: File): List<File> {
        val out = ArrayList<File>()
        SevenZFile(file).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || !isImage(e.name)) continue
                val dest = safeDest(outDir, e.name) ?: continue
                dest.outputStream().use { z.getInputStream(e).copyTo(it) }
                out += dest
            }
        }
        return out
    }

    private fun isImage(path: String) =
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in images && !path.startsWith(".") && !path.contains("/.")

    /** Basename only, then canonical-path check so Zip Slip (`../`) cannot leave [outDir]. */
    private fun safeDest(outDir: File, entryName: String): File? {
        val base = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (base.isEmpty() || base == "." || base == ".." || base.contains("..")) return null
        val dest = File(outDir, base)
        val root = outDir.canonicalFile
        val resolved = dest.canonicalFile
        val prefix = root.path + File.separator
        if (resolved != root && !resolved.path.startsWith(prefix)) return null
        return dest
    }
}
