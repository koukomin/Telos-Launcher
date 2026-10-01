package de.mm20.launcher2.data.store.fetcher

import android.util.Log
import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Resolves the newest [AppSource.GitHub] release via the public GitHub REST API. Unauthenticated
 * requests are rate-limited to 60/hour per IP; that's acceptable for `StoreUpdateWorker`'s
 * periodic background checks, but isn't meant for synchronous high-frequency polling.
 */
class GitHubFetcher(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) : SourceFetcher<AppSource.GitHub> {

    override suspend fun fetchLatestRelease(source: AppSource.GitHub): ReleaseArtifact? {
        return try {
            val release = if (source.includePrereleases) {
                fetchNewestOfAllReleases(source)
            } else {
                fetchLatestStableRelease(source)
            } ?: return null

            val asset = pickAsset(release.assets, source.assetNameRegex) ?: run {
                Log.w(TAG, "No matching .apk asset in ${source.owner}/${source.repo}#${release.tagName}")
                return null
            }

            ReleaseArtifact(
                version = release.tagName.removePrefix("v"),
                // GitHub release tags don't reliably encode an Android versionCode; callers that
                // need one must parse it out of the tag/changelog themselves, or treat this
                // release as always-newer (any non-null download counts as "available").
                versionCode = null,
                size = asset.size,
                downloadUrl = asset.downloadUrl,
                changelog = release.body,
                publishedAt = release.publishedAt?.let { parseInstantMillis(it) },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest release for ${source.owner}/${source.repo}", e)
            null
        }
    }

    private suspend fun fetchLatestStableRelease(source: AppSource.GitHub): GitHubRelease? {
        val response = httpClient.get {
            url("https://api.github.com/repos/${source.owner}/${source.repo}/releases/latest")
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null
        return response.body<GitHubRelease>()
    }

    /** Includes drafts/pre-releases, picking the first entry since the API already sorts by date. */
    private suspend fun fetchNewestOfAllReleases(source: AppSource.GitHub): GitHubRelease? {
        val response = httpClient.get {
            url("https://api.github.com/repos/${source.owner}/${source.repo}/releases")
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return null
        return response.body<List<GitHubRelease>>().firstOrNull()
    }

    private fun pickAsset(assets: List<GitHubAsset>, nameRegex: String?): GitHubAsset? {
        val candidates = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (nameRegex == null) return candidates.firstOrNull()
        val regex = Regex(nameRegex)
        return candidates.firstOrNull { regex.containsMatchIn(it.name) } ?: candidates.firstOrNull()
    }

    private fun parseInstantMillis(iso8601: String): Long? = try {
        Instant.parse(iso8601).toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long? = null,
    )

    companion object {
        private const val TAG = "GitHubFetcher"
    }
}
