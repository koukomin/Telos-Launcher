package de.mm20.launcher2.comms.remote

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

data class RemoteContact(val name: String, val numbers: List<String>)

/**
 * Reads the telephone book(s) of an AVM FRITZ!Box over TR-064 (X_AVM-DE_OnTel).
 * The box asks for HTTP digest authentication; the user needs the "FRITZ!Box settings" right.
 */
class FritzBoxClient(
    private val host: String,
    private val user: String,
    private val password: String,
) {
    suspend fun fetchContacts(): List<RemoteContact> = withContext(Dispatchers.IO) {
        val base = "http://${host.trim()}:49000"
        val list = tag(soap(base, "GetPhonebookList", ""), "NewPhonebookList")
        val ids = list.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(0) }
        val out = mutableListOf<RemoteContact>()
        for (id in ids) {
            val resp = soap(base, "GetPhonebook", "<NewPhonebookID>$id</NewPhonebookID>")
            val url = tag(resp, "NewPhonebookURL")
            if (url.isBlank()) continue
            // the box names the address to fetch the phonebook from; it only gets the login if that
            // address is the box itself
            val urlHost = runCatching { java.net.URI(url).host }.getOrNull()
            if (!urlHost.equals(host.trim(), ignoreCase = true)) continue
            out += parsePhonebook(request("GET", url, null, emptyMap()))
        }
        out
    }

    private fun soap(base: String, action: String, args: String): String {
        val body = "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"$SERVICE\">$args</u:$action></s:Body></s:Envelope>"
        return request(
            "POST",
            "$base/upnp/control/x_contact",
            body,
            mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SoapAction" to "$SERVICE#$action",
            ),
        )
    }

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>): String {
        var auth: String? = null
        for (attempt in 0..1) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                auth?.let { conn.setRequestProperty("Authorization", it) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code == 401 && attempt == 0) {
                    val challenge = conn.getHeaderField("WWW-Authenticate")
                        ?: throw IOException("HTTP 401 without challenge")
                    auth = digest(challenge, method, URL(url).file)
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Authentication failed")
    }

    private fun digest(challenge: String, method: String, uri: String): String {
        val params = Regex("(\\w+)=\"?([^\",]*)\"?").findAll(challenge)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val realm = params["realm"].orEmpty()
        val nonce = params["nonce"].orEmpty()
        val qop = params["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        val nc = "00000001"
        val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val ha1 = md5("$user:$realm:$password")
        val ha2 = md5("$method:$uri")
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\"")
            params["opaque"]?.let { append(", opaque=\"$it\"") }
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun tag(xml: String, name: String): String =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.trim().orEmpty()

    private fun parsePhonebook(xml: String): List<RemoteContact> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<RemoteContact>()
        var name: String? = null
        val numbers = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "contact" -> {
                        name = null
                        numbers.clear()
                    }
                    "realName" -> name = parser.nextText().trim()
                    "number" -> parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { numbers += it }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "contact") {
                val n = name
                if (!n.isNullOrBlank() && numbers.isNotEmpty()) out += RemoteContact(n, numbers.toList())
            }
            event = parser.next()
        }
        return out
    }

    private companion object {
        const val SERVICE = "urn:dslforum-org:service:X_AVM-DE_OnTel:1"
    }
}
