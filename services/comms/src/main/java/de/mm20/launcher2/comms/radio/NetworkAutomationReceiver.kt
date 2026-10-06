package de.mm20.launcher2.comms.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NetworkAutomationReceiver : BroadcastReceiver(), KoinComponent {
    private val commsSettings: CommsSettings by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        scope.launch {
            try {
                val snap = commsSettings.snapshot.first()
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> if (snap.screenOffLte) {
                        CellularRadio.applyPreferred(context, "lte")
                    }
                    Intent.ACTION_SCREEN_ON -> if (snap.screenOffLte) {
                        CellularRadio.applyPreferred(context, snap.preferredNetworkMode)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        if (snap.batterySaverLte) {
                            val pm = context.getSystemService(PowerManager::class.java)
                            val saver = pm?.isPowerSaveMode == true
                            CellularRadio.applyPreferred(context, if (saver) "lte" else snap.preferredNetworkMode)
                        }
                    }
                    Intent.ACTION_BOOT_COMPLETED -> CellularRadio.applyPreferred(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
