package de.mm20.launcher2.store.updater

import android.app.AlarmManager
import android.content.Context
import de.mm20.launcher2.store.options.ItemOptions

object StoreUpdatePolicy {
    fun discoverInstaller(installerPackage: String?): Boolean = installerPackage != "com.android.vending"

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
