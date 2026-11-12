package com.telos.desktop.theme

import androidx.compose.ui.graphics.Color
import com.telos.desktop.cursor.KdeCursorConfig
import java.io.File

data class HostDesktopTheme(
    val accent: Color,
    val iconTheme: String?,
    val cursorTheme: String?,
    val cursorSize: Int?,
    val source: String,
)

object SystemThemeBridge {
    fun load(): HostDesktopTheme {
        val home = File(System.getProperty("user.home"))
        val kde = File(home, ".config/kdeglobals")
        val gtk3 = File(home, ".config/gtk-3.0/settings.ini")
        val gtk4 = File(home, ".config/gtk-4.0/settings.ini")
        var accent = Color(0xFFFF007F)
        var icons: String? = null
        var source = "telos"
        if (kde.isFile) {
            val map = ini(kde)
            parseRgb(map["AccentColor"] ?: map["DecorationFocus"])?.let { accent = it }
            icons = map["Theme"] ?: map["theme"]
            source = "kdeglobals"
        }
        val gtk = listOf(gtk3, gtk4).firstOrNull { it.isFile }
        if (gtk != null) {
            val map = ini(gtk)
            icons = icons ?: map["gtk-icon-theme-name"]
            parseGtkColor(map["gtk-theme-name"])
            if (source == "telos") source = "gtk"
        }
        val cursorSel = KdeCursorConfig.load()
        if (source == "telos" && cursorSel.source != "none") source = cursorSel.source
        return HostDesktopTheme(
            accent = accent,
            iconTheme = icons,
            cursorTheme = cursorSel.theme,
            cursorSize = cursorSel.size,
            source = source,
        )
    }

    private fun ini(file: File): Map<String, String> {
        val out = HashMap<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            out[line.substring(0, eq).trim()] = line.substring(eq + 1).trim().trim('"')
        }
        return out
    }

    private fun parseRgb(value: String?): Color? {
        if (value.isNullOrBlank()) return null
        val p = value.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }
        return if (p.size >= 3) Color(p[0], p[1], p[2]) else null
    }

    private fun parseGtkColor(name: String?) {
        name ?: return
    }
}
