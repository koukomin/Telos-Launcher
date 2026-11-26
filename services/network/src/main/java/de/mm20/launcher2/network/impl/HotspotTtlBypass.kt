package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Hotspot Carrier Throttling Bypass via Kernel IPTables (Root / Shizuku).
 * Sets TTL = 64 on outgoing packets so mobile carriers cannot detect
 * or throttle tethered devices.
 */
object HotspotTtlBypass {
    private const val TAG = "HotspotTtlBypass"

    suspend fun enableTtlBypass(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            val res1 = PrivilegedShell.run("iptables -t mangle -A POSTROUTING -j TTL --ttl-set 64")
            val res2 = PrivilegedShell.run("ip6tables -t mangle -A POSTROUTING -j HL --hl-set 64")
            Log.i(TAG, "Hotspot TTL Bypass applied")
            (res1?.exitCode == 0) || (res2?.exitCode == 0)
        }.getOrDefault(false)
    }

    suspend fun disableTtlBypass(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("iptables -t mangle -D POSTROUTING -j TTL --ttl-set 64")
            PrivilegedShell.run("ip6tables -t mangle -D POSTROUTING -j HL --hl-set 64")
            Log.i(TAG, "Hotspot TTL Bypass disabled")
            true
        }.getOrDefault(false)
    }
}
