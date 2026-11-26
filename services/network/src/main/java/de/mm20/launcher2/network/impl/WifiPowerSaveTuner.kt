package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Wi-Fi Power Save Mode Disabler (Gamer Low-Ping Mode) via Root.
 * Disables Wi-Fi chip power-saving mode during gaming or speed tests
 * to reduce ping jitter by 50-70%.
 */
object WifiPowerSaveTuner {
    private const val TAG = "WifiPowerSaveTuner"

    suspend fun disablePowerSave(iface: String = "wlan0"): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            val res = PrivilegedShell.run("iw dev $iface set power_save off")
            Log.i(TAG, "Disabled Wi-Fi power save on $iface")
            res?.exitCode == 0
        }.getOrDefault(false)
    }

    suspend fun enablePowerSave(iface: String = "wlan0"): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            val res = PrivilegedShell.run("iw dev $iface set power_save on")
            Log.i(TAG, "Enabled Wi-Fi power save on $iface")
            res?.exitCode == 0
        }.getOrDefault(false)
    }
}
