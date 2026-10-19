package de.mm20.launcher2.comms.ermis

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.ContentType
import org.jsoup.Jsoup

/** Both display and download use this exact traversal, so an attachment index is stable. */
internal object ErmisMime {
    data class Content(val body: String, val attachments: List<Part>)
    fun read(message: Part): Content {
        val text = StringBuilder()
        val attachments = mutableListOf<Part>()
        var visited = 0
        var bodyBytes = 0
        fun walk(part: Part, depth: Int) {
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
                val plain = if (part.isMimeType("text/html")) Jsoup.parse(body).text() else body
                if (text.isNotEmpty() && text.length < 1_000_000) text.append('\n')
                text.append(plain.take((1_000_000 - text.length).coerceAtLeast(0)))
            } else if (part.isMimeType("multipart/*")) {
                val multi = part.content as Multipart
                require(multi.count <= 100) { "MIME part limit" }
                if (part.isMimeType("multipart/alternative")) {
                    val children = (0 until multi.count).map(multi::getBodyPart)
                    val selected = children.firstOrNull { it.isMimeType("text/plain") }
                        ?: children.firstOrNull { it.isMimeType("text/html") } ?: children.firstOrNull()
                    selected?.let { walk(it, depth + 1) }
                } else for (i in 0 until multi.count) walk(multi.getBodyPart(i), depth + 1)
            }
        }
        walk(message, 0)
        return Content(text.toString(), attachments)
    }
}
