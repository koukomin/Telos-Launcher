package de.mm20.launcher2.oem

import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell

/**
 * System Runtime Resource Overlay (RRO) Manager via Shizuku / Root.
 * Enables/disables system-wide theme overlays (fonts, status bar icons, Monet tints)
 * directly from Telos Appearance Settings.
 */
object SystemOverlayManager {
    private const val TAG = "SystemOverlayManager"

    suspend fun setOverlayState(overlayPackage: String, enable: Boolean): Boolean {
        if (!PrivilegedShell.available() || overlayPackage.isBlank()) return false
        val cmd = if (enable) "cmd overlay enable $overlayPackage" else "cmd overlay disable $overlayPackage"
        return runCatching {
            val res = PrivilegedShell.run(cmd)
            Log.i(TAG, "Changed overlay state for $overlayPackage to $enable")
            res?.exitCode == 0
        }.getOrDefault(false)
    }
}
