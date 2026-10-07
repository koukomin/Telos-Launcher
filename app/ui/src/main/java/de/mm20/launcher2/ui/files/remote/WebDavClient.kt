package de.mm20.launcher2.ui.files.remote

import android.net.Uri
import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** WebDAV, which is also what Nextcloud and ownCloud speak. [base] is the address of the folder that counts as the root. */
class WebDavClient(
    private val base: String,
    private val user: String,
    private val password: String,
    private val cacheDir: File,
) : RemoteClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val basePath: String = Uri.parse(base).path.orEmpty().trimEnd('/')
    private val auth: String? = if (user.isNotEmpty()) Credentials.basic(user, password, Charsets.UTF_8) else null

    private fun url(path: String): String {
        val encoded = path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
        return base.trimEnd('/') + if (encoded.isEmpty()) "/" else "/$encoded"
    }

    private fun request(url: String, method: String, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): Request {
        val b = Request.Builder().url(url).method(method, body)
        auth?.let { b.header("Authorization", it) }
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun expect(method: String, url: String, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): Boolean =
        http.newCall(request(url, method, body, headers)).execute().use { it.isSuccessful }

    override fun list(path: String): List<RemoteEntry> {
        val propfind = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        val req = request(url(path).trimEnd('/') + "/", "PROPFIND", propfind.toRequestBody("application/xml".toMediaType()), mapOf("Depth" to "1"))
        http.newCall(req).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw IOException("Access denied. Check the user name and password.")
            if (!response.isSuccessful) throw IOException("The server answered ${response.code}")
            return parse(response.body!!.byteStream(), path)
        }
    }

    private fun parse(input: InputStream, requested: String): List<RemoteEntry> {
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        val out = mutableListOf<RemoteEntry>()
        var href = ""
        var dir = false
        var size = -1L
        var modified = 0L
        var text = ""
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
        val wanted = "/" + requested.trim('/')
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')?.lowercase()
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = ""
                    when (name) {
                        "response" -> { href = ""; dir = false; size = -1; modified = 0 }
                        "collection" -> dir = true
                    }
                }
                XmlPullParser.TEXT -> text += parser.text
                XmlPullParser.END_TAG -> when (name) {
                    "href" -> href = text.trim()
                    "getcontentlength" -> size = text.trim().toLongOrNull() ?: -1
                    "getlastmodified" -> modified = runCatching { date.parse(text.trim())!!.time }.getOrDefault(0)
                    "response" -> {
                        // the address on the server, without the part that leads to our root
                        var p = Uri.decode(Uri.parse(href).path ?: href)
                        if (basePath.isNotEmpty() && p.startsWith(basePath)) p = p.removePrefix(basePath)
                        p = "/" + p.trim('/')
                        if (p != wanted && p != "/") out += RemoteEntry(p.substringAfterLast('/'), p, dir, size, modified)
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    override fun mkdir(path: String) = expect("MKCOL", url(path).trimEnd('/') + "/")
    override fun delete(path: String) = expect("DELETE", url(path))
    override fun rename(from: String, to: String) = expect("MOVE", url(from), null, mapOf("Destination" to url(to), "Overwrite" to "F"))
    override fun copy(from: String, to: String) = expect("COPY", url(from), null, mapOf("Destination" to url(to), "Overwrite" to "F"))

    override fun openRead(path: String): InputStream {
        val response = http.newCall(request(url(path), "GET")).execute()
        if (!response.isSuccessful) { response.close(); throw IOException("The server answered ${response.code}") }
        return response.body!!.byteStream()
    }

    /** The data is collected in a file and sent when the stream is closed */
    override fun openWrite(path: String): OutputStream {
        val temp = File.createTempFile("webdav", ".upload", cacheDir)
        return object : FilterOutputStream(temp.outputStream().buffered()) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() {
                try {
                    super.close()
                    http.newCall(request(url(path), "PUT", temp.asRequestBody("application/octet-stream".toMediaType()))).execute().use {
                        if (!it.isSuccessful) throw IOException("The upload failed (${it.code})")
                    }
                } finally {
                    temp.delete()
                }
            }
        }
    }

    override fun close() { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
}
