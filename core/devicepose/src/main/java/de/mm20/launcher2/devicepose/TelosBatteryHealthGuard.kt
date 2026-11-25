package de.mm20.launcher2.devicepose

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Immutable

/**
 * Immutable status container for Battery Health and Thermal Guard.
 */
@Immutable
data class BatteryHealthStatus(
    val temperatureCelsius: Float,
    val isOverheating: Boolean,
    val isCharging: Boolean,
    val batteryLevelPercent: Int
)

/**
 * Hardware Battery Health and Thermal Throttling Guard for Telos Launcher.
 * Monitors device battery temperature during charging to protect physical battery lifespan.
 */
object TelosBatteryHealthGuard {

    private const val OVERHEAT_THRESHOLD_CELSIUS = 45.0f

    /**
     * Reads current device battery temperature, charging state, and level.
     */
    fun checkStatus(context: Context): BatteryHealthStatus {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0f

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

        val isOverheating = tempCelsius >= OVERHEAT_THRESHOLD_CELSIUS

        return BatteryHealthStatus(
            temperatureCelsius = tempCelsius,
            isOverheating = isOverheating,
            isCharging = isCharging,
            batteryLevelPercent = pct
        )
    }
}
