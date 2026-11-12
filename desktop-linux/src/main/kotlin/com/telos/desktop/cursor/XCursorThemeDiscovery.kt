package com.telos.desktop.cursor

import com.telos.desktop.icons.FreedesktopThemeRoots
import java.io.File

data class XCursorThemeInfo(
    val name: String,
    val displayName: String,
    val directory: File,
    val inherits: List<String>,
    val comment: String?,
    val hasCursorsDir: Boolean,
)

/**
 * Discovers installed XCursor / Plasma cursor themes under Freedesktop icon roots.
 *
 * A directory counts as a cursor theme when it has a `cursors/` folder and/or an
 * `index.theme` with a `[Cursor Theme]` section (KDE/Plasma) or `Directories=cursors`.
 */
object XCursorThemeDiscovery {
    fun listInstalled(includeHostSystem: Boolean = true): List<XCursorThemeInfo> {
        val out = ArrayList<XCursorThemeInfo>()
        for (root in FreedesktopThemeRoots.searchRoots(includeHostSystem)) {
            val children = root.listFiles() ?: continue
            for (dir in children) {
                if (!dir.isDirectory) continue
                parseTheme(dir)?.let { out += it }
            }
        }
        return out.distinctBy { it.name.lowercase() }.sortedBy { it.displayName.lowercase() }
    }

    fun find(name: String, includeHostSystem: Boolean = true): XCursorThemeInfo? {
        if (name.isBlank()) return null
        return listInstalled(includeHostSystem).firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun resolveCursorFile(theme: XCursorThemeInfo, shape: String, inheritThemes: Map<String, XCursorThemeInfo>): File? {
        val seen = HashSet<String>()
        fun walk(info: XCursorThemeInfo): File? {
            if (!seen.add(info.name.lowercase())) return null
            val direct = File(info.directory, "cursors/$shape")
            if (direct.isFile) return direct
            // Some packs use size subfolders; walk shallow.
            val cursors = File(info.directory, "cursors")
            if (cursors.isDirectory) {
                cursors.listFiles()?.firstOrNull { it.isFile && it.name == shape }?.let { return it }
            }
            for (parent in info.inherits) {
                val next = inheritThemes[parent] ?: inheritThemes.entries
                    .firstOrNull { it.key.equals(parent, ignoreCase = true) }?.value
                    ?: continue
                walk(next)?.let { return it }
            }
            return null
        }
        return walk(theme)
    }

    internal fun parseTheme(dir: File): XCursorThemeInfo? {
        val cursorsDir = File(dir, "cursors")
        val hasCursors = cursorsDir.isDirectory && (cursorsDir.list()?.isNotEmpty() == true)
        val index = File(dir, "index.theme")
        if (!index.isFile && !hasCursors) return null
        if (!index.isFile) {
            return XCursorThemeInfo(
                name = dir.name,
                displayName = dir.name,
                directory = dir,
                inherits = emptyList(),
                comment = null,
                hasCursorsDir = true,
            )
        }
        val sections = parseIniSections(index)
        val cursorSection = sections["Cursor Theme"]
        val iconSection = sections["Icon Theme"]
        val isCursorTheme = cursorSection != null ||
            hasCursors ||
            (iconSection?.get("Directories")?.split(',')?.any { it.trim() == "cursors" } == true)
        if (!isCursorTheme) return null
        val name = cursorSection?.get("Name")
            ?: iconSection?.get("Name")
            ?: dir.name
        val inheritsRaw = cursorSection?.get("Inherits")
            ?: iconSection?.get("Inherits")
            ?: ""
        val inherits = inheritsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val comment = cursorSection?.get("Comment") ?: iconSection?.get("Comment")
        return XCursorThemeInfo(
            name = dir.name,
            displayName = name,
            directory = dir,
            inherits = inherits,
            comment = comment,
            hasCursorsDir = hasCursors,
        )
    }

    private fun parseIniSections(file: File): Map<String, Map<String, String>> {
        val values = LinkedHashMap<String, LinkedHashMap<String, String>>()
        var section = ""
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                values.putIfAbsent(section, LinkedHashMap())
                return@forEach
            }
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            values.getOrPut(section) { LinkedHashMap() }[line.substring(0, eq).trim()] =
                line.substring(eq + 1).trim().trim('"')
        }
        return values
    }
}
