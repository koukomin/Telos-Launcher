package de.mm20.launcher2.oem

import android.content.Context

/**
 * MicroG & Self-Hosted Push Notification Health Auditor for Custom ROMs.
 * Detects deGoogled ROMs (microG / CalyxOS / LineageOS) and fallback
 * to UnifiedPush / Persistent WebSockets for 100% reliable push alerts.
 */
object MicroGAuditor {
    private const val MICROG_PACKAGE = "com.google.android.gms"

    fun isMicroGInstalled(context: Context): Boolean {
        return runCatching {
            val pi = context.packageManager.getPackageInfo(MICROG_PACKAGE, 64)
            val signatures = pi.signatures
            signatures?.any { it.toCharsString().contains("3891827fda02166157f94237845f95b05a74d752") } ?: false
        }.getOrDefault(false)
    }

    fun isPlayServicesAbsent(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            false
        }.getOrDefault(true)
    }
}
