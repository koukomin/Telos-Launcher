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
 * explicitly opted in via settings ([FreezeSettings.candidates]).
 *
 * Deliberately conservative for now: it only ever acts on that explicit opt-in list, never on
 * "whatever's running in the background". The exclusion-rule engine (never freeze foreground
 * apps, apps with an active notification/media session/foreground service, etc.) is a separate
 * stage that hasn't been built yet, so until it exists, this only touches apps the user picked.
 *
 * Registered as a Koin singleton so it's created (and starts listening) once, at app start.
 */
class AutoFreezeController internal constructor(
    private val context: Context,
    private val freezeManager: FreezeManager,
    private val settings: FreezeSettings,
    private val exclusionChecker: FreezeExclusionChecker,
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
            if (settings.autoFreezeEnabled.first() && settings.freezeOnScreenOff.first()) {
                freezeCandidates()
            }
        }
    }

    private fun onScreenOn() {
        idleJob?.cancel()
        idleJob = scope.launch {
            if (!settings.autoFreezeEnabled.first() || !settings.freezeOnIdle.first()) return@launch
            val timeoutMinutes = settings.idleTimeoutMinutes.first()
            delay(timeoutMinutes * 60_000L)
            freezeCandidates()
        }
    }

    private fun onPowerSaveModeChanged() {
        val powerManager = context.getSystemService<PowerManager>() ?: return
        if (!powerManager.isPowerSaveMode) return
        scope.launch {
            if (settings.autoFreezeEnabled.first() && settings.freezeOnBatterySaver.first()) {
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
