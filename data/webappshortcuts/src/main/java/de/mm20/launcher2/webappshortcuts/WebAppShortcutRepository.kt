package de.mm20.launcher2.webappshortcuts

import android.webkit.URLUtil
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.searchable.SavableSearchableRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.io.UncheckedIOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.UUID

interface WebAppShortcutRepository : SearchableRepository<WebAppShortcut> {
    /**
     * Best-effort favicon/manifest icon lookup for [url], used to pre-fill a suggested icon
     * when the user creates a new web app shortcut. Returns null if none could be found or on
     * any network/parsing error.
     */
    suspend fun findFavicon(url: String): String?

    fun create(
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String? = null,
        showInGrid: Boolean = true,
        showInPanel: Boolean = false,
        order: Int = 0,
        iconSource: WebAppShortcut.IconSource = WebAppShortcut.IconSource.Website,
    ): WebAppShortcut

    fun update(
        shortcut: WebAppShortcut,
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String? = null,
        showInGrid: Boolean = true,
        showInPanel: Boolean = false,
        order: Int = 0,
        iconSource: WebAppShortcut.IconSource = WebAppShortcut.IconSource.Website,
    ): WebAppShortcut

    fun delete(shortcut: WebAppShortcut)
}

internal class WebAppShortcutRepositoryImpl(
    private val savableSearchableRepository: SavableSearchableRepository,
) : WebAppShortcutRepository {

    private val httpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 200
                requestTimeoutMillis = 3000
                socketTimeoutMillis = 1000
            }
        }
    }

    override fun search(query: String, allowNetwork: Boolean): Flow<List<WebAppShortcut>> {
        return savableSearchableRepository.get(includeTypes = listOf(WebAppShortcutImpl.Domain))
            .map { items ->
                items.filterIsInstance<WebAppShortcut>()
                    .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            }
    }

    override fun create(
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String?,
        showInGrid: Boolean,
        showInPanel: Boolean,
        order: Int,
        iconSource: WebAppShortcut.IconSource,
    ): WebAppShortcut {
        val shortcut = WebAppShortcutImpl(
            id = UUID.randomUUID().toString(),
            label = label,
            url = normalizeUrl(url),
            iconUri = iconUri,
            faviconUrl = faviconUrl,
            color = null,
            rendererPackage = rendererPackage,
            showInGrid = showInGrid,
            showInPanel = showInPanel,
            order = order,
            iconSource = iconSource,
        )
        savableSearchableRepository.insert(shortcut)
        return shortcut
    }

    override fun update(
        shortcut: WebAppShortcut,
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String?,
        showInGrid: Boolean,
        showInPanel: Boolean,
        order: Int,
        iconSource: WebAppShortcut.IconSource,
    ): WebAppShortcut {
        shortcut as WebAppShortcutImpl
        val updated = shortcut.copy(
            label = label,
            url = normalizeUrl(url),
            iconUri = iconUri,
            faviconUrl = faviconUrl,
            rendererPackage = rendererPackage,
            showInGrid = showInGrid,
            showInPanel = showInPanel,
            order = order,
            iconSource = iconSource,
        )
        savableSearchableRepository.update(updated)
        return updated
    }

    override fun delete(shortcut: WebAppShortcut) {
        savableSearchableRepository.delete(shortcut)
    }

    private fun normalizeUrl(url: String): String {
        return if (!url.startsWith("https://") && !url.startsWith("http://")) "https://$url"
        else url
    }

    override suspend fun findFavicon(url: String): String? {
        return withContext(Dispatchers.IO) {
            var normalizedUrl = url
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                normalizedUrl = "https://$url"
            }
            if (!URLUtil.isValidUrl(normalizedUrl)) return@withContext null
            try {
                val response = httpClient.get {
                    url(normalizedUrl)
                }
                val body = response.bodyAsText()
                val doc = Jsoup.parse(body)
                var favicon = doc.select("link[rel=apple-touch-icon]").attr("href")
                if (favicon.isBlank()) favicon =
                    doc.head().select("meta[itemprop=image]").attr("content")
                if (favicon.isBlank()) favicon = doc.select("link[rel=icon]").attr("href")
                if (favicon.isBlank()) favicon =
                    doc.head().select("link[href~=.*\\.(ico|png)]").attr("href")
                if (favicon.isNotBlank()) favicon = resolveUrl(response.request.url, favicon)
                favicon.takeIf { it.isNotBlank() }
            } catch (e: IOException) {
                null
            } catch (e: UncheckedIOException) {
                null
            } catch (e: URISyntaxException) {
                null
            } catch (e: RuntimeException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    private fun resolveUrl(url: Url, link: String): String {
        return try {
            URL(URL(url.toString()), link).toString()
        } catch (e: MalformedURLException) {
            ""
        }
    }
}
