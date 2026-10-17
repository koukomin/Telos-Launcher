package de.mm20.launcher2.ui.store

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.ktx.tryStartActivity
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

    fun isPlayStoreInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(PLAY_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun installedFromPlay(context: Context, isFrozen: (String) -> Boolean): List<PlayStoreApp> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val apps = pm.getInstalledApplications(flags)
        return apps.mapNotNull { info ->
            if (info.packageName == PLAY_PACKAGE || info.packageName == context.packageName) return@mapNotNull null
            val installer = runCatching {
                PackageManagerCompat.getInstallSource(pm, info.packageName)
            }.getOrNull()
            val fromPlay = installer?.initiatingPackageName == PLAY_PACKAGE ||
                installer?.installingPackageName == PLAY_PACKAGE ||
                installer?.originatingPackageName == PLAY_PACKAGE
            if (!fromPlay) return@mapNotNull null
            val pkgInfo = runCatching { pm.getPackageInfo(info.packageName, flags) }.getOrNull()
            val versionCode = pkgInfo?.longVersionCode ?: 0L
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
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage(PLAY_PACKAGE)
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!context.tryStartActivity(market)) {
            context.tryStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openPlayUpdates(context: Context) {
        val intents = listOf(
            Intent("com.google.android.finsky.VIEW_MY_DOWNLOADS").setPackage(PLAY_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/myapps")).setPackage(PLAY_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=")).setPackage(PLAY_PACKAGE),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.tryStartActivity(intent)) return
        }
    }

    private fun versionParts(value: String): List<Long> =
        value.split(Regex("[^0-9]+")).mapNotNull { it.toLongOrNull() }
}
