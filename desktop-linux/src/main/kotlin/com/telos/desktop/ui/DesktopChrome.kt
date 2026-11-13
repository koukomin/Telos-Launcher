package com.telos.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement

object DesktopChrome {
    var fullscreen by mutableStateOf(false)
    var appGridOpen by mutableStateOf(false)
    var overviewOpen by mutableStateOf(false)
    var controlCenterOpen by mutableStateOf(false)
    /** Helix global command palette (Super+Period / Ctrl+Space). */
    var commandPaletteOpen by mutableStateOf(false)
    /** Kickoff search seed from `telos://search?q=…` (consumed by PlasmaAppGrid). */
    var pendingSearchQuery by mutableStateOf<String?>(null)
    /** Bumped when wallpaper tonal / accent prefs change so Main re-resolves chrome accent. */
    var accentEpoch by mutableStateOf(0)

    fun bumpAccentEpoch() {
        accentEpoch++
    }

    val placement: WindowPlacement
        get() = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating

    fun toggleFullscreen() {
        fullscreen = !fullscreen
    }

    fun openAppGridWithQuery(query: String) {
        pendingSearchQuery = query
        appGridOpen = true
        overviewOpen = false
        controlCenterOpen = false
        commandPaletteOpen = false
    }

    fun toggleAppGrid() {
        appGridOpen = !appGridOpen
        if (appGridOpen) {
            overviewOpen = false
            controlCenterOpen = false
            commandPaletteOpen = false
        }
    }

    fun toggleOverview() {
        overviewOpen = !overviewOpen
        if (overviewOpen) {
            appGridOpen = false
            controlCenterOpen = false
            commandPaletteOpen = false
        }
    }

    fun toggleControlCenter() {
        controlCenterOpen = !controlCenterOpen
        if (controlCenterOpen) {
            overviewOpen = false
            commandPaletteOpen = false
        }
    }

    fun toggleCommandPalette() {
        commandPaletteOpen = !commandPaletteOpen
        if (commandPaletteOpen) {
            appGridOpen = false
            overviewOpen = false
            controlCenterOpen = false
        }
    }
}
