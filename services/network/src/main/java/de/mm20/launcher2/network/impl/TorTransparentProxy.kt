package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Tor Transparent Proxy Engine via Root / IPTables.
 * Automatically routes all TCP traffic through the local Tor SOCKS proxy port
 * without needing app-specific proxy configurations.
 */
object TorTransparentProxy {
    private const val TAG = "TorTransparentProxy"

    suspend fun enableTransparentProxy(torUid: Int, torTransPort: Int = 9040, torDnsPort: Int = 5353): Boolean {
        if (!PrivilegedShell.rootAvailable() || torUid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -t nat -A OUTPUT -m owner ! --uid-owner $torUid -p tcp -j REDIRECT --to-ports $torTransPort")
            PrivilegedShell.run("iptables -t nat -A OUTPUT -m owner ! --uid-owner $torUid -p udp --dport 53 -j REDIRECT --to-ports $torDnsPort")
            Log.i(TAG, "Tor Transparent Proxy enabled on port $torTransPort")
            true
        }.getOrDefault(false)
    }

    suspend fun disableTransparentProxy(torUid: Int, torTransPort: Int = 9040, torDnsPort: Int = 5353): Boolean {
        if (!PrivilegedShell.rootAvailable() || torUid <= 0) return false
        return runCatching {
            PrivilegedShell.run("iptables -t nat -D OUTPUT -m owner ! --uid-owner $torUid -p tcp -j REDIRECT --to-ports $torTransPort")
            PrivilegedShell.run("iptables -t nat -D OUTPUT -m owner ! --uid-owner $torUid -p udp --dport 53 -j REDIRECT --to-ports $torDnsPort")
            Log.i(TAG, "Tor Transparent Proxy disabled")
            true
        }.getOrDefault(false)
    }
}
