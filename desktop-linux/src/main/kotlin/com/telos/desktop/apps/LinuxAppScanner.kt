package com.telos.desktop.apps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object LinuxAppScanner {
    private data class Cached(val mtime: Long, val entry: DesktopEntry?)

    private val fileCache = ConcurrentHashMap<String, Cached>()

    private val searchDirs: List<File>
        get() {
            val home = System.getProperty("user.home")
            return listOf(
                File("/usr/share/applications"),
                File("/usr/local/share/applications"),
                File("$home/.local/share/applications"),
                File("/var/lib/flatpak/exports/share/applications"),
                File("$home/.local/share/flatpak/exports/share/applications"),
            )
        }

    fun scan(locale: Locale = Locale.getDefault()): List<DesktopEntry> {
        val localeKeys = localeNameKeys(locale)
        val seen = LinkedHashMap<String, DesktopEntry>()
        val parsed = runBlocking(Dispatchers.Default) {
            searchDirs.map { dir ->
                async(Dispatchers.IO) { scanDir(dir, localeKeys) }
            }.awaitAll()
        }
        for (list in parsed) {
            for (entry in list) {
                seen.putIfAbsent(entry.file.name, entry)
            }
        }
        return seen.values.sortedBy { it.name.lowercase(locale) }
    }

    private fun scanDir(dir: File, localeKeys: List<String>): List<DesktopEntry> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".desktop") } ?: return emptyList()
        val out = ArrayList<DesktopEntry>(files.size)
        for (file in files) {
            val mtime = file.lastModified()
            val path = file.absolutePath
            val cached = fileCache[path]
            val entry = if (cached != null && cached.mtime == mtime) {
                cached.entry
            } else {
                val parsed = DesktopFileParser.parse(file, localeKeys)
                val keep = parsed?.takeUnless { it.noDisplay || it.exec.isBlank() }
                fileCache[path] = Cached(mtime, keep)
                keep
            }
            if (entry != null) out += entry
        }
        return out
    }

    fun mapCategory(freedesktop: List<String>): TelosAppCategory {
        val set = freedesktop.toSet()
        return when {
            "Settings" in set || "DesktopSettings" in set -> TelosAppCategory.Settings
            "Development" in set || "IDE" in set -> TelosAppCategory.Development
            "Game" in set -> TelosAppCategory.Game
            "AudioVideo" in set || "Audio" in set || "Video" in set -> TelosAppCategory.AudioVideo
            "Graphics" in set -> TelosAppCategory.Graphics
            "Network" in set || "WebBrowser" in set || "Email" in set -> TelosAppCategory.Network
            "Office" in set || "WordProcessor" in set || "Spreadsheet" in set -> TelosAppCategory.Office
            "System" in set || "Monitor" in set || "TerminalEmulator" in set -> TelosAppCategory.System
            "Utility" in set || "Accessories" in set -> TelosAppCategory.Utility
            else -> TelosAppCategory.Other
        }
    }

    private fun localeNameKeys(locale: Locale): List<String> {
        val lang = locale.language
        val country = locale.country
        val keys = mutableListOf<String>()
        if (lang.isNotEmpty() && country.isNotEmpty()) keys += "Name[${lang}_$country]"
        if (lang.isNotEmpty()) keys += "Name[$lang]"
        keys += "Name"
        return keys
    }
}
