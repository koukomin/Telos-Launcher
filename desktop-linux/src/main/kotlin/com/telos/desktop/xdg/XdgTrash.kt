package com.telos.desktop.xdg

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object XdgTrash {
    fun filesDir(home: File = File(System.getProperty("user.home"))): File =
        File(home, ".local/share/Trash/files")

    fun infoDir(home: File = File(System.getProperty("user.home"))): File =
        File(home, ".local/share/Trash/info")

    fun moveToTrash(file: File, home: File = File(System.getProperty("user.home"))): Boolean {
        if (!file.exists()) return false
        val files = filesDir(home).apply { mkdirs() }
        val info = infoDir(home).apply { mkdirs() }
        var name = file.name
        var dest = File(files, name)
        var n = 1
        while (dest.exists()) {
            dest = File(files, "${file.name}.$n")
            name = dest.name
            n++
        }
        if (!file.renameTo(dest)) return false
        val encoded = URLEncoder.encode(file.absolutePath, StandardCharsets.UTF_8).replace("+", "%20")
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.now())
        File(info, "$name.trashinfo").writeText("[Trash Info]\nPath=$encoded\nDeletionDate=$stamp\n")
        return true
    }

    fun originalPath(trashed: File, home: File = File(System.getProperty("user.home"))): File? {
        val info = File(infoDir(home), trashed.name + ".trashinfo")
        if (!info.isFile) return null
        val line = info.readLines().firstOrNull { it.startsWith("Path=") } ?: return null
        val raw = line.removePrefix("Path=")
        val decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8)
        return File(decoded)
    }

    fun restore(trashed: File, home: File = File(System.getProperty("user.home"))): Boolean {
        val dest = originalPath(trashed, home) ?: return false
        dest.parentFile?.mkdirs()
        if (dest.exists()) return false
        if (!trashed.renameTo(dest)) return false
        File(infoDir(home), trashed.name + ".trashinfo").delete()
        return true
    }

    fun empty(home: File = File(System.getProperty("user.home"))) {
        filesDir(home).listFiles()?.forEach { it.deleteRecursively() }
        infoDir(home).listFiles()?.forEach { it.delete() }
    }
}
