package de.mm20.launcher2.data.store.installer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The installed `versionCode` for [packageName], or null if it's not (or no longer) installed. */
internal fun resolveInstalledVersionCode(context: Context, packageName: String): Long? {
    return try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
