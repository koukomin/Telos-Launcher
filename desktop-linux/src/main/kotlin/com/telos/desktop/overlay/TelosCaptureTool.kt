package com.telos.desktop.overlay

import com.telos.desktop.linux.Sys
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TelosCaptureTool {
    internal fun dest(): File {
        val pics = XdgUserDirsManager.resolve().pictures
        pics.mkdirs()
        val name = "telos-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"
        val file = File(pics, name)
        lockPrivate(file)
        return file
    }

    internal fun lockPrivate(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    fun fullscreen(): File? {
        val file = dest()
        val ok = captureFullscreen(file)
        if (ok) {
            lockPrivate(file)
            maybeClipboard(file)
            TelosNotificationCenter.post("Screenshot", file.name)
        } else {
            file.delete()
        }
        return file.takeIf { ok }
    }

    fun area(): File? {
        val file = dest()
        val ok = captureArea(file)
        if (ok) {
            lockPrivate(file)
            maybeClipboard(file)
            TelosNotificationCenter.post("Screenshot", file.name)
        } else {
            file.delete()
        }
        return file.takeIf { ok }
    }

    private fun captureFullscreen(file: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            return Sys.which("screencapture")?.let { Sys.run(it, "-x", file.absolutePath, timeoutSec = 15).ok } == true
        }
        val session = System.getenv("XDG_SESSION_TYPE")
        return if (session == "wayland" && Sys.which("grim") != null) {
            Sys.run("grim", "-t", "png", file.absolutePath, timeoutSec = 8).ok
        } else {
            Sys.which("scrot")?.let { Sys.run(it, file.absolutePath).ok } == true ||
                Sys.which("import")?.let { Sys.run(it, "-window", "root", file.absolutePath).ok } == true
        }
    }

    private fun captureArea(file: File): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac") || os.contains("darwin")) {
            return Sys.which("screencapture")?.let { Sys.run(it, "-i", "-x", file.absolutePath, timeoutSec = 60).ok } == true
        }
        val session = System.getenv("XDG_SESSION_TYPE")
        return if (session == "wayland" && Sys.which("grim") != null && Sys.which("slurp") != null) {
            val geo = Sys.run("slurp", timeoutSec = 30).stdout.trim()
            if (geo.isBlank()) false else Sys.run("grim", "-t", "png", "-g", geo, file.absolutePath, timeoutSec = 8).ok
        } else {
            Sys.which("scrot")?.let { Sys.run(it, "-s", file.absolutePath, timeoutSec = 30).ok } == true
        }
    }

    private fun maybeClipboard(file: File) {
        if (System.getenv("TELOS_SHOT_CLIPBOARD") != "1") return
        if (Sys.which("wl-copy") == null) return
        val path = com.telos.desktop.session.UnlockCli.shQuote(file.absolutePath)
        Sys.run("sh", "-c", "wl-copy --type image/png < $path", timeoutSec = 8)
    }
}
