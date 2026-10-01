package de.mm20.launcher2.data.store.fetcher

import android.util.Log
import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Resolves the newest [AppSource.FDroid] release via f-droid.org's per-package REST endpoint
 * (`/api/v1/packages/<packageName>`), rather than downloading the full repo index
 * (`index-v1.json`/`index-v2.json`, tens of MB) just to look up one package.
 *
 * This endpoint is specific to f-droid.org's own site infrastructure, not part of the generic
 * F-Droid repo format every F-Droid-compatible repo serves - so [AppSource.FDroid.repoUrl] is
 * only used here to build the APK download URL (every F-Droid-compatible repo, official or a
 * mirror, serves APKs at `<repoUrl>/<apkName>`), not to choose where metadata comes from. A repo
 * that isn't f-droid.org itself (a private/custom repo) would need the full index-file approach
 * instead - not implemented here.
 */
class FDroidFetcher(
    private val httpClient: HttpClient = HttpClient(),
) : SourceFetcher<AppSource.FDroid> {

    override suspend fun fetchLatestRelease(source: AppSource.FDroid): ReleaseArtifact? {
        return try {
            val response = httpClient.get {
                url("https://f-droid.org/api/v1/packages/${source.packageName}")
            }
            if (!response.status.isSuccess()) {
                Log.w(TAG, "F-Droid lookup for ${source.packageName} failed: ${response.status}")
                return null
            }

            val body = response.body<FDroidPackageResponse>()
            // packages[] is newest-first in the real API response, but don't rely on response
            // ordering - pick the highest versionCode explicitly.
            val latest = body.packages.maxByOrNull { it.versionCode } ?: run {
                Log.w(TAG, "No packages listed for ${source.packageName} on F-Droid")
                return null
            }

            ReleaseArtifact(
                version = latest.versionName,
                versionCode = latest.versionCode,
                size = latest.size,
                downloadUrl = "${source.repoUrl.trimEnd('/')}/${latest.apkName}",
                // The per-package API doesn't reliably include release notes/changelog text -
                // left null rather than fabricated.
                changelog = null,
                publishedAt = latest.added,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest release for ${source.packageName} from F-Droid", e)
            null
        }
    }

    @Serializable
    private data class FDroidPackageResponse(
        val packageName: String,
        val packages: List<FDroidPackage> = emptyList(),
    )

    @Serializable
    private data class FDroidPackage(
        val versionName: String,
        val versionCode: Long,
        val apkName: String,
        val size: Long? = null,
        val added: Long? = null,
    )

    companion object {
        private const val TAG = "FDroidFetcher"
    }
}
