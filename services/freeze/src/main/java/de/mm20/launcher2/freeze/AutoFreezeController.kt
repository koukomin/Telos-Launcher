package de.mm20.launcher2.freeze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches for screen-off, idle timeout, and battery-saver events and freezes the apps the user
 * explicitly opted in via settings ([FreezeSettings.candidates]) - minus whatever
 * [FreezeExclusionChecker] vetoes (foreground, active notification/media session/foreground
 * service, Android Auto, or a per-app "never freeze" override).
 *
 * Trigger enablement and the idle timeout come from [FreezeProfileManager.resolvedSettings], i.e.
 * whichever [de.mm20.launcher2.preferences.FreezeProfile] the user has selected.
 *
 * It only ever acts on the explicit opt-in candidate list, never on "whatever's running in the
 * background".
 *
 * Registered as a Koin singleton so it's created (and starts listening) once, at app start.
 */
class AutoFreezeController internal constructor(
    private val context: Context,
    private val freezeManager: FreezeManager,
    private val settings: FreezeSettings,
    private val exclusionChecker: FreezeExclusionChecker,
    private val profileManager: FreezeProfileManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onPowerSaveModeChanged()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun onScreenOff() {
        idleJob?.cancel()
        scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            if (profileManager.resolvedSettings.first().freezeOnScreenOff) {
                freezeCandidates()
            }
        }
    }

    private fun onScreenOn() {
        idleJob?.cancel()
        idleJob = scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            val resolved = profileManager.resolvedSettings.first()
            if (!resolved.freezeOnIdle) return@launch
            delay(resolved.idleTimeoutMinutes * 60_000L)
            freezeCandidates()
        }
    }

    private fun onPowerSaveModeChanged() {
        val powerManager = context.getSystemService<PowerManager>() ?: return
        if (!powerManager.isPowerSaveMode) return
        scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            if (profileManager.resolvedSettings.first().freezeOnBatterySaver) {
                freezeCandidates()
            }
        }
    }

    private suspend fun freezeCandidates() {
        val candidates = settings.candidates.first()
        if (candidates.isEmpty()) return
        val freezable = candidates.filterNot { exclusionChecker.isExcluded(it) }
        if (freezable.isEmpty()) return
        freezeManager.refreshBackendState()
        freezeManager.freeze(freezable)
    }
}
