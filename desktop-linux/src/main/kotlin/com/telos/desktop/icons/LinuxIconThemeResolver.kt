package com.telos.desktop.icons

import java.io.File

enum class IconThemeSource {
    System,
    TelosDownloaded,
    BuiltIn,
}

data class IconThemeInfo(
    val name: String,
    val directory: File?,
    val inherits: List<String>,
    val directories: List<ThemeDir>,
    val displayName: String = name,
    val source: IconThemeSource = IconThemeSource.System,
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
    extraRoots: List<File> = emptyList(),
) {
    private val extraRoots: MutableList<File> = extraRoots.toMutableList()
    @Volatile private var cachedThemes: List<IconThemeInfo>? = null

    fun addExtraRoot(root: File) {
        if (extraRoots.none { it.absolutePath == root.absolutePath }) {
            extraRoots += root
            invalidate()
        }
    }

    fun invalidate() {
        cachedThemes = null
    }

    private val themeRoots: List<File>
        get() {
            val roots = LinkedHashSet<File>()
            // Shared portable/vault/host XDG roots (also used by KDE/XCursor discovery).
            roots.addAll(FreedesktopThemeRoots.searchRoots(includeHostSystem = true))
            val home = System.getProperty("user.home")
            roots += File("/var/lib/flatpak/exports/share/icons")
            roots += File("$home/.local/share/flatpak/exports/share/icons")
            for (managed in TelosIconPackPaths.managedThemeRoots()) {
                roots += managed
            }
            for (extra in extraRoots) {
                roots += extra
            }
            return roots.toList()
        }

    private val pixmapDir = File("/usr/share/pixmaps")

    fun listInstalledThemes(): List<IconThemeInfo> {
        cachedThemes?.let { return it }
        val out = ArrayList<IconThemeInfo>()
        val managedCanonical = TelosIconPackPaths.managedThemeRoots().mapNotNull {
            runCatching { it.canonicalFile }.getOrNull()
        }
        for (root in themeRoots) {
            val children = root.listFiles() ?: continue
            for (dir in children) {
                if (!dir.isDirectory) continue
                if (dir.name.startsWith(".")) continue
                val index = File(dir, "index.theme")
                if (!index.isFile) continue
                if (isCursorOnlyTheme(index)) continue
                val parsed = parseIndex(dir, index) ?: continue
                val underManaged = managedCanonical.any { managed ->
                    runCatching { dir.canonicalFile.path.startsWith(managed.path + File.separator) }.getOrDefault(false)
                }
                out += if (underManaged) parsed.copy(source = IconThemeSource.TelosDownloaded) else parsed
            }
        }
        if (out.none { it.name == TelosIconPackPaths.BUILTIN_CANDY }) {
            out += IconThemeInfo(
                name = TelosIconPackPaths.BUILTIN_CANDY,
                directory = null,
                inherits = listOf("hicolor"),
                directories = emptyList(),
                displayName = "CandyCyber",
                source = IconThemeSource.BuiltIn,
            )
        }
        val sorted = out.distinctBy { it.name }.sortedBy { it.name.lowercase() }
        cachedThemes = sorted
        return sorted
    }

    fun resolve(iconName: String, size: Int = 48): File? {
        if (iconName.isBlank()) return null
        val asPath = File(iconName)
        if (asPath.isFile) return asPath
        val themes = listInstalledThemes().filter { it.directory != null }.associateBy { it.name }
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
        if (start != TelosIconPackPaths.BUILTIN_CANDY) walk(start)
        for (fb in fallbackChain) walk(fb)
        walk("hicolor")
        return ordered
    }

    private fun resolveInTheme(theme: IconThemeInfo, iconName: String, size: Int): File? {
        val base = theme.directory ?: return null
        val ranked = theme.directories.sortedBy { kotlin.math.abs(it.size - size) }
        val dirs = if (ranked.isEmpty()) listOf(ThemeDir(".", size, 1, "", "Threshold")) else ranked
        for (dir in dirs) {
            val folder = File(base, dir.path)
            for (ext in listOf("svg", "png")) {
                val f = File(folder, "$iconName.$ext")
                if (f.isFile) return f
            }
        }
        return base.walkTopDown().maxDepth(4).firstOrNull { f ->
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
        val display = iconTheme["Name"] ?: dir.name
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
        return IconThemeInfo(
            name = dir.name,
            directory = dir,
            inherits = inherits,
            directories = directories,
            displayName = display,
            source = IconThemeSource.System,
        )
    }

    private fun isCursorOnlyTheme(index: File): Boolean {
        val text = runCatching { index.readText() }.getOrNull() ?: return false
        val hasCursor = text.lines().any { it.trim() == "[Cursor Theme]" }
        val hasIcon = text.lines().any { it.trim() == "[Icon Theme]" }
        return hasCursor && !hasIcon
    }

    companion object {
        val DEFAULT_FALLBACK_CHAIN = listOf(
            "Papirus",
            "Breeze",
            "Adwaita",
            "gnome",
            "hicolor",
        )

        fun withManagedRoots(preferredTheme: String): LinuxIconThemeResolver =
            LinuxIconThemeResolver(
                preferredTheme = preferredTheme,
                extraRoots = TelosIconPackPaths.managedThemeRoots(),
            )
    }
}
