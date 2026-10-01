package de.mm20.launcher2.store.model

import kotlinx.serialization.Serializable

/**
 * An app tracked by the Store, whether or not it's currently installed. [StoreUpdateWorker]
 * (`:data:store`) periodically refreshes [latestRelease] for every item via the [source]'s
 * fetcher; [installedVersionCode] is updated whenever Telos observes the app being
 * installed/updated/removed on-device.
 */
@Serializable
data class StoreItem(
    /** Stable id, independent of [packageName] so re-pointing a source doesn't lose history. */
    val id: String,
    val packageName: String,
    val displayName: String,
    val source: AppSource,
    val installedVersionCode: Long? = null,
    val latestRelease: ReleaseArtifact? = null,
    /** Epoch millis of the last successful [latestRelease] refresh. */
    val lastCheckedAt: Long? = null,
) {
    /** True when [latestRelease] has a known, strictly newer versionCode than what's installed. */
    val hasUpdate: Boolean
        get() {
            val installed = installedVersionCode ?: return false
            val latest = latestRelease?.versionCode ?: return false
            return latest > installed
        }
}
