package de.mm20.launcher2.data.store.security

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class InureInspectionResult(
    val packageName: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val certIssuer: String?,
    val detectedTrackers: List<String>,
    val dangerousPermissions: List<String>,
    val normalPermissions: List<String>,
)

object AppInureInspector {

    private val KNOWN_TRACKERS = mapOf(
        "com.google.firebase.analytics" to "Google Firebase Analytics",
        "com.google.android.gms.ads" to "Google AdMob / Ads",
        "com.facebook.sdk" to "Facebook SDK",
        "com.facebook.appevents" to "Facebook App Events",
        "com.appsflyer" to "AppsFlyer Analytics",
        "com.adjust.sdk" to "Adjust Tracking",
        "com.unity3d.ads" to "Unity Ads",
        "com.flurry" to "Flurry Analytics",
        "com.mixpanel" to "Mixpanel Analytics",
        "com.segment" to "Segment Analytics",
    )

    fun inspectPackage(context: Context, packageName: String): InureInspectionResult? {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES or (if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val info = runCatching { pm.getPackageInfo(packageName, flags) }.getOrNull() ?: return null

        // Detect Trackers
        val trackers = mutableSetOf<String>()
        val allComponents = (info.services.orEmpty().map { it.name } +
                info.receivers.orEmpty().map { it.name } +
                info.activities.orEmpty().map { it.name })

        for ((prefix, name) in KNOWN_TRACKERS) {
            if (allComponents.any { it.contains(prefix, ignoreCase = true) }) {
                trackers.add(name)
            }
        }

        // Certificate & Fingerprints
        var sha256 = ""
        var sha1 = ""
        var issuer: String? = null

        @Suppress("DEPRECATION")
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        if (!signatures.isNullOrEmpty()) {
            val certBytes = signatures[0].toByteArray()
            sha256 = hashBytes(certBytes, "SHA-256")
            sha1 = hashBytes(certBytes, "SHA-1")

            runCatching {
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                issuer = cert.issuerDN?.name
            }
        }

        // Permissions
        val requested = info.requestedPermissions?.toList().orEmpty()
        val dangerous = mutableListOf<String>()
        val normal = mutableListOf<String>()

        for (perm in requested) {
            val pInfo = runCatching { pm.getPermissionInfo(perm, 0) }.getOrNull()
            if (pInfo != null && (pInfo.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) {
                dangerous.add(perm)
            } else {
                normal.add(perm)
            }
        }

        return InureInspectionResult(
            packageName = packageName,
            sha256Fingerprint = sha256,
            sha1Fingerprint = sha1,
            certIssuer = issuer,
            detectedTrackers = trackers.toList(),
            dangerousPermissions = dangerous,
            normalPermissions = normal,
        )
    }

    private fun hashBytes(bytes: ByteArray, algorithm: String): String {
        return runCatching {
            val digest = MessageDigest.getInstance(algorithm)
            val hash = digest.digest(bytes)
            hash.joinToString(":") { "%02X".format(it) }
        }.getOrDefault("")
    }
}
