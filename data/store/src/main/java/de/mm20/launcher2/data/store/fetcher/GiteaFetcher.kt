package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.ApkPicker
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import de.mm20.launcher2.store.model.ReleaseAsset
import io.ktor.client.HttpClient
import org.json.JSONArray
import java.time.Instant

/** Codeberg, Forgejo and Gitea servers: their releases API has the same shape as GitHub's. */
class GiteaFetcher(private val httpClient: HttpClient = HttpClient()) : SourceFetcher<AppSource.Gitea> {

    override suspend fun fetchLatestRelease(source: AppSource.Gitea): ReleaseArtifact? =
        runCatching { fetch(source) }.getOrNull()

    suspend fun fetch(source: AppSource.Gitea): ReleaseArtifact {
        val answer = httpClient.getText(
            "https://${source.host}/api/v1/repos/${source.owner}/${source.repo}/releases?limit=15",
            accept = "application/json",
        )
        when {
            answer.code == 404 -> throw StoreFetchException("${source.owner}/${source.repo} was not found on ${source.host}")
            !answer.ok -> throw StoreFetchException("${source.host} answered ${answer.code}")
        }
        val releases = JSONArray(answer.body)
        if (releases.length() == 0) throw StoreFetchException("${source.repo} has no releases")
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            if (release.optBoolean("prerelease") && !source.includePrereleases) continue
            val assets = release.optJSONArray("assets") ?: continue
            val list = (0 until assets.length()).map { assets.getJSONObject(it) }.map {
                ReleaseAsset(it.optString("name"), it.optString("browser_download_url"), it.optLong("size").takeIf { s -> s > 0 })
            }
            val asset = ApkPicker.pick(list, source.assetNameRegex) ?: continue
            return ReleaseArtifact(
                version = release.optString("tag_name").removePrefix("v"),
                versionCode = null,
                size = asset.size,
                downloadUrl = asset.url,
                changelog = release.optString("body").ifBlank { null },
                publishedAt = release.optString("published_at").takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            )
        }
        throw StoreFetchException("No APK found in the latest releases of ${source.owner}/${source.repo}")
    }
}
