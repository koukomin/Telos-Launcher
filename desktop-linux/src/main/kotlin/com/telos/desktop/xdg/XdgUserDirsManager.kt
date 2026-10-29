package com.telos.desktop.xdg

import java.io.File
import java.util.concurrent.TimeUnit

data class XdgUserDirs(
    val desktop: File,
    val download: File,
    val documents: File,
    val music: File,
    val pictures: File,
    val videos: File,
    val home: File,
    val trash: File,
)

object XdgUserDirsManager {
    fun resolve(): XdgUserDirs {
        val home = File(System.getProperty("user.home"))
        val fromFile = parseUserDirsFile(File(home, ".config/user-dirs.dirs"), home)
        return XdgUserDirs(
            desktop = fromFile["XDG_DESKTOP_DIR"] ?: xdgUserDir("DESKTOP") ?: File(home, "Desktop"),
            download = fromFile["XDG_DOWNLOAD_DIR"] ?: xdgUserDir("DOWNLOAD") ?: File(home, "Downloads"),
            documents = fromFile["XDG_DOCUMENTS_DIR"] ?: xdgUserDir("DOCUMENTS") ?: File(home, "Documents"),
            music = fromFile["XDG_MUSIC_DIR"] ?: xdgUserDir("MUSIC") ?: File(home, "Music"),
            pictures = fromFile["XDG_PICTURES_DIR"] ?: xdgUserDir("PICTURES") ?: File(home, "Pictures"),
            videos = fromFile["XDG_VIDEOS_DIR"] ?: xdgUserDir("VIDEOS") ?: File(home, "Videos"),
            home = home,
            trash = File(home, ".local/share/Trash/files"),
        )
    }

    fun shortcuts(dirs: XdgUserDirs = resolve()): List<Pair<String, File>> = listOf(
        "Home" to dirs.home,
        "Desktop" to dirs.desktop,
        "Downloads" to dirs.download,
        "Documents" to dirs.documents,
        "Music" to dirs.music,
        "Pictures" to dirs.pictures,
        "Videos" to dirs.videos,
        "Trash" to dirs.trash,
    )

    private fun parseUserDirsFile(file: File, home: File): Map<String, File> {
        if (!file.isFile) return emptyMap()
        val out = HashMap<String, File>()
        for (raw in file.readLines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1).trim().trim('"')
            value = value.replace("\$HOME", home.absolutePath)
            out[key] = File(value)
        }
        return out
    }

    private fun xdgUserDir(name: String): File? {
        return try {
            val p = ProcessBuilder("xdg-user-dir", name).start()
            val text = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(3, TimeUnit.SECONDS)
            if (p.exitValue() == 0 && text.isNotEmpty()) File(text) else null
        } catch (_: Exception) {
            null
        }
    }
}
