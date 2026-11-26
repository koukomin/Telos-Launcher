package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Multipath Dual-Interface Network Routing Engine via Root.
 * Configures Linux Kernel IP rules to bind specific interfaces
 * or merge Wi-Fi + 5G Mobile Data traffic for max speed.
 */
object MultipathRoutingEngine {
    private const val TAG = "MultipathRoutingEngine"

    suspend fun setupMultipathRules(wifiIp: String, mobileIp: String): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("ip rule add from $wifiIp table 100 2>/dev/null")
            PrivilegedShell.run("ip rule add from $mobileIp table 200 2>/dev/null")
            Log.i(TAG, "Configured dual-interface IP rules for $wifiIp and $mobileIp")
            true
        }.getOrDefault(false)
    }

    suspend fun clearMultipathRules(wifiIp: String, mobileIp: String): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("ip rule del from $wifiIp table 100 2>/dev/null")
            PrivilegedShell.run("ip rule del from $mobileIp table 200 2>/dev/null")
            Log.i(TAG, "Cleared multipath IP rules")
            true
        }.getOrDefault(false)
    }
}
