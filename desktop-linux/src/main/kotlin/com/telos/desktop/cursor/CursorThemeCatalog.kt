package com.telos.desktop.cursor

import com.telos.desktop.icons.FreedesktopThemeRoots
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.ZipInputStream

data class CursorCatalogEntry(
    val id: String,
    val displayName: String,
    val license: String,
    val homepage: String,
    /** Direct archive URL that expands to a theme directory with cursors/ (+ index.theme). */
    val zipUrl: String,
    /** Directory name expected after extract (theme id under icons/). */
    val themeFolder: String,
)

/**
 * Optional OSS cursor theme downloads into the portable/vault icons root.
 *
 * Shares the Freedesktop `<icons>/<ThemeName>/cursors` layout with icon packs.
 * Does not implement or rewrite any icon-pack downloader.
 *
 * Licenses are GPL/LGPL-family only (compatible with Telos GPL-3.0).
 * BreezeX is a Breeze-inspired GPL-3.0 XCursor pack (ful1e5); Bibata is GPL-3.0.
 * Distro-packaged Plasma `breeze_cursors` under `/usr/share/icons` is discovered
 * without downloading.
 */
object CursorThemeCatalog {
    val entries: List<CursorCatalogEntry> = listOf(
        CursorCatalogEntry(
            id = "breezex-dark",
            displayName = "BreezeX Dark",
            license = "GPL-3.0-or-later",
            homepage = "https://github.com/ful1e5/BreezeX_Cursor",
            zipUrl = "https://github.com/ful1e5/BreezeX_Cursor/releases/download/v2.0.1/BreezeX-Dark.tar.gz",
            themeFolder = "BreezeX-Dark",
        ),
        CursorCatalogEntry(
            id = "breezex-light",
            displayName = "BreezeX Light",
            license = "GPL-3.0-or-later",
            homepage = "https://github.com/ful1e5/BreezeX_Cursor",
            zipUrl = "https://github.com/ful1e5/BreezeX_Cursor/releases/download/v2.0.1/BreezeX-Light.tar.gz",
            themeFolder = "BreezeX-Light",
        ),
        CursorCatalogEntry(
            id = "bibata-modern-classic",
            displayName = "Bibata Modern Classic",
            license = "GPL-3.0-or-later",
            homepage = "https://github.com/ful1e5/Bibata_Cursor",
            zipUrl = "https://github.com/ful1e5/Bibata_Cursor/releases/download/v2.0.7/Bibata-Modern-Classic.tar.gz",
            themeFolder = "Bibata-Modern-Classic",
        ),
        CursorCatalogEntry(
            id = "bibata-original-classic",
            displayName = "Bibata Original Classic",
            license = "GPL-3.0-or-later",
            homepage = "https://github.com/ful1e5/Bibata_Cursor",
            zipUrl = "https://github.com/ful1e5/Bibata_Cursor/releases/download/v2.0.7/Bibata-Original-Classic.tar.gz",
            themeFolder = "Bibata-Original-Classic",
        ),
    )

    fun offered(): List<CursorCatalogEntry> = entries

    data class DownloadResult(val ok: Boolean, val themeFolder: String?, val message: String)

    fun download(entry: CursorCatalogEntry, destRoot: File = FreedesktopThemeRoots.installRoot()): DownloadResult {
        return try {
            destRoot.mkdirs()
            val lower = entry.zipUrl.lowercase()
            val tmp = File.createTempFile(
                "telos-cursor-",
                when {
                    lower.endsWith(".zip") -> ".zip"
                    else -> ".tar.gz"
                },
            )
            try {
                URI(entry.zipUrl).toURL().openStream().use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                if (lower.endsWith(".zip")) unzip(tmp, destRoot) else untarGz(tmp, destRoot)
                val themeDir = File(destRoot, entry.themeFolder)
                if (!themeDir.isDirectory) {
                    val found = destRoot.listFiles()?.firstOrNull {
                        it.isDirectory && it.name.contains(entry.themeFolder, ignoreCase = true)
                    }
                    if (found != null && found != themeDir && !themeDir.exists()) {
                        found.renameTo(themeDir)
                    }
                }
                ensureIndexTheme(File(destRoot, entry.themeFolder), entry.displayName)
                if (!File(destRoot, entry.themeFolder).isDirectory) {
                    return DownloadResult(false, null, "Archive extracted but theme folder missing")
                }
                DownloadResult(true, entry.themeFolder, "Installed ${entry.displayName}")
            } finally {
                tmp.delete()
            }
        } catch (e: Exception) {
            DownloadResult(false, null, e.message ?: "download failed")
        }
    }

    private fun ensureIndexTheme(dir: File, displayName: String) {
        if (!dir.isDirectory) return
        val index = File(dir, "index.theme")
        if (index.isFile) {
            val text = index.readText()
            if (!text.contains("[Cursor Theme]") && File(dir, "cursors").isDirectory) {
                index.appendText("\n[Cursor Theme]\nName=$displayName\n")
            }
            return
        }
        if (File(dir, "cursors").isDirectory) {
            index.writeText(
                """
                [Icon Theme]
                Name=$displayName
                Comment=Telos Desktop cursor theme
                Directories=cursors

                [Cursor Theme]
                Name=$displayName
                """.trimIndent() + "\n",
            )
        }
    }

    private fun unzip(zip: File, dest: File): File? {
        var top: File? = null
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(dest, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                    if (top == null) top = out
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                    if (top == null) top = out.parentFile
                }
                zis.closeEntry()
            }
        }
        return top
    }

    private fun untarGz(archive: File, dest: File): File? {
        val pb = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", dest.absolutePath)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) throw IllegalStateException("tar failed: $out")
        return dest.listFiles()?.firstOrNull { it.isDirectory }
    }
}
