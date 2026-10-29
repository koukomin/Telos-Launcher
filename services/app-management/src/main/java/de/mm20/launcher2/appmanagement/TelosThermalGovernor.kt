package de.mm20.launcher2.appmanagement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Battery & Thermal Governor for Telos Launcher.
 * Monitors battery level and charging state to automatically trigger
 * Ultra Power Saver mode (reducing wallpaper blur, capping FPS to 60Hz, throttling background tasks).
 */
class TelosThermalGovernor private constructor(context: Context) {

    data class GovernorState(
        val batteryPct: Int = 100,
        val isCharging: Boolean = false,
        val isPowerSaverActive: Boolean = false,
        val maxTargetFps: Int = 120
    )

    private val appContext: Context = context.applicationContext
    private val isRegistered = AtomicBoolean(false)

    private val _state = MutableStateFlow(GovernorState())
    val state: StateFlow<GovernorState> = _state.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryState(intent)
            }
        }
    }

    fun register() {
        if (!isRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        appContext.registerReceiver(batteryReceiver, filter)
        Log.i(TAG, "TelosThermalGovernor registered")
    }

    fun unregister() {
        if (!isRegistered.compareAndSet(true, false)) return
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
        Log.i(TAG, "TelosThermalGovernor unregistered")
    }

    private fun updateBatteryState(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val enablePowerSaver = !isCharging && pct <= 20

        _state.value = GovernorState(
            batteryPct = pct,
            isCharging = isCharging,
            isPowerSaverActive = enablePowerSaver,
            maxTargetFps = if (enablePowerSaver) 60 else 120
        )
    }

    companion object {
        private const val TAG = "TelosThermalGovernor"

        @Volatile
        private var instance: TelosThermalGovernor? = null

        fun getInstance(context: Context): TelosThermalGovernor {
            return instance ?: synchronized(this) {
                instance ?: TelosThermalGovernor(context.applicationContext).also { instance = it }
            }
        }
    }
}
