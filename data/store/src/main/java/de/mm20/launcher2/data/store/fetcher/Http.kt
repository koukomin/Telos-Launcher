package de.mm20.launcher2.data.store.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel

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
        return prepareGet {
            url(url)
            header("User-Agent", "Telos Store")
            accept?.let { header("Accept", it) }
            bearer?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }.execute { response ->
            val limit = 4 * 1024 * 1024
            if ((response.headers["Content-Length"]?.toLongOrNull() ?: 0) > limit)
                throw StoreFetchException("The source response is too large")
            val channel = response.bodyAsChannel()
            val output = java.io.ByteArrayOutputStream()
            try {
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (output.size() + read > limit) throw StoreFetchException("The source response is too large")
                    output.write(buffer, 0, read)
                }
                HttpText(response.status.value, output.toString(Charsets.UTF_8.name()))
            } finally { channel.cancel() }
        }
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
