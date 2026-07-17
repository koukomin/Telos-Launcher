package de.mm20.launcher2.desktopmode

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.core.content.getSystemService
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tracks external display connection state and decides whether the desktop shell should be
 * showing. Does not launch anything itself - see LauncherApplication, which observes
 * [shouldShowDesktopShell] and starts/relies on DesktopModeActivity finishing on its own when
 * the display goes away. Kept this way because this module sits below app:ui in the dependency
 * graph and can't reference the Activity class directly.
 */
class DesktopModeManager internal constructor(
    private val context: Context,
    private val settings: DesktopModeSettings,
) {
    private val displayManager = context.getSystemService<DisplayManager>()

    /**
     * Whether this device exposes the platform capability launchDisplayId relies on. If false,
     * desktop mode can never work here regardless of the user's preference - checked once, this
     * is a hardware/build characteristic, not something that changes at runtime.
     */
    val isSupportedOnThisDevice: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)

    private val _externalDisplay = MutableStateFlow(findExternalDisplay())
    val externalDisplay: StateFlow<Display?> = _externalDisplay.asStateFlow()

    init {
        if (isSupportedOnThisDevice) {
            displayManager?.registerDisplayListener(
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) {
                        _externalDisplay.value = findExternalDisplay()
                    }

                    override fun onDisplayRemoved(displayId: Int) {
                        _externalDisplay.value = findExternalDisplay()
                    }

                    override fun onDisplayChanged(displayId: Int) {
                        _externalDisplay.value = findExternalDisplay()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    private fun findExternalDisplay(): Display? {
        return displayManager?.displays?.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }
    }

    /** True while the desktop shell should be visible on an external display. */
    val shouldShowDesktopShell = combine(settings.enabled, _externalDisplay) { enabled, display ->
        enabled && isSupportedOnThisDevice && display != null
    }.distinctUntilChanged()

    /** The external display to show the shell on, valid only while [shouldShowDesktopShell] is true. */
    fun currentExternalDisplayId(): Int? = _externalDisplay.value?.displayId
}
