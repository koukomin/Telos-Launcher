package de.mm20.launcher2.store.updater

import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem

data class CheckSummary(
    /** Apps that have a newer version than what is installed */
    val updates: List<StoreItem>,
    /** Apps that were installed by this check (silent updates) */
    val installed: List<StoreItem>,
    /** Apps whose source could not be reached */
    val failed: Int,
)

data class ImportSummary(val added: Int, val skipped: Int, val unsupported: Int)

/** Checks the sources of all tracked apps for new versions (the Store's background check). */
interface StoreUpdater {
    /**
     * Asks every source for its newest release and stores the answer. With [background] the apps
     * marked "exclude from background" are left alone, the user is notified and, when allowed and
     * possible, updates are installed silently.
     */
    suspend fun checkAll(background: Boolean): CheckSummary

    /** Checks one app (the app's page, "check now") */
    suspend fun checkOne(item: StoreItem): Boolean
}

/** Import, export and discovery helpers for the Store. */
interface StoreTools {
    /** The tracked apps in the format of Obtainium's export file, so they can be moved both ways */
    suspend fun exportObtainium(): String

    /** Adds the apps of an Obtainium export (or of an export of this Store) */
    suspend fun importObtainium(json: String): ImportSummary

    /** Looks for the source of an app that is already installed: F-Droid first, then IzzyOnDroid */
    suspend fun findSourceForInstalled(packageName: String): AppSource?

    /** Adds the app at [url] and returns the new item, or an error text */
    suspend fun addFromUrl(url: String, packageName: String = ""): Result<StoreItem>
}
