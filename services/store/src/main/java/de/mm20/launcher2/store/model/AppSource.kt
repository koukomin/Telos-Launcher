package de.mm20.launcher2.store.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a [de.mm20.launcher2.store.model.StoreItem]'s releases come from, and how to resolve
 * them. Each variant maps to exactly one [de.mm20.launcher2.store.fetcher.SourceFetcher]
 * implementation (except [DirectApk] and [AffiliateDirect], which are already a resolved
 * download and need no fetcher).
 */
@Serializable
sealed interface AppSource {

    /**
     * Polls the GitHub Releases API (`/repos/{owner}/{repo}/releases/latest`) for the newest
     * release and picks its first asset whose name matches [assetNameRegex] (any `.apk` asset by
     * default).
     */
    @Serializable
    @SerialName("github")
    data class GitHub(
        val owner: String,
        val repo: String,
        val assetNameRegex: String? = null,
        /** Include releases marked as pre-release/draft when picking the latest one. */
        val includePrereleases: Boolean = false,
    ) : AppSource

    /** An app from an F-Droid-compatible repository index (`index-v2.json`). */
    @Serializable
    @SerialName("fdroid")
    data class FDroid(
        val packageName: String,
        val repoUrl: String = "https://f-droid.org/repo",
    ) : AppSource

    /** A fixed APK download URL with no version-checking - every install re-downloads it. */
    @Serializable
    @SerialName("direct_apk")
    data class DirectApk(
        val downloadUrl: String,
    ) : AppSource

    /**
     * A listing on the Play Store that the user installs themselves via the Play Store app -
     * Telos never downloads or installs the APK itself, it only launches the Play Store with
     * [referrer] attached so the developer/launcher gets install attribution.
     */
    @Serializable
    @SerialName("affiliate_play_store")
    data class AffiliatePlayStore(
        val packageName: String,
        /** UTM-style referrer string, e.g. "utm_source=telos_store&utm_medium=launcher". */
        val referrer: String,
    ) : AppSource

    /**
     * A direct APK download that is monetized/tracked through [campaignId] (e.g. appended as a
     * query parameter or header by the downloader), as opposed to [DirectApk] which is not.
     */
    @Serializable
    @SerialName("affiliate_direct")
    data class AffiliateDirect(
        val downloadUrl: String,
        val campaignId: String,
    ) : AppSource

    /** GitLab (gitlab.com or a self-hosted server), via the releases API. [path] is "owner/repo". */
    @Serializable
    @SerialName("gitlab")
    data class GitLab(
        val host: String = "gitlab.com",
        val path: String,
        val assetNameRegex: String? = null,
        val includePrereleases: Boolean = false,
    ) : AppSource

    /** Codeberg, Forgejo, Gitea and compatible servers, via the Gitea releases API. */
    @Serializable
    @SerialName("gitea")
    data class Gitea(
        val host: String = "codeberg.org",
        val owner: String,
        val repo: String,
        val assetNameRegex: String? = null,
        val includePrereleases: Boolean = false,
    ) : AppSource

    /** A SourceForge project: the newest .apk in its file list. */
    @Serializable
    @SerialName("sourceforge")
    data class SourceForge(
        val project: String,
        val assetNameRegex: String? = null,
    ) : AppSource

    /**
     * Any web page that links to APK files (Obtainium's "HTML" source): the page is searched for
     * links to .apk files, or for links matching [linkRegex], and the one with the highest version wins.
     */
    @Serializable
    @SerialName("html")
    data class Html(
        val pageUrl: String,
        val linkRegex: String? = null,
    ) : AppSource
}
