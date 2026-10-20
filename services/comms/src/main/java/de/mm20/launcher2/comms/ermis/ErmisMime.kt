package de.mm20.launcher2.comms.ermis

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import javax.activation.DataSource
import javax.mail.internet.MimeMultipart
import javax.mail.Part
import javax.mail.internet.ContentType
import org.jsoup.Jsoup

/** Both display and download use this exact traversal, so an attachment index is stable. */
internal object ErmisMime {
    data class Content(val body: String, val attachments: List<Part>, val html: String = "")
    private const val MAX_RAW_PARSE_BYTES = 24_000_000
    fun read(message: Part, rawParseLimit: Int = MAX_RAW_PARSE_BYTES): Content {
        require(rawParseLimit in 1..MAX_RAW_PARSE_BYTES)
        val budget = RawBudget(rawParseLimit)
        val text = StringBuilder()
        val attachments = mutableListOf<Part>()
        val html = StringBuilder()
        var htmlTooLarge = false
        var visited = 0
        var bodyBytes = 0
        fun walk(part: Part, depth: Int, captureText: Boolean = true) {
            require(depth < 20 && ++visited <= 512) { "MIME complexity limit" }
            if (part.fileName != null || Part.ATTACHMENT.equals(part.disposition, true)) {
                attachments += part
            } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                val bytes = part.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bodyBytes += count
                        require(out.size() + count <= 1_000_000 && bodyBytes <= 2_000_000) { "Mail body limit" }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                val charset = runCatching { ContentType(part.contentType).getParameter("charset")?.let(Charset::forName) }.getOrNull() ?: Charsets.UTF_8
                val body = String(bytes, charset)
                val isHtml = part.isMimeType("text/html")
                if (isHtml && !htmlTooLarge) {
                    if (html.length + body.length <= ErmisHtml.MAX_HTML) html.append(body)
                    else { html.setLength(0); htmlTooLarge = true }
                }
                if (captureText) {
                    val plain = if (isHtml) Jsoup.parse(body).text() else body
                    if (text.isNotEmpty() && text.length < 1_000_000) text.append('\n')
                    text.append(plain.take((1_000_000 - text.length).coerceAtLeast(0)))
                }
            } else if (part.isMimeType("multipart/*")) {
                // Do not use part.content: JavaMail could allocate an arbitrarily large
                // multipart before we can inspect its count. Bound the parser's source itself.
                val multi = MimeMultipart(object : DataSource {
                    override fun getInputStream(): InputStream = budget.wrap(part.inputStream)
                    override fun getOutputStream(): java.io.OutputStream = throw IOException("Read-only MIME source")
                    override fun getContentType(): String = part.contentType
                    override fun getName(): String = "Ermis MIME source"
                })
                require(multi.count <= 100) { "MIME part limit" }
                if (part.isMimeType("multipart/alternative")) {
                    val children = (0 until multi.count).map(multi::getBodyPart)
                    val selected = children.firstOrNull { it.isMimeType("text/plain") }
                        ?: children.firstOrNull { it.isMimeType("text/html") } ?: children.firstOrNull()
                    selected?.let { walk(it, depth + 1, captureText) }
                    // Retain the HTML alternative without duplicating its text in the plain view.
                    if (selected?.isMimeType("text/plain") == true) {
                        children.firstOrNull { it.isMimeType("text/html") || it.isMimeType("multipart/related") }
                            ?.let { walk(it, depth + 1, false) }
                    }
                } else for (i in 0 until multi.count) walk(multi.getBodyPart(i), depth + 1, captureText)
            }
        }
        walk(message, 0)
        return Content(text.toString(), attachments, if (htmlTooLarge) "" else ErmisHtml.sanitize(html.toString()))
    }
    /** Shared across all nested multipart parsers, including bytes parsed again in a child. */
    private class RawBudget(private var remaining: Int) {
        fun wrap(source: InputStream): InputStream = object : FilterInputStream(source) {
            private fun charge(count: Int) {
                if (count > remaining) throw IOException("MIME raw parsing limit")
                if (count > 0) remaining -= count
            }
            override fun read(): Int {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val value = `in`.read()
                if (value >= 0) charge(1)
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = `in`.read(buffer, offset, minOf(length, remaining + 1))
                charge(count)
                return count
            }
            override fun skip(count: Long): Long {
                val buffer = ByteArray(8192)
                var skipped = 0L
                while (skipped < count) {
                    val read = read(buffer, 0, minOf(buffer.size.toLong(), count - skipped).toInt())
                    if (read < 0) break
                    skipped += read
                }
                return skipped
            }
            // Let JavaMail add its own bounded read-ahead buffer; resets cannot bypass charging.
            override fun markSupported(): Boolean = false
            override fun mark(readLimit: Int) = Unit
            override fun reset(): Unit = throw IOException("Mark not supported")
        }
    }

}
