package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell
import java.security.SecureRandom

/**
 * MAC Address Randomization Engine via Root.
 * Spoofs the Wi-Fi interface MAC address on public networks
 * to prevent physical location tracking and fingerprinting.
 */
object MacSpoofer {
    private const val TAG = "MacSpoofer"

    private fun generateRandomMac(): String {
        val random = SecureRandom()
        val macBytes = ByteArray(6).also { random.nextBytes(it) }
        // Ensure unicast and locally administered
        macBytes[0] = ((macBytes[0].toInt() and 0xFC) or 0x02).toByte()
        return macBytes.joinToString(":") { String.format("%02x", it) }
    }

    suspend fun randomizeMac(iface: String = "wlan0"): String? {
        if (!PrivilegedShell.rootAvailable()) return null
        val mac = generateRandomMac()
        return runCatching {
            PrivilegedShell.run("ip link set dev $iface down")
            PrivilegedShell.run("ip link set dev $iface address $mac")
            PrivilegedShell.run("ip link set dev $iface up")
            Log.i(TAG, "Randomized $iface MAC address to $mac")
            mac
        }.getOrNull()
    }
}
