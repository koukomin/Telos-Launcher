package de.mm20.launcher2.data.store.icons

import de.mm20.launcher2.data.store.fetcher.getText
import de.mm20.launcher2.store.icons.StoreIconResolver
import de.mm20.launcher2.store.model.AppSource
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class StoreIconResolverImpl(private val http: HttpClient) : StoreIconResolver {
    private data class Entry(val expires: Long, val urls: List<String>)
    private val cache = LinkedHashMap<String, Entry>()
    private val lock = Mutex()
    private val network = Semaphore(4)

    override suspend fun candidates(source: AppSource, packageName: String): List<String> {
        val key = "$source:$packageName"
        lock.withLock { cache[key]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return it.urls } }
        val resolved = network.withPermit {
            when (source) {
                is AppSource.GitHub -> repository("https://raw.githubusercontent.com/${source.owner}/${source.repo}/HEAD/")
                is AppSource.GitLab -> repository("https://${source.host}/${source.path}/-/raw/HEAD/")
                is AppSource.Gitea -> repository("https://${source.host}/${source.owner}/${source.repo}/raw/branch/main/", "https://${source.host}/${source.owner}/${source.repo}/raw/branch/master/")
                is AppSource.FDroid -> {
                    val host = runCatching { java.net.URI(source.repoUrl).host }.getOrNull()
                    val page = when (host) {
                        "f-droid.org" -> "https://f-droid.org/en/packages/${source.packageName}/"
                        "apt.izzysoft.de" -> "https://apt.izzysoft.de/fdroid/index/apk/${source.packageName}"
                        else -> source.repoUrl.trimEnd('/') + "/"
                    }
                    (publishedIcons(text(page), page) + listOf("${source.repoUrl.trimEnd('/')}/icons/${source.packageName}.png")).distinct()
                }
                is AppSource.AffiliatePlayStore -> {
                    val page = "https://play.google.com/store/apps/details?id=${source.packageName}&hl=en"
                    publishedIcons(text(page), page, allowOpenGraph = true)
                }
                is AppSource.SourceForge -> {
                    val page = "https://sourceforge.net/projects/${source.project}/"
                    publishedIcons(text(page), page)
                }
                is AppSource.Html -> publishedIcons(text(source.pageUrl), source.pageUrl)
                else -> emptyList()
            }
        }
        val urls = resolved.mapNotNull { imageUrl(it, it) }.distinct()
        lock.withLock {
            cache[key] = Entry(System.currentTimeMillis() + 15 * 60_000L, urls)
            while (cache.size > 256) cache.remove(cache.keys.first())
        }
        return urls
    }

    private suspend fun repository(base: String, fallback: String? = null): List<String> {
        var readmeUrl = base + "README.md"
        var readme = text(readmeUrl)
        if (readme.isBlank() && fallback != null) { readmeUrl = fallback + "README.md"; readme = text(readmeUrl) }
        val discovered = publishedIcons(readme, readmeUrl)
        val conventional = listOf("fastlane/metadata/android/en-US/images/icon.png", "metadata/en-US/images/icon.png",
            "app/src/main/ic_launcher-playstore.png", "assets/icon.png", "logo.png", "icon.png",
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
        return (discovered + conventional.map { base + it } + fallback?.let { other -> conventional.take(2).map { other + it } }.orEmpty()).distinct().take(10)
    }

    private suspend fun text(url: String): String = try {
        // A public artwork lookup never receives the user's GitHub API token.
        http.getText(url, maxBytes = 256 * 1024).let { if (it.ok) it.body else "" }
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { "" }
}
