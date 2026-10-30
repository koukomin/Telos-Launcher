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

    val placement: WindowPlacement
        get() = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating

    fun toggleFullscreen() {
        fullscreen = !fullscreen
    }

    fun toggleAppGrid() {
        appGridOpen = !appGridOpen
        if (appGridOpen) {
            overviewOpen = false
            controlCenterOpen = false
        }
    }

    fun toggleOverview() {
        overviewOpen = !overviewOpen
        if (overviewOpen) {
            appGridOpen = false
            controlCenterOpen = false
        }
    }

    fun toggleControlCenter() {
        controlCenterOpen = !controlCenterOpen
        if (controlCenterOpen) overviewOpen = false
    }
}
