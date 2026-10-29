package com.telos.desktop.overlay

import com.telos.desktop.linux.Sys
import com.telos.desktop.xdg.XdgUserDirsManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TelosCaptureTool {
    private fun dest(): File {
        val pics = XdgUserDirsManager.resolve().pictures
        pics.mkdirs()
        val name = "telos-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"
        return File(pics, name)
    }

    fun fullscreen(): File? {
        val file = dest()
        val session = System.getenv("XDG_SESSION_TYPE")
        val ok = if (session == "wayland" && Sys.which("grim") != null) {
            Sys.run("grim", file.absolutePath, timeoutSec = 8).ok
        } else {
            Sys.which("scrot")?.let { Sys.run(it, file.absolutePath).ok } == true ||
                Sys.which("import")?.let { Sys.run(it, "-window", "root", file.absolutePath).ok } == true
        }
        if (ok) copy(file)
        return file.takeIf { ok }
    }

    fun area(): File? {
        val file = dest()
        val session = System.getenv("XDG_SESSION_TYPE")
        val ok = if (session == "wayland" && Sys.which("grim") != null && Sys.which("slurp") != null) {
            val geo = Sys.run("slurp", timeoutSec = 30).stdout.trim()
            if (geo.isBlank()) false else Sys.run("grim", "-g", geo, file.absolutePath, timeoutSec = 8).ok
        } else {
            Sys.which("scrot")?.let { Sys.run(it, "-s", file.absolutePath, timeoutSec = 30).ok } == true
        }
        if (ok) copy(file)
        return file.takeIf { ok }
    }

    private fun copy(file: File) {
        val wl = Sys.which("wl-copy")
        if (wl != null) Sys.run(wl, "--type", "image/png", input = null).let {
            Sys.run(wl, "--type", "image/png", file.absolutePath)
        }
        TelosNotificationCenter.post("Screenshot", file.name)
    }
}
