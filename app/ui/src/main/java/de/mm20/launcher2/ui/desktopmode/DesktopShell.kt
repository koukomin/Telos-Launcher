package de.mm20.launcher2.ui.desktopmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Root of the desktop shell shown on the external display. The area above the taskbar is
 * intentionally empty here: this launcher doesn't composite other apps' windows itself, it just
 * launches them onto this same display (see DesktopStartMenu) and lets the platform's own
 * window manager render them, fullscreen or freeform depending on what's available.
 */
@Composable
fun DesktopShell() {
    var showStartMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DesktopTaskbar(
            modifier = Modifier.align(Alignment.BottomStart),
            startMenuOpen = showStartMenu,
            onToggleStartMenu = { showStartMenu = !showStartMenu },
        )
        if (showStartMenu) {
            DesktopStartMenu(onDismiss = { showStartMenu = false })
        }
    }
}
