package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Linux Kernel TCP Congestion Control Tuner (Root).
 * Switches TCP congestion control to BBR (Bottleneck Bandwidth and RTT)
 * for up to 40% lower packet loss and higher throughput on 5G/Wi-Fi.
 */
object TcpBbrTuner {
    private const val TAG = "TcpBbrTuner"

    suspend fun enableBbr(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("sysctl -w net.ipv4.tcp_congestion_control=bbr")
            Log.i(TAG, "Switched TCP congestion control to BBR")
            true
        }.getOrDefault(false)
    }

    suspend fun resetToCubic(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("sysctl -w net.ipv4.tcp_congestion_control=cubic")
            Log.i(TAG, "Reset TCP congestion control to cubic")
            true
        }.getOrDefault(false)
    }
}
