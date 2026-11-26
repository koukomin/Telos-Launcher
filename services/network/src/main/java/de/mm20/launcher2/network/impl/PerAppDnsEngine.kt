package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Per-App Split-DNS Engine via Root / IPTables.
 * Redirects DNS requests of selected app UIDs to encrypted DNS ports (e.g. DoH/NextDNS)
 * while leaving game traffic or low-latency apps unaffected.
 */
object PerAppDnsEngine {
    private const val TAG = "PerAppDnsEngine"

    suspend fun redirectAppDns(uid: Int, targetPort: Int = 5353): Boolean {
        if (!PrivilegedShell.rootAvailable() || uid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -t nat -A OUTPUT -m owner --uid-owner $uid -p udp --dport 53 -j REDIRECT --to-ports $targetPort")
            PrivilegedShell.run("iptables -t nat -A OUTPUT -m owner --uid-owner $uid -p tcp --dport 53 -j REDIRECT --to-ports $targetPort")
            Log.i(TAG, "Redirected DNS for app UID $uid to port $targetPort")
            true
        }.getOrDefault(false)
    }

    suspend fun clearAppDnsRedirect(uid: Int, targetPort: Int = 5353): Boolean {
        if (!PrivilegedShell.rootAvailable() || uid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -t nat -D OUTPUT -m owner --uid-owner $uid -p udp --dport 53 -j REDIRECT --to-ports $targetPort")
            PrivilegedShell.run("iptables -t nat -D OUTPUT -m owner --uid-owner $uid -p tcp --dport 53 -j REDIRECT --to-ports $targetPort")
            Log.i(TAG, "Cleared DNS redirect for app UID $uid")
            true
        }.getOrDefault(false)
    }
}
