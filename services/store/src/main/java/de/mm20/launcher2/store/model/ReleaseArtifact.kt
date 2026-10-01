package de.mm20.launcher2.store.model

import kotlinx.serialization.Serializable

/**
 * A single downloadable release of an app, as resolved by a
 * [de.mm20.launcher2.store.fetcher.SourceFetcher] (or constructed directly for sources that are
 * already a fixed download, like [AppSource.DirectApk]).
 */
@Serializable
data class ReleaseArtifact(
    /** Human-readable version string, e.g. "2.4.1". Not necessarily comparable/sortable. */
    val version: String,
    /**
     * The APK's `versionCode`, used to decide whether this release is newer than what's
     * installed. Null when the source can't tell us without downloading the APK itself (e.g. a
     * GitHub release whose tag doesn't encode it).
     */
    val versionCode: Long?,
    /** Size in bytes, if known up front (e.g. from a GitHub release asset's `size` field). */
    val size: Long? = null,
    val downloadUrl: String,
    val changelog: String? = null,
    /** When this release was published, if the source reports it. Epoch millis. */
    val publishedAt: Long? = null,
)
