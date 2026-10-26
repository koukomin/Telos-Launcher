package de.mm20.launcher2.comms.callerid

import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class CallerLookupResult(val suggestedName: String?, val likelySpam: Boolean, val sourceUrl: String)

/** Optional, best-effort community data. Never participates in the five-second call-screening decision. */
class CallerLookup internal constructor(
    private val enabled: kotlinx.coroutines.flow.Flow<Boolean>,
    private val client: OkHttpClient,
) {
    constructor(settings: CommsSettings) : this(settings.onlineCallerLookup,
        OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build())

    private data class Cached(val result: CallerLookupResult?, val expires: Long)
    private val cache = LinkedHashMap<String, Cached>()
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val _results = kotlinx.coroutines.flow.MutableStateFlow<Map<String, CallerLookupResult>>(emptyMap())
    val results: kotlinx.coroutines.flow.StateFlow<Map<String, CallerLookupResult>> = _results

    /** Only callers/details explicitly opened by the user; never batch-upload the address book/history. */
    suspend fun lookup(number: String): CallerLookupResult? {
        if (!enabled.first()) return null
        val canonical = CallerLookupParser.greekNumber(number) ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mutex.lock()
        try {
            val now = System.nanoTime() / 1_000_000
            cache[canonical]?.takeIf { it.expires > now }?.let { return@withContext it.result }
            val url = "https://www.white-pages.gr/arithmos/$canonical"
            val response = fetch(url) ?: return@withContext null
            val result = try {
                response.use outer@{
                    // Follow at most one same-origin canonical redirect; do not disclose a number to another host.
                    val final = if (it.code in 301..308) {
                        val target = it.header("Location")?.let(it.request.url::resolve)
                        if (target == null || target.scheme != "https" || target.host != "www.white-pages.gr" ||
                            target.encodedPath.trimEnd('/') != "/arithmos/$canonical") return@outer null
                        fetch(target.toString()) ?: return@outer null
                    } else it
                    final.use inner@{ page ->
                        if (!page.isSuccessful || page.body == null) return@inner null
                        val body = page.body!!
                        if (body.contentLength() > 1_000_000L) return@inner null
                        val input = body.byteStream()
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 1_000_000) return@inner null
                            output.write(buffer, 0, count)
                        }
                        CallerLookupParser.parse(canonical, output.toString("UTF-8"), page.request.url.toString())
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (_: IOException) { null }
              catch (_: IllegalArgumentException) { null }
            cache[canonical] = Cached(result, now + if (result == null) 60_000 else 6 * 60 * 60 * 1000L)
            while (cache.size > 128) cache.remove(cache.keys.first())
            _results.value = cache.mapNotNull { (key, value) ->
                value.result?.takeIf { value.expires > now }?.let { key to it }
            }.toMap()
            result
        } finally { mutex.unlock() }
        }
    }

    private suspend fun fetch(url: String): Response? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Telos/CallerLookup").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}

/** Only exact Greek numbers and aggregate statistics on that number's own page are accepted. */
object CallerLookupParser {
    private val riskPattern = Regex("Βαθμός του κινδύνου:\\s*(\\d{1,3})\\s*%")
    private val ratingsPattern = Regex("Αριθμός αξιολογήσεων:\\s*(\\d+)\\s*×")
    fun greekNumber(raw: String): String? {
        val key = de.mm20.launcher2.ktx.PhoneNumberKey.of(raw)
        if (!key.startsWith("+30") || key.length != 13) return null
        return key.drop(3).takeIf { it.startsWith('2') || it.startsWith('6') }
    }

    fun parse(number: String, html: String, sourceUrl: String): CallerLookupResult? {
        val document = Jsoup.parse(html)
        val heading = document.selectFirst("h1")?.text() ?: return null
        if (!heading.startsWith("Αριθμός τηλεφώνου:") || greekNumber(heading.substringAfter(':')) != number) return null
        // Exclude free-form comments and recommended articles from every decision.
        val text = document.body().text().substringBefore("Σχόλια για τον αριθμό").substringBefore("Προσθήκη ενός σχολίου")
        val risk = riskPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val ratings = ratingsPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val name = document.selectFirst("h3")?.text()?.takeIf {
            it.isNotBlank() && it.length <= 100 && text.contains("Αυτός ο τίτλος γράφτηκε από τον χρήστη") &&
                !it.contains("Ενδιαφέρον") && !it.contains("άγνωστος", ignoreCase = true)
        }
        val spam = risk != null && risk in 70..100 && ratings != null && ratings >= 3
        return CallerLookupResult(name, spam, sourceUrl)
    }
}
