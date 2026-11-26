package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Background Traffic Rate Limiter (Data Saver) via Root / IPTables & tc.
 * Limits background data throughput to 128 Kbps on metered cellular networks
 * to prevent mobile data plan exhaustion.
 */
object BackgroundTrafficShaper {
    private const val TAG = "BackgroundTrafficShaper"

    suspend fun limitBackgroundRate(iface: String = "rmnet_data0", rateKbps: Int = 128): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("tc qdisc add dev $iface root tbf rate ${rateKbps}kbit burst 32k latency 400ms 2>/dev/null")
            Log.i(TAG, "Applied background traffic shaper ($rateKbps Kbps) on $iface")
            true
        }.getOrDefault(false)
    }

    suspend fun removeRateLimit(iface: String = "rmnet_data0"): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("tc qdisc del dev $iface root 2>/dev/null")
            Log.i(TAG, "Removed background traffic shaper on $iface")
            true
        }.getOrDefault(false)
    }
}
