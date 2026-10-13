package de.mm20.launcher2.store.updater

import de.mm20.launcher2.store.options.ItemOptions

object StoreUpdatePolicy {
    fun discoverInstaller(installerPackage: String?): Boolean = installerPackage != "com.android.vending"
    fun automatic(options: ItemOptions, globalEnabled: Boolean): Boolean =
        !options.trackOnly && !options.pinned && !options.excludeFromBackground && (options.autoUpdate ?: globalEnabled)
}
