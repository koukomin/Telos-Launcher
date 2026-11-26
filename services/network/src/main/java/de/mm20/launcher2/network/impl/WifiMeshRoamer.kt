package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Wi-Fi Fast Mesh BSSID Roaming Controller via Shizuku / Root.
 * Triggers instant roaming to the strongest access point (BSSID) in Mesh Wi-Fi
 * when RSSI signal drops below -70 dBm.
 */
object WifiMeshRoamer {
    private const val TAG = "WifiMeshRoamer"

    suspend fun triggerRoam(targetBssid: String): Boolean {
        if (!PrivilegedShell.available() || targetBssid.isBlank()) return false
        return runCatching {
            val res1 = PrivilegedShell.run("wpa_cli -i wlan0 roam $targetBssid")
            val res2 = PrivilegedShell.run("cmd wifi connect-network -b $targetBssid")
            Log.i(TAG, "Triggered fast Wi-Fi roam to BSSID $targetBssid")
            (res1?.exitCode == 0) || (res2?.exitCode == 0)
        }.getOrDefault(false)
    }
}
