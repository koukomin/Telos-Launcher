package de.mm20.launcher2.ui.desktopmode

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject

/**
 * Hosts the desktop shell (taskbar, start menu, clock, system tray) on an external display.
 * Launched by LauncherApplication when DesktopModeManager reports an external display is
 * connected and desktop mode is enabled - never appears on the phone's own screen, never
 * responds to the HOME intent (see the manifest entry: no LAUNCHER/HOME category, not exported).
 */
class DesktopModeActivity : BaseActivity() {

    private val desktopModeSettings: DesktopModeSettings by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val orientation by desktopModeSettings.orientation.collectAsStateWithLifecycle(
                DesktopModeOrientation.Auto
            )
            LaunchedEffect(orientation) {
                requestedOrientation = when (orientation) {
                    DesktopModeOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    DesktopModeOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    DesktopModeOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_USER
                }
            }

            LauncherTheme {
                ProvideCompositionLocals {
                    DesktopShell()
                }
            }
        }
    }
}
