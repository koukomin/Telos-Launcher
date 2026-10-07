package de.mm20.launcher2.data.store.fetcher

import de.mm20.launcher2.store.fetcher.SourceFetcher
import de.mm20.launcher2.store.model.ApkPicker
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.ReleaseArtifact
import de.mm20.launcher2.store.model.ReleaseAsset
import io.ktor.client.HttpClient
import java.net.URI

/**
 * Obtainium's "HTML" source: reads a web page, finds the links to APK files (or the links that match
 * the app's filter) and takes the one with the highest version number in its file name.
 * A page whose APK files have no version in their names never shows updates, but can still be installed.
 */
class HtmlFetcher(private val httpClient: HttpClient = HttpClient()) : SourceFetcher<AppSource.Html> {

    private val href = Regex("href\\s*=\\s*[\"']([^\"'#]+)[\"']", RegexOption.IGNORE_CASE)

    override suspend fun fetchLatestRelease(source: AppSource.Html): ReleaseArtifact? =
        runCatching { fetch(source) }.getOrNull()

    suspend fun fetch(source: AppSource.Html): ReleaseArtifact {
        val answer = httpClient.getText(source.pageUrl)
        if (!answer.ok) throw StoreFetchException("The page answered ${answer.code}")
        val base = URI.create(source.pageUrl)
        val custom = source.linkRegex?.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
        val links = href.findAll(answer.body).map { it.groupValues[1].trim().replace("&amp;", "&") }
            .mapNotNull { runCatching { base.resolve(it).toString() }.getOrNull() }
            .filter { link ->
                if (custom != null) custom.containsMatchIn(link) else link.substringBefore('?').endsWith(".apk", ignoreCase = true)
            }
            .distinct().toList()
        if (links.isEmpty()) throw StoreFetchException("No APK links found on this page")
        val assets = links.map { ReleaseAsset(it.substringBefore('?').substringAfterLast('/').ifBlank { "app.apk" }.let { n -> if (n.endsWith(".apk", true)) n else "$n.apk" }, it) }
        val newest = assets.maxWithOrNull { a, b -> compareVersionLists(versionNumbers(a.name), versionNumbers(b.name)) }!!
        val sameVersion = assets.filter { compareVersionLists(versionNumbers(it.name), versionNumbers(newest.name)) == 0 }
        val asset = ApkPicker.pick(sameVersion, null) ?: sameVersion.first()
        return ReleaseArtifact(
            version = versionNumbers(asset.name).takeIf { it.isNotEmpty() }?.joinToString(".") ?: "unknown",
            versionCode = null,
            downloadUrl = asset.url,
        )
    }
}
