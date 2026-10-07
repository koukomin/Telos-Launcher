package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.ApkPicker
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import de.mm20.launcher2.store.model.ReleaseAsset
import io.ktor.client.HttpClient

/** SourceForge: the project's file feed lists the newest files first; the newest APK version wins. */
class SourceForgeFetcher(private val httpClient: HttpClient = HttpClient()) : SourceFetcher<AppSource.SourceForge> {

    private val item = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val title = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>", RegexOption.DOT_MATCHES_ALL)
    private val link = Regex("<link>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</link>", RegexOption.DOT_MATCHES_ALL)

    override suspend fun fetchLatestRelease(source: AppSource.SourceForge): ReleaseArtifact? =
        runCatching { fetch(source) }.getOrNull()

    suspend fun fetch(source: AppSource.SourceForge): ReleaseArtifact {
        val answer = httpClient.getText("https://sourceforge.net/projects/${source.project}/rss?path=/&limit=100")
        when {
            answer.code == 404 -> throw StoreFetchException("Project ${source.project} was not found on SourceForge")
            !answer.ok -> throw StoreFetchException("SourceForge answered ${answer.code}")
        }
        val files = item.findAll(answer.body).mapNotNull { m ->
            val name = title.find(m.groupValues[1])?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val url = link.find(m.groupValues[1])?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            if (!name.endsWith(".apk", ignoreCase = true)) null else ReleaseAsset(name.substringAfterLast('/'), url)
        }.toList()
        if (files.isEmpty()) throw StoreFetchException("No APK files found in ${source.project}")
        val newest = files.maxWithOrNull { a, b -> compareVersionLists(versionNumbers(a.name), versionNumbers(b.name)) }!!
        val sameVersion = files.filter { compareVersionLists(versionNumbers(it.name), versionNumbers(newest.name)) == 0 }
        val asset = ApkPicker.pick(sameVersion, source.assetNameRegex) ?: throw StoreFetchException("No matching APK in ${source.project}")
        return ReleaseArtifact(
            version = versionNumbers(asset.name).joinToString(".").ifBlank { "unknown" },
            versionCode = null,
            downloadUrl = asset.url,
        )
    }
}
