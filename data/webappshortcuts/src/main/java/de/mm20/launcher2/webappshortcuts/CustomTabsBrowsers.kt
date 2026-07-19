package de.mm20.launcher2.webappshortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri

/**
 * Detects which installed browsers actually implement the Custom Tabs service - many apps
 * register as an ACTION_VIEW handler for http/https without supporting Custom Tabs at all, so
 * that alone isn't enough to offer them as a renderer choice.
 */
object CustomTabsBrowsers {

    /** Installed browsers that support Custom Tabs, as a list of package names. */
    fun findSupportedBrowsers(context: Context): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val candidates = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
        return candidates.filter { isCustomTabsSupported(context, it) }
    }

    fun isCustomTabsSupported(context: Context, packageName: String): Boolean {
        return try {
            CustomTabsClient.getPackageName(context, listOf(packageName), true) == packageName
        } catch (e: Exception) {
            false
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
