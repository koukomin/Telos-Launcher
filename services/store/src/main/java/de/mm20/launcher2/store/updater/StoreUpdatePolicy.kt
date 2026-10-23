package de.mm20.launcher2.store.updater

import android.app.AlarmManager
import android.content.Context
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.ItemOptions

object StoreUpdatePolicy {
    fun discoverInstaller(installerPackage: String?): Boolean = installerPackage != "com.android.vending"

    fun checksUpdates(options: ItemOptions): Boolean = options.checkForUpdates

    fun isTelosLauncher(item: StoreItem, launcherPackage: String): Boolean {
        if (item.id == "telos-self" || item.packageName == launcherPackage) return true
        val source = item.source as? AppSource.GitHub ?: return false
        return source.owner.equals("koukomin", ignoreCase = true) &&
            source.repo.equals("Telos-Launcher", ignoreCase = true)
    }

    fun automatic(options: ItemOptions, globalEnabled: Boolean, context: Context? = null): Boolean {
        if (context != null) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am?.nextAlarmClock != null) {
                // Active alarm is set on system/Telos! Disable background auto-update to ensure alarm is not missed
                return false
            }
        }
        return !options.trackOnly && !options.pinned && !options.excludeFromBackground && globalEnabled && options.autoUpdate != false
    }
}
