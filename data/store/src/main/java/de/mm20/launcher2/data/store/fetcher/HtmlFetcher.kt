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

    private fun apkLinks(body: String, pageUrl: String, custom: Regex?): List<String> {
        val base = URI.create(pageUrl)
        return href.findAll(body).map { it.groupValues[1].trim().replace("&amp;", "&") }
            .mapNotNull { runCatching { base.resolve(it).toString() }.getOrNull() }
            .filter { link ->
                if (custom != null) custom.containsMatchIn(link) else link.substringBefore('?').endsWith(".apk", ignoreCase = true)
            }
            .distinct().toList()
    }

    /** Sub-folders of the page whose name is a version number, such as `14.5.7/` */
    private fun versionFolders(body: String, pageUrl: String): List<String> {
        val base = URI.create(pageUrl)
        val dir = pageUrl.substringBefore('?').let { if (it.endsWith("/")) it else "$it/" }
        return href.findAll(body).map { it.groupValues[1].trim() }
            .mapNotNull { runCatching { base.resolve(it).toString() }.getOrNull() }
            .filter { it.startsWith(dir) && it != dir && it.endsWith("/") && it.removePrefix(dir).trimEnd('/').let { n -> !n.contains('/') && versionNumbers(n).isNotEmpty() } }
            .distinct().toList()
    }

    override suspend fun fetchLatestRelease(source: AppSource.Html): ReleaseArtifact? =
        runCatching { fetch(source) }.getOrNull()

    suspend fun fetch(source: AppSource.Html): ReleaseArtifact {
        var pageUrl = source.pageUrl
        var answer = httpClient.getText(pageUrl)
        if (!answer.ok) throw StoreFetchException("The page answered ${answer.code}")
        val custom = source.linkRegex?.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it) }.getOrNull() }
        var links = apkLinks(answer.body, pageUrl, custom)
        if (links.isEmpty()) {
            // a download directory with one folder per version (the Tor Project's, for example): look into the newest folder
            val folder = versionFolders(answer.body, pageUrl)
                .maxWithOrNull { a, b -> compareVersionLists(versionNumbers(a), versionNumbers(b)) }
            if (folder != null) {
                pageUrl = folder
                answer = httpClient.getText(pageUrl)
                if (!answer.ok) throw StoreFetchException("The page answered ${answer.code}")
                links = apkLinks(answer.body, pageUrl, custom)
            }
        }
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
