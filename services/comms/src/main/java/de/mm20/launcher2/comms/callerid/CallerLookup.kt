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
class CallerLookup(private val settings: CommsSettings) {
    private val client = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun lookup(number: String): CallerLookupResult? {
        if (!settings.onlineCallerLookup.first()) return null
        val canonical = CallerLookupParser.greekNumber(number) ?: return null
        val url = "https://www.white-pages.gr/arithmos/$canonical"
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Telos/CallerLookup").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful || it.body == null) return@use null
                            val body = it.body!!
                            if ((body.contentLength()) > 1_000_000L) return@use null
                            val input = body.byteStream()
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 1_000_000) return@use null
                                output.write(buffer, 0, count)
                            }
                            CallerLookupParser.parse(canonical, output.toString("UTF-8"), url)
                        }
                    }.getOrNull()
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }
}

/** Only exact Greek numbers and aggregate statistics on that number's own page are accepted. */
object CallerLookupParser {
    fun greekNumber(raw: String): String? {
        if (raw.any { !(it.isDigit() || it in "+ -().") }) return null
        val digits = raw.filter(Char::isDigit)
        val local = when {
            raw.trim().startsWith("+30") && digits.length == 12 -> digits.drop(2)
            digits.startsWith("0030") && digits.length == 14 -> digits.drop(4)
            !raw.trim().startsWith('+') && digits.length == 10 -> digits
            else -> return null
        }
        return local.takeIf { it.length == 10 && (it.startsWith('2') || it.startsWith('6')) }
    }

    fun parse(number: String, html: String, sourceUrl: String): CallerLookupResult? {
        val document = Jsoup.parse(html)
        val heading = document.selectFirst("h1")?.text() ?: return null
        if (!heading.startsWith("Αριθμός τηλεφώνου:") || greekNumber(heading.substringAfter(':')) != number) return null
        // Exclude free-form comments and recommended articles from every decision.
        val text = document.body().text().substringBefore("Προσθήκη ενός σχολίου")
        val risk = Regex("Βαθμός του κινδύνου:\\s*(\\d{1,3})\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val ratings = Regex("Αριθμός αξιολογήσεων:\\s*(\\d+)\\s*×").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val name = document.selectFirst("h3")?.text()?.takeIf {
            it.isNotBlank() && it.length <= 100 && text.contains("Αυτός ο τίτλος γράφτηκε από τον χρήστη") &&
                !it.contains("Ενδιαφέρον") && !it.contains("άγνωστος", ignoreCase = true)
        }
        val spam = risk != null && risk in 70..100 && ratings != null && ratings >= 3
        return CallerLookupResult(name, spam, sourceUrl)
    }
}
