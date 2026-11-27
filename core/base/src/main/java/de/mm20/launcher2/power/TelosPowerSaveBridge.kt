package de.mm20.launcher2.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dynamic Power Saver Mode Sync for Telos Launcher.
 * Monitors Android Power Saver Mode (PowerManager.isPowerSaveMode)
 * and emits state to pause video wallpapers and lower animation rates.
 */
object TelosPowerSaveBridge {

    private val _isPowerSave = MutableStateFlow(false)
    val isPowerSave: StateFlow<Boolean> = _isPowerSave.asStateFlow()

    fun init(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isPowerSave.value = pm?.isPowerSaveMode ?: false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    _isPowerSave.value = pm?.isPowerSaveMode ?: false
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
    }
}
