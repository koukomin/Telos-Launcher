package com.telos.desktop.files

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class DavEntry(
    val name: String,
    val href: String,
    val directory: Boolean,
    val size: Long,
)

object DavSecrets {
    private val map = ConcurrentHashMap<String, String>()
    fun put(uri: String, password: String) {
        if (password.isNotEmpty()) map[uri] = password
    }
    fun get(uri: String): String? = map[uri]
    fun clear() {
        map.clear()
    }
}

object WebDavClient {
    fun normalize(url: String, user: String): String {
        var u = url.trim().trimEnd('/')
        when {
            u.startsWith("davs://", true) -> u = "https://" + u.substring(7)
            u.startsWith("dav://", true) -> u = "http://" + u.substring(6)
        }
        if (user.isNotBlank() && !u.contains("/dav", ignoreCase = true) && !u.contains("/remote.php/", ignoreCase = true)) {
            u = "$u/remote.php/dav/files/${user.trim('/')}"
        }
        return u
    }

    fun parsePropfind(xml: String, requestHref: String): List<DavEntry> {
        val self = requestHref.substringAfter("://").substringAfter('/')
        val responses = Regex("(?is)<[^>]*:?response[^>]*>.*?</[^>]*:?response>").findAll(xml)
        val out = ArrayList<DavEntry>()
        for (m in responses) {
            val block = m.value
            val href = Regex("(?is)<[^>]*:?href[^>]*>([^<]+)</").find(block)?.groupValues?.get(1)?.trim() ?: continue
            val decoded = href.replace("&amp;", "&")
            val path = decoded.substringAfter("://").let { if (it.contains('/')) it.substringAfter('/') else it.trimStart('/') }
            if (path.trim('/') == self.trim('/')) continue
            val directory = Regex("(?is)<[^>]*:?collection\\s*/>").containsMatchIn(block) || decoded.endsWith("/")
            val size = Regex("(?is)<[^>]*:?getcontentlength[^>]*>(\\d+)</").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val name = decoded.trimEnd('/').substringAfterLast('/').ifBlank { decoded }
            out += DavEntry(name, decoded, directory, size)
        }
        return out.distinctBy { it.href }
    }

    fun list(url: String, user: String, password: String): List<DavEntry> {
        val conn = open(url, user, password, "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
        conn.doOutput = true
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:displayname/></d:prop></d:propfind>"""
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val xml = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) return emptyList()
        return parsePropfind(xml, url)
    }

    fun download(url: String, user: String, password: String, dest: File): File? {
        val conn = open(url, user, password, "GET")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        dest.parentFile?.mkdirs()
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        return dest.takeIf { it.isFile }
    }

    private fun open(url: String, user: String, password: String, method: String): HttpURLConnection {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection)
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        if (user.isNotBlank() || password.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(StandardCharsets.UTF_8))
            conn.setRequestProperty("Authorization", "Basic $token")
        }
        return conn
    }
}
