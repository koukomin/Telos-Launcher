package com.telos.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.telos.desktop.display.LinuxDisplayManager
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.ui.AuthScreen
import com.telos.desktop.ui.DesktopWorkspace
import com.telos.desktop.ui.TelosDesktopTheme
import com.telos.desktop.vault.LuksVaultManager
import com.telos.desktop.wm.TelosWindowManager

fun main() = application {
    val vault = remember { LuksVaultManager() }
    var unlocked by remember { mutableStateOf(false) }
    val wm = remember { TelosWindowManager() }
    Window(
        onCloseRequest = {
            vault.lock()
            exitApplication()
        },
        undecorated = true,
        resizable = false,
        title = "Telos Desktop",
        state = WindowState(placement = WindowPlacement.Fullscreen),
    ) {
        TelosDesktopTheme {
            if (!unlocked) {
                AuthScreen(vault) { unlocked = true }
            } else {
                val settings = remember {
                    TelosDesktopSettings(vault.configDir).also { it.load() }
                }
                val icons = remember {
                    LinuxIconThemeResolver(preferredTheme = settings.iconTheme)
                }
                val display = remember { LinuxDisplayManager(settings) }
                DesktopWorkspace(
                    settings = settings,
                    icons = icons,
                    display = display,
                    wm = wm,
                )
            }
        }
    }
}
