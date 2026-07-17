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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val freeformAccessProvider = FreeformAccessProvider(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

            // The very first external display this device ever sees auto-enables desktop mode,
            // so it "just works" out of the box - but only until the user touches the toggle
            // themselves (either direction), tracked by userConfigured, so this never fights a
            // deliberate opt-out and never fires more than once.
            combine(_externalDisplay, settings.userConfigured) { display, configured ->
                display != null && !configured
            }.distinctUntilChanged()
                .onEach { shouldAutoEnable -> if (shouldAutoEnable) settings.autoEnable() }
                .launchIn(scope)
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

    // --- Freeform windowing (Stage 4) ---
    // Separate opt-in from desktop mode itself: off by default, and turning it on flips a
    // device-wide OS setting (Settings.Global.enable_freeform_support) that affects every app's
    // windowing, not just ours - per explicit user requirement, this must also be reversible
    // from the same toggle without needing adb.

    val isFreeformPotentiallySupported: Boolean = freeformAccessProvider.isPotentiallySupported

    /** The user's last explicit choice, independent of what the OS setting currently reads. */
    val freeformPreferenceEnabled = settings.freeformEnabled

    private val _freeformActiveInSystem = MutableStateFlow(freeformAccessProvider.isEnabledInSystem())
    /** Live read of the OS-level `enable_freeform_support` value - can drift from the user's
     * preference if something else on the device changed it. */
    val freeformActiveInSystem: StateFlow<Boolean> = _freeformActiveInSystem.asStateFlow()

    init {
        // Something else (another app, a factory reset of the setting) may change this while we're
        // not actively toggling it, so re-check it whenever the desktop shell is about to show.
        shouldShowDesktopShell.onEach { showing ->
            if (showing) _freeformActiveInSystem.value = freeformAccessProvider.isEnabledInSystem()
        }.launchIn(scope)
    }

    suspend fun hasFreeformShizukuPermission(): Boolean = freeformAccessProvider.hasShizukuPermission()

    suspend fun isFreeformShizukuAvailable(): Boolean = freeformAccessProvider.isShizukuAvailable()

    suspend fun requestFreeformShizukuPermission(): Boolean =
        freeformAccessProvider.requestShizukuPermission()

    /**
     * Attempts to flip the OS-level freeform setting and records the user's intent regardless of
     * whether the OS write itself succeeded, so the settings UI always reflects what the user
     * asked for rather than getting stuck if Shizuku is temporarily unavailable.
     * @return true if the OS setting ended up in the requested state.
     */
    suspend fun setFreeformEnabled(enabled: Boolean): Boolean {
        settings.setFreeformEnabled(enabled)
        val success = freeformAccessProvider.setEnabled(enabled)
        _freeformActiveInSystem.value = freeformAccessProvider.isEnabledInSystem()
        return success
    }
}
