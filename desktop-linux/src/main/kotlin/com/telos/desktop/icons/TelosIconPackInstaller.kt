package com.telos.desktop.icons

import com.telos.desktop.files.ZipArchives
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Downloads a catalog archive and installs freedesktop icon theme directories
 * under [TelosIconPackPaths.iconsRoot]. Skips cursor-only theme indexes.
 */
object TelosIconPackInstaller {
    data class Result(
        val ok: Boolean,
        val themeNames: List<String> = emptyList(),
        val message: String = "",
    )

    fun isInstalled(entry: TelosIconPackEntry, resolver: LinuxIconThemeResolver = LinuxIconThemeResolver()): Boolean {
        val names = resolver.listInstalledThemes().map { it.name }.toSet()
        if (entry.preferredThemeDirs.any { it in names }) return true
        return names.any { it.equals(entry.displayName, ignoreCase = true) || it.equals(entry.id, ignoreCase = true) }
    }

    fun install(entry: TelosIconPackEntry): Result {
        val root = TelosIconPackPaths.iconsRoot()
        if (TelosIconPackPaths.usePortableIcons()) {
            runCatching {
                com.telos.desktop.vault.PortableTraceGuard.assertNoHostWrites(root.parentFile ?: root)
            }.onFailure {
                return Result(false, message = it.message ?: "portable path rejected")
            }
        }
        val downloads = TelosIconPackPaths.downloadsDir()
        val staging = File(TelosIconPackPaths.stagingDir(), entry.id).also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val archiveName = archiveFileName(entry.archiveUrl)
        val archiveFile = File(downloads, "${entry.id}-$archiveName")
        return try {
            download(entry.archiveUrl, archiveFile)
            if (!extractArchive(archiveFile, staging)) {
                return Result(false, message = "extract failed")
            }
            val themes = findIconThemeDirs(staging)
            if (themes.isEmpty()) {
                return Result(false, message = "no Icon Theme index.theme found")
            }
            val installed = ArrayList<String>()
            for (themeDir in themes) {
                val dest = File(root, themeDir.name)
                if (dest.exists()) dest.deleteRecursively()
                if (!themeDir.renameTo(dest)) {
                    themeDir.copyRecursively(dest, overwrite = true)
                    themeDir.deleteRecursively()
                }
                installed += dest.name
            }
            Result(ok = installed.isNotEmpty(), themeNames = installed, message = "ok")
        } catch (t: Throwable) {
            Result(false, message = t.message ?: t.javaClass.simpleName)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun findIconThemeDirs(root: File): List<File> {
        val out = ArrayList<File>()
        root.walkTopDown().maxDepth(6).forEach { f ->
            if (!f.isFile || f.name != "index.theme") return@forEach
            if (isCursorOnlyTheme(f)) return@forEach
            if (!hasIconThemeSection(f)) return@forEach
            val dir = f.parentFile ?: return@forEach
            if (out.any { dir.path.startsWith(it.path + File.separator) }) return@forEach
            out += dir
        }
        return out.distinctBy { it.canonicalFile }
    }

    private fun hasIconThemeSection(index: File): Boolean {
        val text = runCatching { index.readText() }.getOrNull() ?: return false
        return text.lines().any { it.trim() == "[Icon Theme]" }
    }

    private fun isCursorOnlyTheme(index: File): Boolean {
        val text = runCatching { index.readText() }.getOrNull() ?: return false
        val hasCursor = text.lines().any { it.trim() == "[Cursor Theme]" }
        val hasIcon = text.lines().any { it.trim() == "[Icon Theme]" }
        return hasCursor && !hasIcon
    }

    private fun archiveFileName(url: String): String {
        val path = URI(url).path
        val base = path.substringAfterLast('/').ifBlank { "pack.bin" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun download(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "TelosDesktop-IconPack/1.0")
            setRequestProperty("Accept", "*/*")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        conn.disconnect()
    }

    private fun extractArchive(archive: File, destDir: File): Boolean {
        val name = archive.name.lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTarGz(archive, destDir)
            name.endsWith(".tar") -> ZipArchives.extractAll(archive, destDir)
            name.endsWith(".zip") -> ZipArchives.extractAll(archive, destDir)
            else -> {
                val magic = archive.inputStream().use { i -> ByteArray(2).also { i.read(it) } }
                if (magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()) extractTarGz(archive, destDir)
                else ZipArchives.extractAll(archive, destDir)
            }
        }
    }

    private fun extractTarGz(archive: File, destDir: File): Boolean {
        val named = if (archive.name.lowercase().endsWith(".tar.gz") || archive.name.lowercase().endsWith(".tgz")) {
            archive
        } else {
            File(archive.parentFile, archive.name + ".tar.gz").also { archive.copyTo(it, overwrite = true) }
        }
        return ZipArchives.extractAll(named, destDir)
    }
}
