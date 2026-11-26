package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Zero-VPN Kernel Firewall Engine using Root / Shizuku iptables.
 * Blocks app traffic directly in the Linux Kernel without starting
 * a local VpnService loopback interface (0% battery drain).
 */
object KernelFirewallEngine {
    private const val TAG = "KernelFirewallEngine"

    suspend fun blockApp(uid: Int): Boolean {
        if (!PrivilegedShell.rootAvailable() || uid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -A OUTPUT -m owner --uid-owner $uid -j DROP")
            PrivilegedShell.run("ip6tables -A OUTPUT -m owner --uid-owner $uid -j DROP")
            Log.i(TAG, "Blocked app UID $uid in Kernel Firewall")
            true
        }.getOrDefault(false)
    }

    suspend fun unblockApp(uid: Int): Boolean {
        if (!PrivilegedShell.rootAvailable() || uid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -D OUTPUT -m owner --uid-owner $uid -j DROP")
            PrivilegedShell.run("ip6tables -D OUTPUT -m owner --uid-owner $uid -j DROP")
            Log.i(TAG, "Unblocked app UID $uid in Kernel Firewall")
            true
        }.getOrDefault(false)
    }

    suspend fun flush(): Boolean {
        if (!PrivilegedShell.rootAvailable()) return false
        return runCatching {
            PrivilegedShell.run("iptables -F OUTPUT")
            PrivilegedShell.run("ip6tables -F OUTPUT")
            true
        }.getOrDefault(false)
    }
}
