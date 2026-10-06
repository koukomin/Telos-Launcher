package de.mm20.launcher2.comms.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Listens to screen and battery saver changes only while a network automation is switched on, so
 * the launcher does not react to these broadcasts when the feature is unused.
 */
class NetworkAutomationWatcher(context: Context, settings: CommsSettings) {

    private val appContext = context.applicationContext
    private val receiver = NetworkAutomationReceiver()
    private var registered = false

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            combine(settings.screenOffLte, settings.batterySaverLte) { a, b -> a || b }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) register() else unregister() }
        }
    }

    private fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        appContext.registerReceiver(receiver, filter)
        registered = true
    }

    private fun unregister() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver as BroadcastReceiver) }
        registered = false
    }
}
