package de.mm20.launcher2.data.store.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText

/** A source could not give a release; [message] says why in words for the user. */
class StoreFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class HttpText(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/**
 * Plain GET that returns the status and the text. The answers of the sources are read with
 * org.json on purpose: asking Ktor to decode generic lists (List<...>) failed on devices
 * ("Serializer for class 'List' is not found").
 */
internal suspend fun HttpClient.getText(
    url: String,
    accept: String? = null,
    bearer: String? = null,
): HttpText {
    try {
        val response = get {
            url(url)
            header("User-Agent", "Telos Store")
            accept?.let { header("Accept", it) }
            bearer?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }
        return HttpText(response.status.value, response.bodyAsText())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw StoreFetchException("Could not reach ${url.substringAfter("://").substringBefore('/')}: ${e.message ?: e.javaClass.simpleName}", e)
    }
}

/** First number group of a file or version name, as a list, for comparing releases */
internal fun versionNumbers(text: String): List<Long> =
    de.mm20.launcher2.store.model.VersionCompare.numbers(text) ?: emptyList()

internal fun compareVersionLists(a: List<Long>, b: List<Long>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0L }
        val y = b.getOrElse(i) { 0L }
        if (x != y) return x.compareTo(y)
    }
    return 0
}
