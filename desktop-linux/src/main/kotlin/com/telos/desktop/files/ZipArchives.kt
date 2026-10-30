package com.telos.desktop.files

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ZipChild(
    val name: String,
    val inner: String,
    val directory: Boolean,
    val size: Long,
)

object ZipArchives {
    private val zipExt = setOf("zip", "jar", "apk")

    fun isZip(file: File): Boolean =
        file.isFile && file.extension.lowercase() in zipExt

    fun safeInner(path: String): String? {
        val n = path.replace('\\', '/').trimStart('/')
        if (n.isEmpty() || n.startsWith("/") || n.split('/').any { it == ".." }) return null
        return n
    }

    fun listChildren(archive: File, prefix: String): List<ZipChild> {
        if (!isZip(archive)) return emptyList()
        val want = prefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val seen = LinkedHashMap<String, ZipChild>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val inner = safeInner(e.name) ?: continue
                if (want.isNotEmpty() && !inner.startsWith(want)) continue
                val rest = if (want.isEmpty()) inner else inner.removePrefix(want)
                if (rest.isEmpty()) continue
                val slash = rest.indexOf('/')
                val name = if (slash < 0) rest else rest.substring(0, slash)
                val directory = slash >= 0 || e.isDirectory
                val childInner = want + name + if (directory) "/" else ""
                seen.putIfAbsent(
                    name,
                    ZipChild(name, childInner.trimEnd('/'), directory, if (directory) 0 else e.size),
                )
            }
        }
        return seen.values.sortedWith(compareBy({ !it.directory }, { it.name.lowercase() }))
    }

    fun extractEntry(archive: File, inner: String, destDir: File): File? {
        val safe = safeInner(inner) ?: return null
        ZipFile(archive).use { zip ->
            val e = zip.getEntry(safe) ?: zip.getEntry("$safe/") ?: return null
            val out = File(destDir, File(safe).name)
            if (e.isDirectory) {
                out.mkdirs()
                return out
            }
            destDir.mkdirs()
            zip.getInputStream(e).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            return out
        }
    }

    fun extractAll(archive: File, destDir: File): Boolean {
        if (!isZip(archive)) return false
        destDir.mkdirs()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val safe = safeInner(e.name) ?: continue
                val out = File(destDir, safe)
                if (!out.canonicalPath.startsWith(destDir.canonicalPath + File.separator) &&
                    out.canonicalPath != destDir.canonicalPath
                ) {
                    continue
                }
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        return true
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
}
