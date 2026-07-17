package de.mm20.launcher2.freeze.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Freezes/unfreezes apps through Island (com.oasisfeng.island), using its documented public
 * intent API (`com.oasisfeng.island.action.FREEZE` / `UNFREEZE` with a `packages:` data URI).
 *
 * Important limitations:
 * - This only works for apps the user has already cloned/moved into their Island profile.
 * - Island's API is activity-based, so we cannot observe the result synchronously. The methods
 *   here report whether Island was able to *receive* the request (installed, handles the action),
 *   not whether the app actually ended up frozen. We deliberately do NOT report blanket success.
 */
internal class IslandProvider(private val context: Context) {

    fun isAvailable(): Boolean {
        return try {
            context.packageManager.getPackageInfo(ISLAND_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean): Boolean {
        if (packageNames.isEmpty()) return true
        val action = if (suspended) ACTION_FREEZE else ACTION_UNFREEZE
        val intent = Intent(action).apply {
            data = Uri.parse("packages:" + packageNames.joinToString(","))
            `package` = ISLAND_PACKAGE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If Island doesn't handle the action (wrong/old version), don't claim success.
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Island freeze intent failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "IslandProvider"
        private const val ISLAND_PACKAGE = "com.oasisfeng.island"
        private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
        private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"
    }
}
