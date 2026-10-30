package com.telos.desktop.files

import com.telos.desktop.linux.Sys
import java.io.File
import java.net.URI

data class NetworkPlace(
    val label: String,
    val uri: String,
    val local: File?,
    val user: String? = null,
)

object NetworkPlaces {
    fun gvfsRoot(): File? {
        val runtime = System.getenv("XDG_RUNTIME_DIR") ?: return null
        val dir = File(runtime, "gvfs")
        return dir.takeIf { it.isDirectory }
    }

    fun bookmarks(configHome: File = File(System.getProperty("user.home"), ".config")): List<NetworkPlace> {
        val file = File(configHome, "gtk-3.0/bookmarks")
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull(::parseBookmark)
    }

    fun places(): List<NetworkPlace> {
        val out = LinkedHashMap<String, NetworkPlace>()
        bookmarks().forEach { out.putIfAbsent(it.uri, it) }
        gvfsRoot()?.listFiles()?.forEach { mount ->
            out.putIfAbsent(
                mount.absolutePath,
                NetworkPlace(mount.name, mount.absolutePath, mount),
            )
        }
        return out.values.toList()
    }

    fun open(place: NetworkPlace): File? {
        place.local?.takeIf { it.exists() }?.let { return it }
        if (place.uri.startsWith("file:")) {
            return runCatching { File(URI(place.uri)) }.getOrNull()
        }
        Sys.runBg("gio", "open", place.uri)
        return gvfsRoot()
    }

    internal fun parseBookmark(line: String): NetworkPlace? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val sp = trimmed.indexOf(' ')
        val uri = if (sp < 0) trimmed else trimmed.substring(0, sp)
        val label = if (sp < 0) uri.substringAfterLast('/').ifBlank { uri } else trimmed.substring(sp + 1)
        val local = if (uri.startsWith("file:")) runCatching { File(URI(uri)) }.getOrNull() else null
        return NetworkPlace(label, uri, local)
    }
}
