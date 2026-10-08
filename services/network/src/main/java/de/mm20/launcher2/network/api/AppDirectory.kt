package de.mm20.launcher2.network.api

import kotlinx.coroutines.flow.StateFlow

/** An installed app (or a system uid) as the network layer sees it. */
data class AppEntry(
    /** Uid of the app without user id. Rules are stored under this value ([FlowInfo.appId]). */
    val appId: Int,
    /** Main package name. For apps sharing a uid, the first package. */
    val packageName: String,
    /** Name to show. */
    val label: String,
    /** All packages that run under this uid (more than one for shared user ids). */
    val packages: List<String> = listOf(packageName),
    val isSystem: Boolean = false,
    /** False when the app has no INTERNET permission, so it can never open a connection. */
    val hasInternet: Boolean = true,
    /** Epoch millis of first install, used for "newly installed" rules. */
    val installedAtMs: Long = 0L,
)

/**
 * Lookup of installed apps by uid, so that firewall, logs and UI can show names and rule lists.
 * The list is kept up to date when apps are installed and removed.
 */
interface AppDirectory {
    /** All installed apps, sorted by label. System uids without a package (root, dns...) are not listed; [labelFor] still names them. */
    val apps: StateFlow<List<AppEntry>>

    /** The uid of Telos itself without user id. */
    val ownAppId: Int

    /** Finds an app by uid (with or without user id). `null` when unknown. */
    fun byUid(uid: Int): AppEntry?

    /** Finds the app a package belongs to. */
    fun byPackage(packageName: String): AppEntry?

    /**
     * A name for the uid that is always non-null: the app label, or the system/unknown
     * placeholder from the string resources.
     */
    fun labelFor(uid: Int): String

    /** Reads the package list again. Called automatically on package changes. */
    suspend fun refresh()
}
