package de.mm20.launcher2.data.store.security

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

data class ApkSecurityReport(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val sha256: String,
    val permissions: List<String>,
    val dangerousPermissions: List<String>,
    val riskLevel: RiskLevel,
    val warnings: List<String>,
) {
    enum class RiskLevel {
        CLEAN,
        LOW,
        MODERATE,
        HIGH,
    }
}

object ApkSecurityScanner {

    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.GET_TASKS",
    )

    fun scanApk(context: Context, apkFile: File): ApkSecurityReport? {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
        val info = pm.getPackageArchiveInfo(apkFile.path, flags) ?: return null

        val packageName = info.packageName ?: "unknown.package"
        val versionName = info.versionName
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        val sha256 = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { input ->
                val buffer =ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

        val requestedPermissions = info.requestedPermissions?.toList().orEmpty()
        val dangerous = requestedPermissions.filter { it in DANGEROUS_PERMISSIONS }

        val warnings = mutableListOf<String>()
        if (dangerous.contains("android.permission.BIND_DEVICE_ADMIN")) {
            warnings.add("Requests Device Administrator privileges")
        }
        if (dangerous.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
            warnings.add("Can draw over other apps (Overlay permission)")
        }
        if (dangerous.contains("android.permission.RECORD_AUDIO") || dangerous.contains("android.permission.CAMERA")) {
            warnings.add("Requests access to Microphone and/or Camera")
        }
        if (dangerous.contains("android.permission.ACCESS_FINE_LOCATION")) {
            warnings.add("Requests precise location access")
        }

        val riskLevel = when {
            dangerous.size > 8 || warnings.size >= 3 -> ApkSecurityReport.RiskLevel.HIGH
            dangerous.size > 4 -> ApkSecurityReport.RiskLevel.MODERATE
            dangerous.isNotEmpty() -> ApkSecurityReport.RiskLevel.LOW
            else -> ApkSecurityReport.RiskLevel.CLEAN
        }

        return ApkSecurityReport(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            sha256 = sha256,
            permissions = requestedPermissions,
            dangerousPermissions = dangerous,
            riskLevel = riskLevel,
            warnings = warnings,
        )
    }

    @Suppress("DEPRECATION")
    fun verifyInstalledSignatureMatch(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.path, PackageManager.GET_SIGNATURES) ?: return true
        val packageName = archiveInfo.packageName ?: return true

        val installedInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return true // Clean install, app not present yet

        val archiveSigs = archiveInfo.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val installedSigs = installedInfo.signatures?.map { it.toCharsString() }?.toSet().orEmpty()

        if (archiveSigs.isEmpty() || installedSigs.isEmpty()) return true
        return archiveSigs.intersect(installedSigs).isNotEmpty()
    }
}
