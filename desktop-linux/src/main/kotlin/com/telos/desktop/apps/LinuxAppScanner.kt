package com.telos.desktop.apps

import java.io.File
import java.util.Locale

object LinuxAppScanner {
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
        for (dir in searchDirs) {
            if (!dir.isDirectory) continue
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".desktop") } ?: continue
            for (file in files) {
                val parsed = DesktopFileParser.parse(file, localeKeys) ?: continue
                if (parsed.noDisplay) continue
                if (parsed.exec.isBlank()) continue
                seen.putIfAbsent(file.name, parsed)
            }
        }
        return seen.values.sortedBy { it.name.lowercase(locale) }
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
