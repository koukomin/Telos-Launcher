package de.mm20.launcher2.oem

import android.content.Context

/**
 * OEM Dual Apps & App Cloner Detector for Telos Launcher.
 * Identifies cloned app instances from Samsung Dual Messenger,
 * Xiaomi Dual Apps, and ColorOS App Cloner.
 */
object DualAppDetector {
    private val CLONE_PACKAGES = setOf(
        "com.samsung.android.da.daagent", // Samsung Dual Messenger
        "com.miui.securityadd",            // Xiaomi Dual Apps
        "com.coloros.appclone",           // ColorOS App Cloner
        "com.vivo.doubleinstance",        // Vivo App Clone
    )

    fun isClonedApp(context: Context, packageName: String, userId: Int): Boolean {
        if (userId != 0) return true
        return packageName.contains("clone", ignoreCase = true) ||
            packageName.contains("dual", ignoreCase = true) ||
            CLONE_PACKAGES.any { isPackageInstalled(context, it) }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}
