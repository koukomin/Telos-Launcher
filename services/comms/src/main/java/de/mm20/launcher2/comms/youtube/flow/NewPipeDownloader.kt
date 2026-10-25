package de.mm20.launcher2.comms.youtube.flow

import android.content.Context
import de.mm20.launcher2.comms.youtube.YouTubeAccountManager
import de.mm20.launcher2.comms.youtube.YouTubeSessionPolicy
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class NewPipeDownloader(private val context: Context) : Downloader() {
    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val safe = if (YouTubeSessionPolicy.trusted(request.url.toString())) request else request.newBuilder()
                .removeHeader("Cookie").removeHeader("Authorization").removeHeader("Origin").removeHeader("X-Origin").build()
            chain.proceed(safe)
        }
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val url = request.url()
        val builder = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT)
        for ((key, values) in request.headers()) {
            builder.removeHeader(key)
            for (value in values) builder.addHeader(key, value)
        }

        if (YouTubeSessionPolicy.trusted(url)) {
            YouTubeAccountManager.getCookie(context)?.let { cookie ->
                if (cookie.isNotBlank()) {
                    builder.header("Cookie", YouTubeSessionPolicy.mergeCookies(builder.build().header("Cookie"), cookie))

                    val origin = if (java.net.URI(url).host.equals("music.youtube.com", true)) {
                        "https://" + "music.youtube.com"
                    } else {
                        "https://" + "www.youtube.com"
                    }

                    extractSapisid(cookie)?.let { sapisid ->
                        val sapisidHash = generateSapisidHash(sapisid, origin)
                        builder.header("Authorization", "SAPISIDHASH $sapisidHash")
                        builder.header("X-Origin", origin)
                        builder.header("Origin", origin)
                    }
                }
            }
        }

        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null
        builder.method(method, body)

        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
            val text = response.body?.let { responseBody ->
                if (responseBody.contentLength() > MAX_TEXT_BYTES) throw IOException("Extraction response too large")
                val source = responseBody.source()
                val buffer = okio.Buffer()
                var count = 0L
                while (count <= MAX_TEXT_BYTES) {
                    val read = source.read(buffer, minOf(64 * 1024L, MAX_TEXT_BYTES + 1 - count))
                    if (read < 0) break
                    count += read
                }
                if (count > MAX_TEXT_BYTES) throw IOException("Extraction response too large")
                buffer.readString(responseBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
            }.orEmpty()
            Response(response.code, response.message, response.headers.toMultimap(), text, response.request.url.toString())
        }
    }

    private fun extractSapisid(cookieHeader: String): String? {
        return YouTubeSessionPolicy.sapisid(cookieHeader)
    }

    private fun generateSapisidHash(sapisid: String, origin: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val sha1Hex = digest.joinToString("") { "%02x".format(it) }
        return "${timestamp}_$sha1Hex"
    }

    private companion object {
        const val MAX_TEXT_BYTES = 8 * 1024 * 1024L
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}