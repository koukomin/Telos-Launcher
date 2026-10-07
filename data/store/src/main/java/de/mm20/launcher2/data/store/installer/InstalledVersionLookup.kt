package de.mm20.launcher2.data.store.installer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** What the package manager knows about an installed app. */
internal data class InstalledInfo(val versionCode: Long, val versionName: String?)

/** The installed version of [packageName], or null if it's not (or no longer) installed. */
internal fun resolveInstalledInfo(context: Context, packageName: String): InstalledInfo? {
    return try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        InstalledInfo(code, info.versionName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** The installed `versionCode` for [packageName], or null if it's not (or no longer) installed. */
internal fun resolveInstalledVersionCode(context: Context, packageName: String): Long? =
    resolveInstalledInfo(context, packageName)?.versionCode
