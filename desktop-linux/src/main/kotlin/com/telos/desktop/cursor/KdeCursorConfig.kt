package com.telos.desktop.cursor

import com.telos.desktop.vault.PortableTraceGuard
import java.io.File

data class KdeCursorSelection(
    val theme: String?,
    val size: Int?,
    val source: String,
)

/**
 * Reads Plasma / KDE cursor theme + size from `kcminputrc` and `kdeglobals`,
 * then falls back to GTK `settings.ini` and `~/.icons/default/index.theme`.
 */
object KdeCursorConfig {
    val KDE_SIZES = listOf(16, 24, 32, 48)

    fun load(preferHostHome: Boolean = true): KdeCursorSelection {
        val homes = configHomes(preferHostHome)
        var theme: String? = null
        var size: Int? = null
        var source = "none"

        for (home in homes) {
            val kcminput = File(home, ".config/kcminputrc")
            if (kcminput.isFile) {
                val mouse = section(kcminput, "Mouse")
                theme = theme ?: mouse["cursorTheme"] ?: mouse["CursorTheme"]
                size = size ?: (mouse["cursorSize"] ?: mouse["CursorSize"])?.toIntOrNull()
                if (theme != null || size != null) source = "kcminputrc"
            }
            val kde = File(home, ".config/kdeglobals")
            if (kde.isFile) {
                val icons = section(kde, "Icons")
                val general = section(kde, "General")
                val flat = flatIni(kde)
                theme = theme
                    ?: icons["cursorTheme"]
                    ?: general["cursorTheme"]
                    ?: flat["cursorTheme"]
                size = size
                    ?: icons["cursorSize"]?.toIntOrNull()
                    ?: general["cursorSize"]?.toIntOrNull()
                    ?: flat["cursorSize"]?.toIntOrNull()
                if (source == "none" && (theme != null || size != null)) source = "kdeglobals"
            }
            for (gtkRel in listOf(".config/gtk-3.0/settings.ini", ".config/gtk-4.0/settings.ini")) {
                val gtk = File(home, gtkRel)
                if (!gtk.isFile) continue
                val settings = section(gtk, "Settings").ifEmpty { flatIni(gtk) }
                theme = theme ?: settings["gtk-cursor-theme-name"]
                size = size ?: settings["gtk-cursor-theme-size"]?.toIntOrNull()
                if (source == "none" && theme != null) source = "gtk"
            }
            val def = File(home, ".icons/default/index.theme")
            if (def.isFile) {
                val inherit = section(def, "Icon Theme")["Inherits"]
                    ?: flatIni(def)["Inherits"]
                theme = theme ?: inherit?.split(',')?.firstOrNull()?.trim()
                if (source == "none" && theme != null) source = "icons-default"
            }
        }

        val envTheme = System.getenv("XCURSOR_THEME")?.trim()?.takeIf { it.isNotEmpty() }
        val envSize = System.getenv("XCURSOR_SIZE")?.toIntOrNull()
        if (theme == null && envTheme != null) {
            theme = envTheme
            source = "env"
        }
        if (size == null && envSize != null) size = envSize

        return KdeCursorSelection(
            theme = theme,
            size = size?.coerceIn(8, 128),
            source = source,
        )
    }

    fun nearestKdeSize(raw: Int?): Int {
        val v = raw ?: 24
        return KDE_SIZES.minByOrNull { kotlin.math.abs(it - v) } ?: 24
    }

    private fun configHomes(preferHostHome: Boolean): List<File> {
        val out = LinkedHashSet<File>()
        val user = File(System.getProperty("user.home"))
        val host = PortableTraceGuard.hostProfileHome()
        if (preferHostHome) {
            out += host
            if (user.canonicalFile != host.canonicalFile) out += user
        } else {
            out += user
            if (user.canonicalFile != host.canonicalFile) out += host
        }
        return out.toList()
    }

    private fun section(file: File, name: String): Map<String, String> {
        var section = ""
        val out = LinkedHashMap<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1)
                return@forEach
            }
            if (section != name) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            out[line.substring(0, eq).trim()] = line.substring(eq + 1).trim().trim('"')
        }
        return out
    }

    private fun flatIni(file: File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            out[line.substring(0, eq).trim()] = line.substring(eq + 1).trim().trim('"')
        }
        return out
    }
}
