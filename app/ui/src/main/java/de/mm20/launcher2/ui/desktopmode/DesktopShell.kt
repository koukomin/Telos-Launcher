// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.preferences.DesktopWallpaperMode
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import de.mm20.launcher2.ui.desktopmode.cursor.TelosDesktopCanvas
import de.mm20.launcher2.ui.desktopmode.theme.DesktopThemeRepository
import de.mm20.launcher2.ui.desktopmode.theme.DesktopThemeSettingsWindow
import de.mm20.launcher2.ui.desktopmode.wm.TelosWindowFrame
import de.mm20.launcher2.ui.desktopmode.wm.TelosWindowManager
import org.koin.compose.koinInject

@Composable
fun DesktopShell() {
    val context = LocalContext.current
    var showStartMenu by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }

    val desktopModeSettings = koinInject<DesktopModeSettings>()
    val wallpaperMode by desktopModeSettings.wallpaperMode
        .collectAsStateWithLifecycle(DesktopWallpaperMode.SolidColor)
    val wallpaperImageUri by desktopModeSettings.wallpaperImageUri.collectAsStateWithLifecycle(null)

    val themeRepo = remember(context) { DesktopThemeRepository.getInstance(context) }
    val windowTheme by themeRepo.windowTheme.collectAsStateWithLifecycle()
    val cursorConfig by themeRepo.cursorConfig.collectAsStateWithLifecycle()

    val windowManager = remember { TelosWindowManager() }
    val windows by windowManager.windows.collectAsStateWithLifecycle()

    TelosDesktopCanvas(config = cursorConfig, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val desktopWidth = maxWidth
            val desktopHeight = maxHeight

            if (wallpaperMode == DesktopWallpaperMode.StaticImage && wallpaperImageUri != null) {
                AsyncImage(
                    model = wallpaperImageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            DesktopWorkspace(
                modifier = Modifier.fillMaxSize().padding(top = DesktopTaskbarHeight),
                onOpenThemeSettings = { showThemeSettings = true }
            )

            // Internal Floating Windows
            windows.forEach { windowState ->
                TelosWindowFrame(
                    windowState = windowState,
                    themeConfig = windowTheme,
                    windowManager = windowManager,
                    desktopWidth = desktopWidth,
                    desktopHeight = desktopHeight
                ) {
                    windowState.content()
                }
            }

            DesktopTaskbar(
                modifier = Modifier.align(Alignment.TopStart),
                startMenuOpen = showStartMenu,
                onToggleStartMenu = { showStartMenu = !showStartMenu },
            )

            if (showStartMenu) {
                DesktopStartMenu(onDismiss = { showStartMenu = false })
            }

            if (showThemeSettings) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DesktopThemeSettingsWindow(onDismiss = { showThemeSettings = false })
                }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
