package de.mm20.launcher2.ui.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Failsafe kill-switch for the floating launcher overlay, reachable from outside the app - even
 * with the device otherwise fully unresponsive to touch, which is exactly the scenario this
 * exists for (see [FloatingLauncherService]'s class doc for the touch-lockout bug that prompted
 * it). Trigger via:
 *
 * ```
 * adb shell am broadcast -a de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER -p de.mm20.launcher2
 * ```
 *
 * Just flips [FloatingLauncherSettings.enabled] to false - the same flag the settings screen's
 * own toggle already uses, which [FloatingLauncherService] already watches to stop itself. No
 * separate disable path to keep in sync, and no need to reach any in-app UI first.
 */
class FloatingLauncherDisableReceiver : BroadcastReceiver(), KoinComponent {

    private val floatingLauncherSettings: FloatingLauncherSettings by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISABLE) {
            floatingLauncherSettings.setEnabled(false)
        }
    }

    companion object {
        const val ACTION_DISABLE = "de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER"
    }
}
