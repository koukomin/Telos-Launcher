package de.mm20.launcher2.contextprofiles

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.ContextProfileTrigger
import de.mm20.launcher2.preferences.ui.ContextProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

/**
 * Resolves which [ContextProfile], if any, should currently be active, given the user's
 * configured profiles and live trigger state (time, WiFi, Bluetooth, Battery Saver).
 *
 * Triggers are only evaluated while the launcher is in the foreground - there is no persistent
 * background service polling for them. [tick] is driven by the composable that hosts the home
 * screen (on resume, and periodically while visible), which keeps this bounded in scope and
 * battery impact at the cost of a delay between a trigger condition changing and the profile
 * actually switching.
 */
class ContextProfileManager internal constructor(
    private val context: Context,
    private val settings: ContextProfileSettings,
    private val permissionsManager: PermissionsManager,
) {
    private val connectedBluetoothDeviceNames = MutableStateFlow<Set<String>>(emptySet())

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return
                val name = try {
                    device.name
                } catch (e: SecurityException) {
                    null
                } ?: return
                connectedBluetoothDeviceNames.value = when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> connectedBluetoothDeviceNames.value + name
                    else -> connectedBluetoothDeviceNames.value - name
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Emits once immediately, then every minute - drives periodic re-evaluation of triggers. */
    private val tick = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1.minutes)
        }
    }

    val activeProfile: Flow<ContextProfile?> = combine(
        settings.enabled,
        settings.profiles,
        settings.manualOverrideId,
        tick,
    ) { enabled, profiles, manualOverrideId, _ ->
        resolveActiveProfile(enabled, profiles, manualOverrideId)
    }

    internal fun resolveActiveProfile(
        enabled: Boolean,
        profiles: List<ContextProfile>,
        manualOverrideId: String?,
    ): ContextProfile? {
        if (!enabled || profiles.isEmpty()) return null
        if (manualOverrideId != null) {
            return profiles.firstOrNull { it.id == manualOverrideId }
        }
        val now = LocalTime.now()
        val ssid = currentWifiSsid()
        val batterySaverOn = isBatterySaverOn()
        val connectedDevices = connectedBluetoothDeviceNames.value
        return profiles.firstOrNull { profile ->
            when (val trigger = profile.trigger) {
                ContextProfileTrigger.Manual -> false
                is ContextProfileTrigger.TimeWindow -> isInWindow(now, trigger)
                is ContextProfileTrigger.Wifi -> ssid != null && ssid in trigger.ssids
                is ContextProfileTrigger.Bluetooth -> connectedDevices.any { it in trigger.deviceNames }
                ContextProfileTrigger.BatterySaver -> batterySaverOn
            }
        }
    }

    private fun isInWindow(now: LocalTime, window: ContextProfileTrigger.TimeWindow): Boolean {
        val start = LocalTime.of(window.startHour, window.startMinute)
        val end = LocalTime.of(window.endHour, window.endMinute)
        if (start == end) return false
        return if (start < end) {
            now >= start && now < end
        } else {
            // Window wraps past midnight (e.g. 22:00 - 06:00).
            now >= start || now < end
        }
    }

    private fun currentWifiSsid(): String? {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.Location)) return null
        return try {
            val wifiManager = context.getSystemService<WifiManager>() ?: return null
            val ssid = wifiManager.connectionInfo?.ssid ?: return null
            ssid.removeSurrounding("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun isBatterySaverOn(): Boolean {
        val powerManager = context.getSystemService<PowerManager>() ?: return false
        return powerManager.isPowerSaveMode
    }
}
