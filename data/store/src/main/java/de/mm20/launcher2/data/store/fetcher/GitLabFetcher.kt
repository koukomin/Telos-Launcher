package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.ApkPicker
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import de.mm20.launcher2.store.model.ReleaseAsset
import io.ktor.client.HttpClient
import org.json.JSONArray
import java.net.URLEncoder
import java.time.Instant

/** GitLab (gitlab.com or self-hosted): releases API, with links in the release description as a fallback. */
class GitLabFetcher(private val httpClient: HttpClient = HttpClient()) : SourceFetcher<AppSource.GitLab> {

    private val preReleaseTag = Regex("(?i)(alpha|beta|[-.]rc|preview|nightly|dev)")
    private val descriptionLink = Regex("\\(((?:https?://|/uploads/)[^)\\s]+\\.apk)\\)")

    override suspend fun fetchLatestRelease(source: AppSource.GitLab): ReleaseArtifact? =
        runCatching { fetch(source) }.getOrNull()

    suspend fun fetch(source: AppSource.GitLab): ReleaseArtifact {
        val project = URLEncoder.encode(source.path, "UTF-8")
        val answer = httpClient.getText("https://${source.host}/api/v4/projects/$project/releases?per_page=15", accept = "application/json")
        when {
            answer.code == 404 -> throw StoreFetchException("${source.path} was not found on ${source.host}")
            !answer.ok -> throw StoreFetchException("${source.host} answered ${answer.code}")
        }
        val releases = JSONArray(answer.body)
        if (releases.length() == 0) throw StoreFetchException("${source.path} has no releases")
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            val tag = release.optString("tag_name")
            if (!source.includePrereleases && (release.optBoolean("upcoming_release") || preReleaseTag.containsMatchIn(tag))) continue
            val list = ArrayList<ReleaseAsset>()
            release.optJSONObject("assets")?.optJSONArray("links")?.let { links ->
                for (j in 0 until links.length()) {
                    val l = links.getJSONObject(j)
                    val url = l.optString("direct_asset_url").ifBlank { l.optString("url") }
                    list += ReleaseAsset(l.optString("name").ifBlank { url.substringAfterLast('/') }, url)
                }
            }
            val description = release.optString("description")
            for (m in descriptionLink.findAll(description)) {
                val link = m.groupValues[1]
                val url = if (link.startsWith("/")) "https://${source.host}/${source.path}$link" else link
                list += ReleaseAsset(url.substringAfterLast('/'), url)
            }
            val asset = ApkPicker.pick(list, source.assetNameRegex) ?: continue
            return ReleaseArtifact(
                version = tag.removePrefix("v"),
                versionCode = null,
                size = null,
                downloadUrl = asset.url,
                changelog = description.ifBlank { null },
                publishedAt = release.optString("released_at").takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            )
        }
        throw StoreFetchException("No APK found in the latest releases of ${source.path}")
    }
}
