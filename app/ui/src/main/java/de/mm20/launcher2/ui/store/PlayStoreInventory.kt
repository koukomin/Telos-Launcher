package de.mm20.launcher2.ui.store

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class PlayStoreApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val frozen: Boolean,
    val icon: Drawable?,
    val playVersion: String?,
    val updateAvailable: Boolean?,
)

object PlayStoreInventory {
    const val PLAY_PACKAGE = "com.android.vending"
    const val AURORA_PACKAGE = "com.aurora.store"

    fun isPlayStoreInstalled(context: Context): Boolean {
        return runCatching {
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            context.packageManager.getApplicationInfo(PLAY_PACKAGE, flags)
            true
        }.getOrDefault(false)
    }

    fun isAuroraStoreInstalled(context: Context): Boolean {
        return runCatching {
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            context.packageManager.getApplicationInfo(AURORA_PACKAGE, flags)
            true
        }.getOrDefault(false)
    }

    fun getPreferredStorePackage(context: Context): String? {
        if (isPlayStoreInstalled(context)) return PLAY_PACKAGE
        if (isAuroraStoreInstalled(context)) return AURORA_PACKAGE
        return null
    }

    fun getPreferredStoreTitle(context: Context): String {
        if (isPlayStoreInstalled(context)) return "Play Store"
        if (isAuroraStoreInstalled(context)) return "Aurora Store"
        return "Store"
    }

    fun installedFromPlay(context: Context, isFrozen: (String) -> Boolean): List<PlayStoreApp> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val apps = pm.getInstalledApplications(flags)
        return apps.mapNotNull { info ->
            if (info.packageName == PLAY_PACKAGE) return@mapNotNull null
            val installer = runCatching {
                PackageManagerCompat.getInstallSource(pm, info.packageName)
            }.getOrNull()
            val fromPlay = installer?.initiatingPackageName == PLAY_PACKAGE ||
                installer?.installingPackageName == PLAY_PACKAGE ||
                installer?.originatingPackageName == PLAY_PACKAGE
            if (!fromPlay) return@mapNotNull null
            val pkgInfo = runCatching { pm.getPackageInfo(info.packageName, flags) }.getOrNull()
            val versionCode = pkgInfo?.let(androidx.core.content.pm.PackageInfoCompat::getLongVersionCode) ?: 0L
            PlayStoreApp(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                versionName = pkgInfo?.versionName,
                versionCode = versionCode,
                frozen = isFrozen(info.packageName),
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                playVersion = null,
                updateAvailable = null,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun listingVersion(packageName: String): String? {
        val url = "https://play.google.com/store/apps/details?id=$packageName&hl=en&gl=US"
        val html = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return null
        val patterns = listOf(
            Regex("""itemprop="softwareVersion"\s*content="([^"]+)""""),
            Regex("""Current Version</div><span[^>]*>([^<]+)"""),
            Regex(""""softwareVersion"\s*:\s*"([^"]+)""""),
        )
        val raw = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.trim()
        if (raw.isNullOrBlank() || raw.equals("Varies with device", ignoreCase = true)) return null
        return raw
    }

    fun updateAvailable(installed: String?, listing: String?): Boolean? {
        if (installed.isNullOrBlank() || listing.isNullOrBlank()) return null
        val a = versionParts(installed)
        val b = versionParts(listing)
        if (a.isEmpty() || b.isEmpty()) return if (installed != listing) true else null
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrElse(i) { 0L }
            val bi = b.getOrElse(i) { 0L }
            if (bi > ai) return true
            if (bi < ai) return false
        }
        return false
    }

    fun openPlayApp(context: Context, packageName: String) {
        val directPlayIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(PLAY_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!context.tryStartActivity(directPlayIntent)) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .setPackage(PLAY_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.tryStartActivity(market)
        }
    }

    const val OBTAINIUM_PACKAGE = "dev.imranr.obtainium"
    const val FDROID_PACKAGE = "org.fdroid.fdroid"
    const val DROIDIFY_PACKAGE = "com.looker.droidify"
    const val ACCRESCENT_PACKAGE = "app.accrescent.client"
    const val ORION_PACKAGE = "com.orion.store"

    suspend fun unfreezeAppsForStore(context: Context, freezeManager: de.mm20.launcher2.freeze.FreezeManager, storePackageName: String) {
        val pm = context.packageManager
        if (freezeManager.isFrozen(storePackageName)) {
            freezeManager.unfreeze(storePackageName)
        }
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val apps = pm.getInstalledApplications(flags)
        for (info in apps) {
            if (freezeManager.isFrozen(info.packageName)) {
                val installer = runCatching {
                    PackageManagerCompat.getInstallSource(pm, info.packageName).installingPackageName
                        ?: PackageManagerCompat.getInstallSource(pm, info.packageName).initiatingPackageName
                }.getOrNull()

                val isInstalledByStore = when (storePackageName) {
                    PLAY_PACKAGE -> installer == PLAY_PACKAGE
                    AURORA_PACKAGE -> installer == AURORA_PACKAGE
                    OBTAINIUM_PACKAGE -> installer == OBTAINIUM_PACKAGE
                    FDROID_PACKAGE -> installer == FDROID_PACKAGE || installer == "org.fdroid.fdroid.privileged"
                    DROIDIFY_PACKAGE -> installer == DROIDIFY_PACKAGE
                    ACCRESCENT_PACKAGE -> installer == ACCRESCENT_PACKAGE
                    ORION_PACKAGE -> installer == ORION_PACKAGE || installer == "org.orion.store"
                    else -> installer == storePackageName
                }

                if (isInstalledByStore) {
                    freezeManager.unfreeze(info.packageName)
                }
            }
        }
    }

    suspend fun openPreferredStoreUpdates(context: Context, freezeManager: de.mm20.launcher2.freeze.FreezeManager? = null) {
        val targetPkg = getPreferredStorePackage(context) ?: PLAY_PACKAGE
        
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, "Ξεπάγωμα εφαρμογών για ενημέρωση...", android.widget.Toast.LENGTH_SHORT).show()
        }

        if (freezeManager != null) {
            runCatching {
                unfreezeAppsForStore(context, freezeManager, targetPkg)
            }
        }

        if (targetPkg == AURORA_PACKAGE) {
            val intent = context.packageManager.getLaunchIntentForPackage(AURORA_PACKAGE)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$AURORA_PACKAGE")).apply {
                    setPackage(AURORA_PACKAGE)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.tryStartActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://collection/updates"))
                .setPackage(PLAY_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!context.tryStartActivity(intent)) {
                openPlayUpdates(context)
            }
        }
    }

    fun openPlayUpdates(context: Context) {
        val directPlayIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(PLAY_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!context.tryStartActivity(directPlayIntent)) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE"))
                .setPackage(PLAY_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.tryStartActivity(market)
        }
    }

    private fun versionParts(value: String): List<Long> =
        value.split(Regex("[^0-9]+")).mapNotNull { it.toLongOrNull() }
}
