package com.telos.desktop.apps

import java.io.File

object DesktopFileParser {
    fun parse(file: File, localeNameKeys: List<String> = listOf("Name")): DesktopEntry? {
        if (!file.isFile) return null
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        var inEntry = false
        val map = LinkedHashMap<String, String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                inEntry = line == "[Desktop Entry]"
                continue
            }
            if (!inEntry) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = unescape(line.substring(eq + 1).trim())
            map.putIfAbsent(key, value)
        }
        if (map["Type"] == "Link") return null
        val name = localeNameKeys.firstNotNullOfOrNull { map[it] } ?: map["Name"] ?: file.nameWithoutExtension
        val categories = (map["Categories"] ?: "").split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return DesktopEntry(
            file = file,
            name = name,
            comment = map["Comment"] ?: "",
            icon = map["Icon"] ?: "",
            exec = map["Exec"] ?: "",
            path = map["Path"]?.takeIf { it.isNotBlank() },
            terminal = map["Terminal"].equals("true", ignoreCase = true),
            noDisplay = map["NoDisplay"].equals("true", ignoreCase = true) ||
                map["Hidden"].equals("true", ignoreCase = true),
            categories = categories,
            telosCategory = LinuxAppScanner.mapCategory(categories),
        )
    }

    private fun unescape(value: String): String =
        value.replace("\\s", " ").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
