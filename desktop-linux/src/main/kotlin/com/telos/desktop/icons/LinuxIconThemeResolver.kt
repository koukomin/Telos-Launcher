package com.telos.desktop.icons

import java.io.File

data class IconThemeInfo(
    val name: String,
    val directory: File,
    val inherits: List<String>,
    val directories: List<ThemeDir>,
)

data class ThemeDir(
    val path: String,
    val size: Int,
    val scale: Int,
    val context: String,
    val type: String,
)

class LinuxIconThemeResolver(
    var preferredTheme: String = "hicolor",
    var fallbackChain: List<String> = DEFAULT_FALLBACK_CHAIN,
) {
    private val themeRoots: List<File>
        get() {
            val home = System.getProperty("user.home")
            return listOf(
                File("/usr/share/icons"),
                File("$home/.local/share/icons"),
                File("$home/.icons"),
            )
        }

    private val pixmapDir = File("/usr/share/pixmaps")

    fun listInstalledThemes(): List<IconThemeInfo> {
        val out = ArrayList<IconThemeInfo>()
        for (root in themeRoots) {
            val children = root.listFiles() ?: continue
            for (dir in children) {
                if (!dir.isDirectory) continue
                val index = File(dir, "index.theme")
                if (!index.isFile) continue
                parseIndex(dir, index)?.let { out += it }
            }
        }
        return out.distinctBy { it.name }.sortedBy { it.name.lowercase() }
    }

    fun resolve(iconName: String, size: Int = 48): File? {
        if (iconName.isBlank()) return null
        val asPath = File(iconName)
        if (asPath.isFile) return asPath
        val themes = listInstalledThemes().associateBy { it.name }
        val chain = buildChain(preferredTheme, themes)
        for (themeName in chain) {
            val theme = themes[themeName] ?: continue
            resolveInTheme(theme, iconName, size)?.let { return it }
        }
        if (pixmapDir.isDirectory) {
            for (ext in listOf("svg", "png", "xpm")) {
                val f = File(pixmapDir, "$iconName.$ext")
                if (f.isFile) return f
            }
        }
        return null
    }

    private fun buildChain(start: String, themes: Map<String, IconThemeInfo>): List<String> {
        val ordered = ArrayList<String>()
        val seen = HashSet<String>()
        fun walk(name: String) {
            if (!seen.add(name)) return
            ordered += name
            themes[name]?.inherits?.forEach { walk(it) }
        }
        walk(start)
        for (fb in fallbackChain) walk(fb)
        walk("hicolor")
        return ordered
    }

    private fun resolveInTheme(theme: IconThemeInfo, iconName: String, size: Int): File? {
        val ranked = theme.directories.sortedBy { kotlin.math.abs(it.size - size) }
        val dirs = if (ranked.isEmpty()) listOf(ThemeDir(".", size, 1, "", "Threshold")) else ranked
        for (dir in dirs) {
            val folder = File(theme.directory, dir.path)
            for (ext in listOf("svg", "png")) {
                val f = File(folder, "$iconName.$ext")
                if (f.isFile) return f
            }
        }
        return theme.directory.walkTopDown().maxDepth(4).firstOrNull { f ->
            f.isFile && (f.name == "$iconName.svg" || f.name == "$iconName.png")
        }
    }

    private fun parseIndex(dir: File, index: File): IconThemeInfo? {
        val lines = runCatching { index.readLines() }.getOrNull() ?: return null
        var section = ""
        val values = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                values.putIfAbsent(section, LinkedHashMap())
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            values.getOrPut(section) { LinkedHashMap() }[line.substring(0, eq).trim()] =
                line.substring(eq + 1).trim()
        }
        val iconTheme = values["Icon Theme"] ?: return null
        val name = iconTheme["Name"] ?: dir.name
        val inherits = (iconTheme["Inherits"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val dirNames = (iconTheme["Directories"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val directories = dirNames.map { d ->
            val m = values[d] ?: emptyMap()
            ThemeDir(
                path = d,
                size = m["Size"]?.toIntOrNull() ?: 48,
                scale = m["Scale"]?.toIntOrNull() ?: 1,
                context = m["Context"] ?: "",
                type = m["Type"] ?: "Threshold",
            )
        }
        return IconThemeInfo(name = dir.name, directory = dir, inherits = inherits, directories = directories)
    }

    companion object {
        val DEFAULT_FALLBACK_CHAIN = listOf(
            "Papirus",
            "Breeze",
            "Adwaita",
            "gnome",
            "hicolor",
        )
    }
}
