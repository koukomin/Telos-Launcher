package com.telos.desktop.files

import com.telos.desktop.linux.Sys
import net.lingala.zip4j.ZipFile as Zip4jFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ArchivePasswordException : Exception()

data class ZipChild(
    val name: String,
    val inner: String,
    val directory: Boolean,
    val size: Long,
)

object ZipArchives {
    private val zipExt = setOf("zip", "jar", "apk")
    private val sevenExt = setOf("7z")
    private val rarExt = setOf("rar")
    private val tarExt = setOf("tar", "tgz", "tbz", "txz")

    fun isZip(file: File): Boolean = isArchive(file)

    fun isArchive(file: File): Boolean {
        if (!file.isFile) return false
        val n = file.name.lowercase()
        if (n.endsWith(".tar.gz") || n.endsWith(".tar.bz2") || n.endsWith(".tar.xz")) return true
        return file.extension.lowercase() in zipExt + sevenExt + rarExt + tarExt
    }

    fun safeInner(path: String): String? {
        val n = path.replace('\\', '/').trimStart('/')
        if (n.isEmpty() || n.startsWith("/") || n.split('/').any { it == ".." }) return null
        return n
    }

    fun listChildren(archive: File, prefix: String, password: CharArray? = null): List<ZipChild> {
        val names = entryNames(archive, password)
        return childrenFromNames(names, prefix)
    }

    fun extractEntry(archive: File, inner: String, destDir: File, password: CharArray? = null): File? {
        val safe = safeInner(inner) ?: return null
        destDir.mkdirs()
        when {
            isPlainZip(archive) && password == null -> {
                ZipFile(archive).use { zip ->
                    val e = zip.getEntry(safe) ?: zip.getEntry("$safe/") ?: return null
                    val out = File(destDir, File(safe).name)
                    if (e.isDirectory) {
                        out.mkdirs()
                        return out
                    }
                    zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                    return out
                }
            }
            archive.extension.lowercase() in zipExt -> {
                val z = zip4j(archive, password)
                val header = z.fileHeaders.firstOrNull { it.fileName.trimEnd('/') == safe } ?: return null
                z.extractFile(header, destDir.absolutePath)
                return File(destDir, File(safe).name)
            }
            archive.extension.lowercase() == "7z" -> {
                sevenZ(archive, password).use { sz ->
                    val e = generateSequence { sz.nextEntry }.firstOrNull { it.name.trimEnd('/') == safe } ?: return null
                    val out = File(destDir, File(safe).name)
                    if (e.isDirectory) {
                        out.mkdirs()
                        return out
                    }
                    out.outputStream().use { sz.getInputStream(e).copyTo(it) }
                    return out
                }
            }
            else -> {
                val outDir = File(destDir, File(safe).name)
                return if (hostExtract(archive, destDir, safe, password)) File(destDir, File(safe).name).takeIf { it.exists() } ?: outDir.takeIf { it.exists() } else null
            }
        }
    }

    fun extractAll(archive: File, destDir: File, password: CharArray? = null): Boolean {
        destDir.mkdirs()
        return when {
            isPlainZip(archive) && password == null -> extractZipJdk(archive, destDir)
            archive.extension.lowercase() in zipExt -> {
                zip4j(archive, password).extractAll(destDir.absolutePath)
                true
            }
            archive.extension.lowercase() == "7z" -> {
                sevenZ(archive, password).use { sz ->
                    while (true) {
                        val e = sz.nextEntry ?: break
                        val safe = safeInner(e.name) ?: continue
                        val out = File(destDir, safe)
                        if (!inside(destDir, out)) continue
                        if (e.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { sz.getInputStream(e).copyTo(it) }
                        }
                    }
                }
                true
            }
            isTar(archive) -> extractTar(archive, destDir)
            else -> hostExtract(archive, destDir, null, password)
        }
    }

    fun compressDir(dir: File, dest: File): Boolean {
        if (!dir.isDirectory) return false
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            dir.walkTopDown().forEach { file ->
                if (file == dir) return@forEach
                val rel = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                if (rel.split('/').any { it == ".." }) return@forEach
                if (file.isDirectory) {
                    zos.putNextEntry(ZipEntry("$rel/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return dest.isFile
    }

    internal fun childrenFromNames(names: List<Triple<String, Boolean, Long>>, prefix: String): List<ZipChild> {
        val want = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val seen = LinkedHashMap<String, ZipChild>()
        for ((raw, isDir, size) in names) {
            val inner = safeInner(raw) ?: continue
            if (want.isNotEmpty() && !inner.startsWith(want)) continue
            val rest = if (want.isEmpty()) inner else inner.removePrefix(want)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            val name = if (slash < 0) rest else rest.substring(0, slash)
            val directory = slash >= 0 || isDir
            val childInner = want + name
            seen.putIfAbsent(name, ZipChild(name, childInner, directory, if (directory) 0 else size))
        }
        return seen.values.sortedWith(compareBy({ !it.directory }, { it.name.lowercase() }))
    }

    private fun entryNames(archive: File, password: CharArray?): List<Triple<String, Boolean, Long>> {
        val ext = archive.extension.lowercase()
        return when {
            isPlainZip(archive) && password == null -> jdkZipNames(archive)
            ext in zipExt -> zip4jNames(archive, password)
            ext == "7z" -> sevenNames(archive, password)
            isTar(archive) -> tarNames(archive)
            ext == "rar" -> hostList(archive, password)
            else -> jdkZipNames(archive)
        }
    }

    private fun isPlainZip(archive: File): Boolean =
        archive.extension.lowercase() in zipExt && !zip4jEncrypted(archive)

    private fun zip4jEncrypted(archive: File): Boolean =
        runCatching { Zip4jFile(archive).use { it.isEncrypted } }.getOrDefault(false)

    private fun zip4j(archive: File, password: CharArray?): Zip4jFile {
        val z = if (password != null && password.isNotEmpty()) Zip4jFile(archive, password) else Zip4jFile(archive)
        if (z.isEncrypted && (password == null || password.isEmpty())) {
            z.close()
            throw ArchivePasswordException()
        }
        return z
    }

    private fun zip4jNames(archive: File, password: CharArray?): List<Triple<String, Boolean, Long>> {
        zip4j(archive, password).use { z ->
            return z.fileHeaders.map { Triple(it.fileName, it.isDirectory, it.uncompressedSize) }
        }
    }

    private fun jdkZipNames(archive: File): List<Triple<String, Boolean, Long>> {
        return try {
            ZipFile(archive).use { zip ->
                zip.entries().toList().map { Triple(it.name, it.isDirectory, it.size) }
            }
        } catch (_: ZipException) {
            zip4jNames(archive, null)
        }
    }

    private fun sevenZ(archive: File, password: CharArray?): SevenZFile {
        val builder = SevenZFile.builder().setFile(archive)
        if (password != null && password.isNotEmpty()) builder.setPassword(password)
        return try {
            builder.get()
        } catch (e: Exception) {
            if (password == null || password.isEmpty()) throw ArchivePasswordException()
            throw e
        }
    }

    private fun sevenNames(archive: File, password: CharArray?): List<Triple<String, Boolean, Long>> {
        sevenZ(archive, password).use { sz ->
            val out = ArrayList<Triple<String, Boolean, Long>>()
            while (true) {
                val e = sz.nextEntry ?: break
                out += Triple(e.name, e.isDirectory, e.size)
            }
            return out
        }
    }

    private fun isTar(archive: File): Boolean {
        val n = archive.name.lowercase()
        return n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz") ||
            n.endsWith(".tar.bz2") || n.endsWith(".tbz") || n.endsWith(".tar.xz") || n.endsWith(".txz")
    }

    private fun tarStream(archive: File): TarArchiveInputStream {
        val raw = archive.inputStream().buffered()
        val n = archive.name.lowercase()
        val wrapped = when {
            n.endsWith(".gz") || n.endsWith(".tgz") -> GzipCompressorInputStream(raw)
            n.endsWith(".bz2") || n.endsWith(".tbz") -> BZip2CompressorInputStream(raw)
            n.endsWith(".xz") || n.endsWith(".txz") -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(wrapped)
    }

    private fun tarNames(archive: File): List<Triple<String, Boolean, Long>> {
        tarStream(archive).use { tar ->
            val out = ArrayList<Triple<String, Boolean, Long>>()
            while (true) {
                val e = tar.nextEntry ?: break
                out += Triple(e.name, e.isDirectory, e.size)
            }
            return out
        }
    }

    private fun extractTar(archive: File, destDir: File): Boolean {
        tarStream(archive).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                val safe = safeInner(e.name) ?: continue
                val out = File(destDir, safe)
                if (!inside(destDir, out)) continue
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
        return true
    }

    private fun extractZipJdk(archive: File, destDir: File): Boolean {
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val safe = safeInner(e.name) ?: continue
                val out = File(destDir, safe)
                if (!inside(destDir, out)) continue
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        return true
    }

    private fun hostList(archive: File, password: CharArray?): List<Triple<String, Boolean, Long>> {
        val seven = Sys.which("7z") ?: Sys.which("7za") ?: return emptyList()
        val args = mutableListOf(seven, "l", "-ba", "--", archive.absolutePath)
        val r = Sys.run(*args.toTypedArray(), timeoutSec = 30)
        if (!r.ok) return emptyList()
        return r.stdout.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            if (parts.size < 6) return@mapNotNull null
            val attr = parts[2]
            val size = parts[3].toLongOrNull() ?: 0L
            val name = parts[5]
            Triple(name, attr.startsWith("D"), size)
        }.toList()
    }

    private fun hostExtract(archive: File, destDir: File, inner: String?, password: CharArray?): Boolean {
        val seven = Sys.which("7z") ?: Sys.which("7za") ?: return false
        destDir.mkdirs()
        val args = mutableListOf(seven, "x", "-y", "-o${destDir.absolutePath}", "--", archive.absolutePath)
        if (inner != null) args += inner
        return Sys.run(*args.toTypedArray(), timeoutSec = 120).ok
    }

    private fun inside(root: File, child: File): Boolean {
        val r = root.canonicalPath
        val c = child.canonicalPath
        return c == r || c.startsWith(r + File.separator)
    }
}
