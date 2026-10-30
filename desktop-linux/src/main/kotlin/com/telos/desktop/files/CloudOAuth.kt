package com.telos.desktop.files

import com.telos.desktop.linux.Sys
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

enum class CloudKind { Dropbox, GoogleDrive, OneDrive }

class CloudOAuthProvider(
    val kind: CloudKind,
    val authUrl: String,
    val tokenUrl: String,
    val scope: String?,
    val redirect: String,
    val extraAuth: Map<String, String> = emptyMap(),
    val needsSecret: Boolean = false,
)

object CloudOAuth {
    const val PORT = 53682

    fun provider(kind: CloudKind): CloudOAuthProvider = when (kind) {
        CloudKind.Dropbox -> CloudOAuthProvider(
            kind,
            "https://www.dropbox.com/oauth2/authorize",
            "https://api.dropboxapi.com/oauth2/token",
            null,
            "http://localhost:$PORT/",
            mapOf("token_access_type" to "offline"),
        )
        CloudKind.GoogleDrive -> CloudOAuthProvider(
            kind,
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://www.googleapis.com/auth/drive",
            "http://127.0.0.1:$PORT/",
            mapOf("access_type" to "offline", "prompt" to "consent"),
            needsSecret = true,
        )
        CloudKind.OneDrive -> CloudOAuthProvider(
            kind,
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            "Files.ReadWrite.All offline_access",
            "http://localhost:$PORT/",
        )
    }

    fun detect(uri: String): CloudKind? {
        val u = uri.trim().lowercase()
        return when {
            u.startsWith("dropbox:") || u.contains("dropbox.com") -> CloudKind.Dropbox
            u.startsWith("gdrive:") || u.contains("drive.google") || u == "drive" || u == "gdrive" -> CloudKind.GoogleDrive
            u.startsWith("onedrive:") || u.contains("onedrive") || u.contains("1drv.") ||
                u.contains("graph.microsoft") -> CloudKind.OneDrive
            else -> null
        }
    }

    fun placeUri(kind: CloudKind): String = when (kind) {
        CloudKind.Dropbox -> "dropbox://"
        CloudKind.GoogleDrive -> "gdrive://"
        CloudKind.OneDrive -> "onedrive://"
    }

    fun verifier(): String {
        val raw = ByteArray(48)
        SecureRandom().nextBytes(raw)
        return b64(raw)
    }

    fun challenge(verifier: String): String =
        b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))

    fun state(): String = verifier().take(24)

    fun authorizeUrl(kind: CloudKind, clientId: String, verifier: String, state: String): String {
        val p = provider(kind)
        val params = linkedMapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to p.redirect,
            "code_challenge" to challenge(verifier),
            "code_challenge_method" to "S256",
            "state" to state,
        )
        p.scope?.let { params["scope"] = it }
        params.putAll(p.extraAuth)
        return p.authUrl + "?" + params.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8)
        }
    }

    fun openBrowser(url: String) {
        Sys.runBg("xdg-open", url)
    }

    fun waitForCode(state: String, timeoutMs: Int = 180_000): String? {
        ServerSocket(PORT, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = timeoutMs
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    break
                }
                socket.use { s ->
                    s.soTimeout = 5_000
                    val line = try {
                        s.getInputStream().bufferedReader().readLine().orEmpty()
                    } catch (_: IOException) {
                        return@use
                    }
                    val query = line.substringAfter(' ').substringBefore(' ').substringAfter('?', "")
                    val params = query.split('&').filter { it.contains('=') }.associate {
                        URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) to
                            URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
                    }
                    val ok = params["state"] == state && params["code"] != null
                    val body = if (ok) {
                        "Signed in. You can close this tab and go back to Telos."
                    } else {
                        params["error_description"] ?: params["error"] ?: "Nothing to do here."
                    }
                    val safe = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    val html = "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>$safe</h2></body></html>"
                    val bytes = html.toByteArray(StandardCharsets.UTF_8)
                    s.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                    if (ok) return params["code"]
                }
            }
        }
        return null
    }

    fun exchange(kind: CloudKind, clientId: String, clientSecret: String, code: String, verifier: String): CloudTokens? {
        val p = provider(kind)
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to p.redirect,
            "code_verifier" to verifier,
        )
        if (clientSecret.isNotEmpty()) form["client_secret"] = clientSecret
        val body = postForm(p.tokenUrl, form) ?: return null
        if (body.code !in 200..299) return null
        val access = jsonField(body.text, "access_token") ?: return null
        val refresh = jsonField(body.text, "refresh_token").orEmpty()
        return CloudTokens(kind, clientId, clientSecret, access, refresh)
    }

    fun refresh(tokens: CloudTokens): CloudTokens? {
        if (tokens.refresh.isEmpty()) return null
        val p = provider(tokens.kind)
        val form = linkedMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to tokens.refresh,
            "client_id" to tokens.clientId,
        )
        if (tokens.clientSecret.isNotEmpty()) form["client_secret"] = tokens.clientSecret
        p.scope?.let { form["scope"] = it }
        val body = postForm(p.tokenUrl, form) ?: return null
        if (body.code !in 200..299) return null
        val access = jsonField(body.text, "access_token") ?: return null
        val rotated = jsonField(body.text, "refresh_token") ?: tokens.refresh
        return tokens.copy(access = access, refresh = rotated)
    }

    internal fun jsonField(json: String, key: String): String? {
        val re = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = re.find(json)?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private data class HttpBody(val code: Int, val text: String)

    private fun postForm(url: String, form: Map<String, String>): HttpBody? {
        val encoded = form.entries.joinToString("&") {
            URLEncoder.encode(it.key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8)
        }
        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "Telos-Desktop")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(encoded.toByteArray(StandardCharsets.UTF_8)) }
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

data class CloudTokens(
    val kind: CloudKind,
    val clientId: String,
    val clientSecret: String,
    val access: String,
    val refresh: String,
)

object OAuthSecrets {
    private val map = ConcurrentHashMap<String, CloudTokens>()
    fun put(uri: String, tokens: CloudTokens) {
        map[uri] = tokens
    }
    fun get(uri: String): CloudTokens? = map[uri]
    fun clear() {
        map.clear()
    }
}

data class CloudEntry(val name: String, val id: String, val directory: Boolean, val size: Long)

object CloudApi {
    fun list(kind: CloudKind, access: String, folderId: String): List<CloudEntry> = when (kind) {
        CloudKind.Dropbox -> dropbox(access, folderId)
        CloudKind.GoogleDrive -> drive(access, folderId)
        CloudKind.OneDrive -> oneDrive(access, folderId)
    }

    private fun dropbox(access: String, folderId: String): List<CloudEntry> {
        val path = if (folderId.isBlank() || folderId == "root") "" else folderId
        val json = postJson("https://api.dropboxapi.com/2/files/list_folder", access, """{"path":"$path","recursive":false}""")
            ?: return emptyList()
        return entries(json)
    }

    private fun drive(access: String, folderId: String): List<CloudEntry> {
        val parent = folderId.ifBlank { "root" }
        val q = URLEncoder.encode("'$parent' in parents and trashed=false", StandardCharsets.UTF_8)
        val url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name,mimeType,size)&pageSize=100"
        val json = get(url, access) ?: return emptyList()
        return driveEntries(json)
    }

    private fun oneDrive(access: String, folderId: String): List<CloudEntry> {
        val url = if (folderId.isBlank() || folderId == "root") {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children"
        }
        val json = get(url, access) ?: return emptyList()
        return graphEntries(json)
    }

    internal fun entries(json: String): List<CloudEntry> {
        val out = mutableListOf<CloudEntry>()
        val re = Regex("\\{([^{}]+)\\}")
        re.findAll(json).forEach { m ->
            val chunk = m.groupValues[1]
            val name = CloudOAuth.jsonField("{$chunk}", "name") ?: return@forEach
            val tag = Regex("\".tag\"\\s*:\\s*\"([^\"]+)\"").find(chunk)?.groupValues?.get(1)
            val id = CloudOAuth.jsonField("{$chunk}", "path_display") ?: CloudOAuth.jsonField("{$chunk}", "id") ?: name
            val size = Regex("\"size\"\\s*:\\s*(\\d+)").find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out += CloudEntry(name, id, tag == "folder", size)
        }
        return out
    }

    internal fun driveEntries(json: String): List<CloudEntry> {
        val out = mutableListOf<CloudEntry>()
        val re = Regex("\\{([^{}]+)\\}")
        re.findAll(json).forEach { m ->
            val chunk = m.groupValues[1]
            val name = CloudOAuth.jsonField("{$chunk}", "name") ?: return@forEach
            val id = CloudOAuth.jsonField("{$chunk}", "id") ?: name
            val mime = CloudOAuth.jsonField("{$chunk}", "mimeType").orEmpty()
            val size = Regex("\"size\"\\s*:\\s*\"?(\\d+)").find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out += CloudEntry(name, id, mime == "application/vnd.google-apps.folder", size)
        }
        return out
    }

    internal fun graphEntries(json: String): List<CloudEntry> {
        val out = mutableListOf<CloudEntry>()
        val re = Regex("\\{([^{}]+)\\}")
        re.findAll(json).forEach { m ->
            val chunk = m.groupValues[1]
            val name = CloudOAuth.jsonField("{$chunk}", "name") ?: return@forEach
            val id = CloudOAuth.jsonField("{$chunk}", "id") ?: name
            val folder = chunk.contains("\"folder\"")
            val size = Regex("\"size\"\\s*:\\s*(\\d+)").find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out += CloudEntry(name, id, folder, size)
        }
        return out
    }

    private fun get(url: String, access: String): String? = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Authorization", "Bearer $access")
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code in 200..299) text else null
    } catch (_: Exception) {
        null
    }

    private fun postJson(url: String, access: String, json: String): String? = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $access")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        conn.outputStream.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code in 200..299) text else null
    } catch (_: Exception) {
        null
    }
}
