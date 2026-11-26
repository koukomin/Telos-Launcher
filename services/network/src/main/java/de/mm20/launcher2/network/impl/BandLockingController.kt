package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * 5G / 4G Band Locking Controller via Shizuku / Root.
 * Forces preferred network band types to bypass carrier congestion
 * and maximize throughput.
 */
object BandLockingController {
    private const val TAG = "BandLockingController"

    // Android RIL Network Type IDs
    const val NETWORK_MODE_NR_LTE_CDMA_EVDO_GSM_WCDMA = 27 // 5G Preferred
    const val NETWORK_MODE_LTE_GSM_WCDMA = 9               // 4G Preferred
    const val NETWORK_MODE_NR_ONLY = 28                    // 5G SA Only

    suspend fun setPreferredNetworkType(subId: Int = 1, typeId: Int): Boolean {
        if (!PrivilegedShell.available()) return false
        return runCatching {
            val res1 = PrivilegedShell.run("cmd phone set-preferred-network-type $subId $typeId")
            val res2 = PrivilegedShell.run("service call phone 73 i32 $subId i32 $typeId")
            Log.i(TAG, "Set preferred network type $typeId for subId $subId")
            (res1?.exitCode == 0) || (res2?.exitCode == 0)
        }.getOrDefault(false)
    }
}
