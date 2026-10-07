package de.mm20.launcher2.comms.sms

import java.io.ByteArrayOutputStream

/** One part of a multimedia message: a text, a picture, ... */
class MmsPart(val contentType: String, val name: String, val data: ByteArray)

/** What the notification of a new multimedia message tells: where to fetch it. */
class MmsNotification(val location: String, val transactionId: String?, val size: Long?, val from: String?)

/** A received multimedia message, taken apart. */
class MmsMessage(val from: String?, val subject: String?, val dateSeconds: Long?, val parts: List<MmsPart>)

/**
 * The binary format of multimedia messages (OMA MMS encapsulation on top of WSP). Only what Telos
 * needs: reading the notification and a retrieved message, and writing a message to send.
 */
object MmsPdu {

    private class Reader(val b: ByteArray, var p: Int = 0) {
        fun hasMore() = p < b.size
        fun u8() = b[p++].toInt() and 0xFF
        fun peek() = b[p].toInt() and 0xFF
        fun uintvar(): Int {
            var v = 0
            while (true) {
                val x = u8()
                v = (v shl 7) or (x and 0x7F)
                if (x and 0x80 == 0) break
            }
            return v
        }
        fun text(): String {
            if (peek() == 0x7F) p++
            val start = p
            while (p < b.size && b[p] != 0.toByte()) p++
            val s = String(b, start, p - start, Charsets.UTF_8)
            if (p < b.size) p++
            return s
        }
        /** A value as WSP encodes it: short integer, length-prefixed, or text. Skips it. */
        fun skipValue() {
            val f = peek()
            when {
                f <= 30 -> { p++; p += f }
                f == 31 -> { p++; val n = uintvar(); p += n }
                f in 32..127 -> text()
                else -> p++
            }
        }
        fun longInteger(): Long {
            val f = peek()
            if (f >= 0x80) { p++; return (f and 0x7F).toLong() }
            val n = u8()
            var v = 0L
            repeat(n) { v = (v shl 8) or u8().toLong() }
            return v
        }
    }

    // ---- reading ----

    fun parseNotification(data: ByteArray): MmsNotification? = runCatching {
        val r = Reader(data)
        var location: String? = null
        var tid: String? = null
        var size: Long? = null
        var from: String? = null
        var type = -1
        while (r.hasMore()) {
            val field = r.peek()
            if (field < 0x80) break
            r.p++
            when (field and 0x7F) {
                0x0C -> type = r.u8()
                0x18 -> tid = r.text()
                0x03 -> location = r.text()
                0x0E -> size = r.longInteger()
                0x09 -> from = readFrom(r)
                else -> r.skipValue()
            }
        }
        if (type != 0x82 || location.isNullOrBlank()) null else MmsNotification(location, tid, size, from)
    }.getOrNull()

    /** From: value-length, address-present token (0x80) or insert token (0x81), then the address as text. */
    private fun readFrom(r: Reader): String? {
        val length = r.u8().let { if (it == 31) r.uintvar() else it }
        val end = r.p + length
        var result: String? = null
        if (length > 0 && r.u8() == 0x80) {
            // an encoded string: plain text, or a length and a character set before the text
            val first = r.peek()
            if (first <= 30) r.p += 1 + first
            if (r.p < end) {
                val bytes = r.b.copyOfRange(r.p, end).takeWhile { it != 0.toByte() }.toByteArray()
                result = String(bytes, Charsets.UTF_8).substringBefore("/TYPE=")
            }
        }
        r.p = end
        return result?.takeIf { it.isNotBlank() }
    }

    fun parseRetrieved(data: ByteArray): MmsMessage? = runCatching {
        val r = Reader(data)
        var from: String? = null
        var subject: String? = null
        var date: Long? = null
        var haveBody = false
        while (r.hasMore()) {
            val field = r.peek()
            if (field < 0x80) break
            r.p++
            when (field and 0x7F) {
                0x09 -> from = readFrom(r)
                0x16 -> subject = r.text()
                0x05 -> date = r.longInteger()
                0x04 -> { r.skipValue(); haveBody = true; break } // Content-Type: the parts follow
                else -> r.skipValue()
            }
        }
        if (!haveBody) return@runCatching null
        MmsMessage(from, subject, date, readParts(r))
    }.getOrNull()

    private fun readParts(r: Reader): List<MmsPart> {
        val count = r.uintvar()
        val parts = mutableListOf<MmsPart>()
        repeat(count) { index ->
            val headersLength = r.uintvar()
            val dataLength = r.uintvar()
            val headersEnd = r.p + headersLength
            val contentType = readContentType(r)
            var name = ""
            while (r.p < headersEnd) {
                val h = r.u8()
                when (h) {
                    0x8E, 0x85 -> name = r.text() // Content-Location, Name
                    else -> if (h >= 0x80) r.skipValue() else break
                }
            }
            r.p = headersEnd
            val bytes = r.b.copyOfRange(r.p, minOf(r.b.size, r.p + dataLength))
            r.p += dataLength
            val type = if (contentType == "application/octet-stream") sniff(bytes) ?: contentType else contentType
            parts += MmsPart(type, name.ifBlank { "part$index" }, bytes)
        }
        return parts
    }

    private fun readContentType(r: Reader): String {
        val f = r.peek()
        return when {
            f >= 0x80 -> { r.p++; wellKnown[f and 0x7F] ?: "application/octet-stream" }
            f <= 30 -> {
                r.p++
                val end = r.p + f
                val type = if (r.peek() >= 0x80) wellKnown[r.u8() and 0x7F] ?: "application/octet-stream" else r.text()
                r.p = end
                type
            }
            f == 31 -> { r.p++; val n = r.uintvar(); val end = r.p + n; val t = if (r.peek() >= 0x80) wellKnown[r.u8() and 0x7F] ?: "application/octet-stream" else r.text(); r.p = end; t }
            else -> r.text()
        }
    }

    private fun sniff(b: ByteArray): String? = when {
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
        b.size > 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "image/png"
        b.size > 4 && String(b, 0, 4, Charsets.ISO_8859_1) == "GIF8" -> "image/gif"
        b.size > 12 && String(b, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "video/mp4"
        b.size > 5 && String(b, 0, 5, Charsets.ISO_8859_1) == "#!AMR" -> "audio/amr"
        else -> null
    }

    /** The WSP numbers for the content types that appear in messages */
    private val wellKnown = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain",
        0x06 to "text/x-vCalendar", 0x07 to "text/x-vCard",
        0x1C to "image/*", 0x1D to "image/gif", 0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png",
        0x23 to "application/vnd.wap.multipart.mixed", 0x33 to "application/vnd.wap.multipart.related",
    )

    // ---- writing ----

    private fun ByteArrayOutputStream.text(s: String) {
        write(s.toByteArray(Charsets.UTF_8))
        write(0)
    }

    private fun ByteArrayOutputStream.uintvar(value: Int) {
        val bytes = mutableListOf<Int>()
        var v = value
        bytes += v and 0x7F
        v = v ushr 7
        while (v > 0) { bytes += (v and 0x7F) or 0x80; v = v ushr 7 }
        bytes.asReversed().forEach { write(it) }
    }

    /** A message to send (m-send-req) to the given numbers. */
    fun buildSendRequest(recipients: List<String>, parts: List<MmsPart>, subject: String?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x8C); out.write(0x80) // message type: send request
        out.write(0x98); out.text(System.currentTimeMillis().toString()) // transaction id
        out.write(0x8D); out.write(0x92) // MMS 1.2
        out.write(0x89); out.write(0x01); out.write(0x81) // from: filled in by the network
        for (r in recipients) {
            out.write(0x97)
            out.text(if (r.contains('@')) r else "$r/TYPE=PLMN")
        }
        if (!subject.isNullOrBlank()) { out.write(0x96); out.text(subject) }
        out.write(0x84); out.write(0xA3) // content type: multipart/mixed
        out.uintvar(parts.size)
        for (part in parts) {
            val headers = ByteArrayOutputStream()
            if (part.contentType.startsWith("text/plain")) {
                // text/plain; charset=utf-8
                val type = "text/plain".toByteArray(Charsets.US_ASCII)
                headers.write(type.size + 1 + 2)
                headers.write(type); headers.write(0)
                headers.write(0x81); headers.write(0xEA)
            } else {
                headers.text(part.contentType)
            }
            headers.write(0x8E); headers.text(part.name)
            headers.write(0xC0); headers.text("<${part.name}>")
            out.uintvar(headers.size())
            out.uintvar(part.data.size)
            out.write(headers.toByteArray())
            out.write(part.data)
        }
        return out.toByteArray()
    }
}
