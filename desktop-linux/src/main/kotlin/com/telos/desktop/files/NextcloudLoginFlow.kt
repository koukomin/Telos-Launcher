package com.telos.desktop.files

import com.telos.desktop.linux.Sys
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NextcloudLoginSession(
    val loginUrl: String,
    val pollEndpoint: String,
    val pollToken: String,
)

data class NextcloudLoginResult(
    val server: String,
    val loginName: String,
    val appPassword: String,
)

object NextcloudLoginFlow {
    fun baseUrl(serverUrl: String): String {
        var u = serverUrl.trim().trimEnd('/')
        when {
            u.startsWith("davs://", true) -> u = "https://" + u.substring(7)
            u.startsWith("dav://", true) -> u = "http://" + u.substring(6)
        }
        u = u.substringBefore("/remote.php")
        u = u.substringBefore("/index.php/login")
        return u.trimEnd('/')
    }

    fun start(serverUrl: String): NextcloudLoginSession? {
        val base = baseUrl(serverUrl)
        val endpoint = "$base/index.php/login/v2"
        val body = post(endpoint, ByteArray(0), "application/json") ?: return null
        if (body.code !in 200..299) return null
        val login = jsonField(body.text, "login") ?: return null
        val token = jsonField(body.text, "token") ?: return null
        val poll = jsonField(body.text, "endpoint") ?: return null
        return NextcloudLoginSession(login, poll, token)
    }

    fun poll(session: NextcloudLoginSession): NextcloudLoginResult? {
        val form = "token=" + URLEncoder.encode(session.pollToken, StandardCharsets.UTF_8)
        val body = post(
            session.pollEndpoint,
            form.toByteArray(StandardCharsets.UTF_8),
            "application/x-www-form-urlencoded",
        ) ?: return null
        if (body.code != 200) return null
        val server = jsonField(body.text, "server") ?: return null
        val name = jsonField(body.text, "loginName") ?: return null
        val pass = jsonField(body.text, "appPassword") ?: return null
        if (name.isBlank() || pass.isBlank()) return null
        return NextcloudLoginResult(server.trimEnd('/'), name, pass)
    }

    fun openBrowser(loginUrl: String) {
        Sys.runBg("xdg-open", loginUrl)
    }

    internal fun jsonField(json: String, key: String): String? {
        val re = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = re.find(json)?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
    }

    private data class HttpBody(val code: Int, val text: String)

    private fun post(url: String, body: ByteArray, contentType: String): HttpBody? {
        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "Telos-Desktop")
            conn.setRequestProperty("Content-Type", contentType)
            conn.setRequestProperty("OCS-APIREQUEST", "true")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            HttpBody(code, text)
        } catch (_: Exception) {
            null
        }
    }
}
