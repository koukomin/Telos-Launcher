package de.mm20.launcher2.network.impl

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * Captive Portal Auto-Bypass via Shizuku / Root.
 * Automatically dismisses or re-checks public Wi-Fi login popups (hotel/cafe/airport).
 */
object CaptivePortalBypass {
    private const val TAG = "CaptivePortalBypass"

    suspend fun recheckCaptivePortal(): Boolean {
        if (!PrivilegedShell.available()) return false
        return runCatching {
            val res = PrivilegedShell.run("cmd connectivity captiveportal")
            Log.i(TAG, "Rechecked captive portal state")
            res?.exitCode == 0
        }.getOrDefault(false)
    }
}
