package de.mm20.launcher2.data.store.installer

import android.content.Context
import de.mm20.launcher2.data.store.security.ApkSecurityScanner
import java.io.File

/** APK scan, signature match, and pre-update backup. Freeze leases stay on [TelosPackageInstaller]. */
internal object StoreInstallSecurity {
    fun scanAndLog(context: Context, apk: File, packageName: String) {
        StoreTerminalLogs.log("SECURITY", "Scanning APK $packageName...")
        val securityReport = ApkSecurityScanner.scanApk(context, apk)
        if (securityReport != null) {
            StoreTerminalLogs.log("SECURITY", "Risk Level: ${securityReport.riskLevel.name}")
        }
    }

    fun signatureMatchesInstalled(context: Context, apk: File, packageName: String): Boolean {
        StoreTerminalLogs.log("SIGNATURE", "Verifying certificate signature match for $packageName...")
        val signatureMatch = ApkSecurityScanner.verifyInstalledSignatureMatch(context, apk)
        if (!signatureMatch) {
            StoreTerminalLogs.log("ERROR", "Signature mismatch for $packageName! Install blocked.")
        }
        return signatureMatch
    }

    fun backupInstalledApk(context: Context, packageName: String) {
        runCatching {
            val pm = context.packageManager
            val flags = android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
            val info = pm.getApplicationInfo(packageName, flags)
            val installedApk = File(info.sourceDir)
            if (installedApk.exists()) {
                val backupDir = File(context.cacheDir, "apk-backups").apply { mkdirs() }
                val backupFile = File(backupDir, "$packageName-backup.apk")
                installedApk.copyTo(backupFile, overwrite = true)
                StoreTerminalLogs.log("BACKUP", "Created pre-update rollback backup for $packageName")
            }
        }
    }
}
