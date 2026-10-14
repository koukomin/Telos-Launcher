// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2025-2026 A-EDev (Flow). Adapted for Telos Launcher.
// Source: Flow-Tube/Flow @ 4c93b64790c3d89eed57ba96abc997bb955c05f2
package de.mm20.launcher2.comms.youtube.flow

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Flow's OkHttp adapter, with bounded extraction responses to avoid buffering media files. */
internal class NewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
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
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null
        builder.method(method, body)
        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
            val text = response.body?.let { body ->
                if (body.contentLength() > MAX_TEXT_BYTES) throw IOException("Extraction response too large")
                // Reading only N+1 bytes bounds memory even with missing or misleading Content-Length.
                val source = body.source()
                val buffer = okio.Buffer()
                var count = 0L
                while (count <= MAX_TEXT_BYTES) {
                    val read = source.read(buffer, minOf(64 * 1024L, MAX_TEXT_BYTES + 1 - count))
                    if (read < 0) break
                    count += read
                }
                if (count > MAX_TEXT_BYTES) throw IOException("Extraction response too large")
                buffer.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
            }.orEmpty()
            Response(response.code, response.message, response.headers.toMultimap(), text, response.request.url.toString())
        }
    }
    private companion object {
        const val MAX_TEXT_BYTES = 8 * 1024 * 1024L
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
