package com.telos.desktop.files

import com.telos.desktop.linux.Sys
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
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

    fun download(kind: CloudKind, access: String, id: String, dest: File): File? {
        dest.parentFile?.mkdirs()
        return when (kind) {
            CloudKind.Dropbox -> {
                val path = dropboxPath(id)
                streamTo(
                    "https://content.dropboxapi.com/2/files/download",
                    access,
                    dest,
                    method = "POST",
                    extra = mapOf("Dropbox-API-Arg" to """{"path":"${jsonEscape(path)}"}"""),
                    emptyBody = true,
                )
            }
            CloudKind.GoogleDrive -> streamTo(
                "https://www.googleapis.com/drive/v3/files/${enc(id)}?alt=media",
                access,
                dest,
            )
            CloudKind.OneDrive -> streamTo(
                "https://graph.microsoft.com/v1.0/me/drive/items/${enc(id)}/content",
                access,
                dest,
            )
        }
    }

    fun upload(kind: CloudKind, access: String, parentId: String, name: String, file: File): Boolean {
        if (!file.isFile) return false
        return when (kind) {
            CloudKind.Dropbox -> uploadDropbox(access, parentId, name, file)
            CloudKind.GoogleDrive -> uploadDrive(access, parentId, name, file)
            CloudKind.OneDrive -> uploadOneDrive(access, parentId, name, file)
        }
    }

    internal fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    internal fun dropboxPath(id: String): String {
        val t = id.trim()
        if (t.isEmpty() || t == "root") return ""
        return if (t.startsWith("/")) t else "/$t"
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

    private fun uploadDropbox(access: String, parentId: String, name: String, file: File): Boolean {
        val path = dropboxPath(parentId).trimEnd('/') + "/" + name.trimStart('/')
        val arg = """{"path":"${jsonEscape(path)}","mode":"overwrite","mute":true}"""
        if (file.length() <= 100L * 1024 * 1024) {
            return putFile(
                "https://content.dropboxapi.com/2/files/upload",
                access,
                file,
                method = "POST",
                extra = mapOf("Dropbox-API-Arg" to arg, "Content-Type" to "application/octet-stream"),
            )
        }
        val chunk = 8 * 1024 * 1024
        RandomAccessFile(file, "r").use { raf ->
            fun piece(): ByteArray {
                val n = minOf(chunk.toLong(), raf.length() - raf.filePointer).toInt()
                val b = ByteArray(n)
                raf.readFully(b)
                return b
            }
            val start = postBytes(
                "https://content.dropboxapi.com/2/files/upload_session/start",
                access,
                piece(),
                mapOf("Dropbox-API-Arg" to """{"close":false}""", "Content-Type" to "application/octet-stream"),
            ) ?: return false
            val sessionId = CloudOAuth.jsonField(start, "session_id") ?: return false
            while (raf.length() - raf.filePointer > chunk) {
                val offset = raf.filePointer
                val cursor = """{"session_id":"${jsonEscape(sessionId)}","offset":$offset}"""
                if (!postBytesOk(
                        "https://content.dropboxapi.com/2/files/upload_session/append_v2",
                        access,
                        piece(),
                        mapOf("Dropbox-API-Arg" to """{"cursor":$cursor,"close":false}""", "Content-Type" to "application/octet-stream"),
                    )
                ) return false
            }
            val offset = raf.filePointer
            val cursor = """{"session_id":"${jsonEscape(sessionId)}","offset":$offset}"""
            return postBytesOk(
                "https://content.dropboxapi.com/2/files/upload_session/finish",
                access,
                piece(),
                mapOf("Dropbox-API-Arg" to """{"cursor":$cursor,"commit":$arg}""", "Content-Type" to "application/octet-stream"),
            )
        }
    }

    private fun uploadDrive(access: String, parentId: String, name: String, file: File): Boolean {
        val parent = parentId.ifBlank { "root" }
        val meta = """{"name":"${jsonEscape(name)}","parents":["${jsonEscape(parent)}"]}"""
        val created = postJson("https://www.googleapis.com/drive/v3/files?fields=id", access, meta) ?: return false
        val id = CloudOAuth.jsonField(created, "id") ?: return false
        return putFile(
            "https://www.googleapis.com/upload/drive/v3/files/${enc(id)}?uploadType=media",
            access,
            file,
            method = "PATCH",
            extra = mapOf("Content-Type" to "application/octet-stream"),
        )
    }

    private fun uploadOneDrive(access: String, parentId: String, name: String, file: File): Boolean {
        val encodedName = enc(name)
        val url = if (parentId.isBlank() || parentId == "root") {
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedName:/content"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/items/${enc(parentId)}:/$encodedName:/content"
        }
        if (file.length() <= 4L * 1024 * 1024) {
            return putFile(url, access, file, method = "PUT", extra = mapOf("Content-Type" to "application/octet-stream"))
        }
        val sessionJson = postJson(
            url.removeSuffix("/content") + "/createUploadSession",
            access,
            """{"item":{"@microsoft.graph.conflictBehavior":"replace"}}""",
        ) ?: return false
        val session = CloudOAuth.jsonField(sessionJson, "uploadUrl") ?: return false
        val total = file.length()
        val chunk = 10 * 320 * 1024
        RandomAccessFile(file, "r").use { raf ->
            var start = 0L
            while (start < total) {
                val size = minOf(chunk.toLong(), total - start).toInt()
                val buf = ByteArray(size)
                raf.seek(start)
                raf.readFully(buf)
                val conn = URI(session).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 120_000
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.setRequestProperty("Content-Range", "bytes $start-${start + size - 1}/$total")
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.outputStream.use { it.write(buf) }
                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..308 && code != 202) return false
                start += size
            }
        }
        return true
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

    private fun streamTo(
        url: String,
        access: String,
        dest: File,
        method: String = "GET",
        extra: Map<String, String> = emptyMap(),
        emptyBody: Boolean = false,
    ): File? = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $access")
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (emptyBody) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(0)
            conn.outputStream.close()
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        dest.takeIf { it.isFile }
    } catch (_: Exception) {
        dest.delete()
        null
    }

    private fun putFile(
        url: String,
        access: String,
        file: File,
        method: String,
        extra: Map<String, String>,
    ): Boolean = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.requestMethod = method
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $access")
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.setFixedLengthStreamingMode(file.length())
        file.inputStream().use { input -> conn.outputStream.use { input.copyTo(it) } }
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (_: Exception) {
        false
    }

    private fun postBytes(url: String, access: String, body: ByteArray, extra: Map<String, String>): String? = try {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $access")
        conn.setRequestProperty("User-Agent", "Telos-Desktop")
        extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code in 200..299) text else null
    } catch (_: Exception) {
        null
    }

    private fun postBytesOk(url: String, access: String, body: ByteArray, extra: Map<String, String>): Boolean =
        postBytes(url, access, body, extra) != null

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
