package com.telos.desktop.panel

import com.telos.desktop.linux.Sys
import java.io.File

data class TrayApp(val id: String, val name: String, val running: Boolean, val exec: List<String>)

object SystemTrayManager {
    private val watched = listOf(
        TrayApp("megasync", "MEGASync", false, listOf("megasync")),
        TrayApp("opensnitch-ui", "OpenSnitch", false, listOf("opensnitch-ui")),
        TrayApp("portmaster", "Portmaster", false, listOf("portmaster-start", "portmaster")),
        TrayApp("sunshine", "Sunshine", false, listOf("sunshine")),
    )

    fun snapshot(): List<TrayApp> {
        val ps = Sys.run("ps", "-eo", "comm=").stdout.lines().map { it.trim() }.toSet()
        return watched.map { app ->
            app.copy(running = ps.any { it.equals(app.id, true) || it.contains(app.id) })
        }
    }

    fun launch(app: TrayApp) {
        val bin = app.exec.firstNotNullOfOrNull { Sys.which(it) } ?: return
        Sys.runBg(bin)
    }

    fun desktopStatusNotifiers(): List<File> {
        val home = System.getProperty("user.home")
        return listOf(
            File("/tmp"),
            File("/run/user/${Sys.run("id", "-u").stdout.trim()}"),
        ).flatMap { it.listFiles()?.toList().orEmpty() }.filter { it.name.contains("StatusNotifier") }
    }

    val keyboardManager = com.telos.desktop.keyboard.LinuxKeyboardManager()
}
