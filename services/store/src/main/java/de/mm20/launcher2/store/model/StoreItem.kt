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
    /** `versionName` of the installed app, read live from the package manager */
    val installedVersionName: String? = null,
    val latestRelease: ReleaseArtifact? = null,
    /** Epoch millis of the last successful [latestRelease] refresh. */
    val lastCheckedAt: Long? = null,
) {
    /**
     * True when the latest release is newer than what is installed: by `versionCode` when the source
     * knows it, otherwise by comparing the version numbers in the version names (a GitHub tag such
     * as "v1.4.2" against "1.4.2"), the way Obtainium does.
     */
    val hasUpdate: Boolean
        get() {
            val installed = installedVersionCode ?: return false
            val release = latestRelease ?: return false
            release.versionCode?.let { return it > installed }
            val name = installedVersionName ?: return false
            return VersionCompare.isNewer(release.version, name)
        }
}

/** Compares version names such as "v1.4.2", "release-2.0" or "2024.03.1" by their numbers. */
object VersionCompare {
    private val dotted = Regex("\\d+(?:\\.\\d+)+")
    private val plain = Regex("\\d+")

    fun numbers(version: String): List<Long>? {
        val m = dotted.find(version) ?: plain.find(version) ?: return null
        return m.value.split('.').mapNotNull { it.toLongOrNull() }
    }

    /** True when [candidate] is newer than [installed]; unknown versions never count as an update. */
    fun isNewer(candidate: String, installed: String): Boolean {
        if (candidate.isBlank() || candidate == "unknown") return false
        val a = numbers(candidate)
        val b = numbers(installed)
        if (a == null || b == null) return candidate.trim().lowercase().removePrefix("v") != installed.trim().lowercase().removePrefix("v")
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0L }
            val y = b.getOrElse(i) { 0L }
            if (x != y) return x > y
        }
        return false
    }
}
