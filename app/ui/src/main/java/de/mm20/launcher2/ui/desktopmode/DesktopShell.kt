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
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.preferences.DesktopWallpaperMode
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import org.koin.compose.koinInject

/**
 * Root of the desktop shell shown on the external display. The area above the taskbar is
 * otherwise empty: this launcher doesn't composite other apps' windows itself, it just
 * launches them onto this same display (see DesktopStartMenu) and lets the platform's own
 * window manager render them, fullscreen or freeform depending on what's available.
 *
 * Deliberately not the phone's own wallpaper: the video wallpaper is a system WallpaperService
 * tied to the default display and can't render here, and a phone-shaped static image wouldn't
 * fit a landscape external display's aspect ratio anyway - so this is its own, independent
 * wallpaper setting (see DesktopModeSettingsScreen).
 */
@Composable
fun DesktopShell() {
    var showStartMenu by remember { mutableStateOf(false) }
    val desktopModeSettings = koinInject<DesktopModeSettings>()
    val wallpaperMode by desktopModeSettings.wallpaperMode
        .collectAsStateWithLifecycle(DesktopWallpaperMode.SolidColor)
    val wallpaperImageUri by desktopModeSettings.wallpaperImageUri.collectAsStateWithLifecycle(null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (wallpaperMode == DesktopWallpaperMode.StaticImage && wallpaperImageUri != null) {
            AsyncImage(
                model = wallpaperImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
