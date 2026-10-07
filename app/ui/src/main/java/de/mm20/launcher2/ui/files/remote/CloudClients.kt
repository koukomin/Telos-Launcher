package de.mm20.launcher2.ui.files.remote

import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private val JSON = "application/json; charset=utf-8".toMediaType()
private val BINARY = "application/octet-stream".toMediaType()

/** What the cloud clients have in common: authorised requests and uploads through a temporary file. */
abstract class CloudClient(private val tokens: AccessTokens, private val cacheDir: File) : RemoteClient {
    protected val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    protected fun authorised(url: String, configure: Request.Builder.() -> Unit = {}): Request =
        Request.Builder().url(url).header("Authorization", "Bearer " + tokens.get()).apply(configure).build()

    /** Runs a request; an error answer becomes an exception with the message of the service. */
    protected fun execute(request: Request): Response {
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val text = response.body?.string().orEmpty().take(300)
            val code = response.code
            response.close()
            throw IOException(
                when (code) {
                    401 -> "Signing in again is needed. Open the connection and sign in."
                    403 -> "Not allowed ($text)"
                    404 -> "Not found"
                    409 -> "Conflict or does not exist ($text)"
                    else -> "The service answered $code $text"
                }
            )
        }
        return response
    }

    protected fun json(request: Request): JSONObject = execute(request).use { JSONObject(it.body?.string().orEmpty().ifBlank { "{}" }) }
    protected fun ok(request: Request): Boolean = runCatching { execute(request).close(); true }.getOrDefault(false)

    protected abstract fun upload(path: String, file: File)

    override fun openWrite(path: String): OutputStream {
        val temp = File.createTempFile("cloud", ".upload", cacheDir)
        return object : FilterOutputStream(temp.outputStream().buffered()) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() {
                try { super.close(); upload(path, temp) } finally { temp.delete() }
            }
        }
    }

    override fun close() { http.connectionPool.evictAll() }
}

/** Dropbox. Paths are used as they are. */
class DropboxClient(tokens: AccessTokens, private val cacheDir: File) : CloudClient(tokens, cacheDir) {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun p(path: String) = if (path.trim('/').isEmpty()) "" else "/" + path.trim('/')

    private fun rpc(method: String, body: JSONObject): JSONObject =
        json(authorised("https://api.dropboxapi.com/2/$method") { post(body.toString().toRequestBody(JSON)) })

    override fun list(path: String): List<RemoteEntry> {
        val out = mutableListOf<RemoteEntry>()
        var result = rpc("files/list_folder", JSONObject().put("path", p(path)).put("limit", 2000))
        while (true) {
            val entries = result.getJSONArray("entries")
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val dir = e.getString(".tag") == "folder"
                out += RemoteEntry(
                    e.getString("name"), e.optString("path_display", e.getString("name")), dir, e.optLong("size", -1),
                    runCatching { iso.parse(e.optString("server_modified"))!!.time }.getOrDefault(0),
                )
            }
            if (!result.optBoolean("has_more")) break
            result = rpc("files/list_folder/continue", JSONObject().put("cursor", result.getString("cursor")))
        }
        return out
    }

    override fun mkdir(path: String) = runCatching { rpc("files/create_folder_v2", JSONObject().put("path", p(path))); true }.getOrDefault(false)
    override fun delete(path: String) = runCatching { rpc("files/delete_v2", JSONObject().put("path", p(path))); true }.getOrDefault(false)
    override fun rename(from: String, to: String) = runCatching { rpc("files/move_v2", JSONObject().put("from_path", p(from)).put("to_path", p(to))); true }.getOrDefault(false)
    override fun copy(from: String, to: String) = runCatching { rpc("files/copy_v2", JSONObject().put("from_path", p(from)).put("to_path", p(to))); true }.getOrDefault(false)

    override fun openRead(path: String): InputStream =
        execute(authorised("https://content.dropboxapi.com/2/files/download") {
            header("Dropbox-API-Arg", JSONObject().put("path", p(path)).toString())
            post(ByteArray(0).toRequestBody(null))
        }).body!!.byteStream()

    override fun upload(path: String, file: File) {
        val commit = JSONObject().put("path", p(path)).put("mode", "overwrite").put("mute", true)
        if (file.length() <= 100L * 1024 * 1024) {
            execute(authorised("https://content.dropboxapi.com/2/files/upload") {
                header("Dropbox-API-Arg", commit.toString()); post(file.asRequestBody(BINARY))
            }).close()
            return
        }
        // large files go up in pieces
        val chunk = 8 * 1024 * 1024
        RandomAccessFile(file, "r").use { raf ->
            fun piece(): ByteArray { val buffer = ByteArray(minOf(chunk.toLong(), raf.length() - raf.filePointer).toInt()); raf.readFully(buffer); return buffer }
            val sessionId = json(authorised("https://content.dropboxapi.com/2/files/upload_session/start") {
                header("Dropbox-API-Arg", JSONObject().put("close", false).toString()); post(piece().toRequestBody(BINARY))
            }).getString("session_id")
            while (raf.length() - raf.filePointer > chunk) {
                val offset = raf.filePointer
                val cursor = JSONObject().put("session_id", sessionId).put("offset", offset)
                execute(authorised("https://content.dropboxapi.com/2/files/upload_session/append_v2") {
                    header("Dropbox-API-Arg", JSONObject().put("cursor", cursor).put("close", false).toString()); post(piece().toRequestBody(BINARY))
                }).close()
            }
            val offset = raf.filePointer
            val cursor = JSONObject().put("session_id", sessionId).put("offset", offset)
            execute(authorised("https://content.dropboxapi.com/2/files/upload_session/finish") {
                header("Dropbox-API-Arg", JSONObject().put("cursor", cursor).put("commit", commit).toString()); post(piece().toRequestBody(BINARY))
            }).close()
        }
    }
}

/** OneDrive through the Microsoft Graph. Paths are used as they are. */
class OneDriveClient(tokens: AccessTokens, private val cacheDir: File) : CloudClient(tokens, cacheDir) {
    private val graph = "https://graph.microsoft.com/v1.0/me/drive"
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun item(path: String) = if (path.trim('/').isEmpty()) "$graph/root" else "$graph/root:" + encode(path) + ":"
    private fun encode(path: String) = "/" + path.trim('/').split('/').joinToString("/") { Uri.encode(it) }

    override fun list(path: String): List<RemoteEntry> {
        val out = mutableListOf<RemoteEntry>()
        var url: String? = item(path) + "/children?\$top=500"
        while (url != null) {
            val result = json(authorised(url))
            val values = result.getJSONArray("value")
            for (i in 0 until values.length()) {
                val e = values.getJSONObject(i)
                val name = e.getString("name")
                out += RemoteEntry(
                    name, "/" + path.trim('/') + "/" + name, e.has("folder"), e.optLong("size", -1),
                    runCatching { iso.parse(e.optString("lastModifiedDateTime").substringBefore('.').removeSuffix("Z"))!!.time }.getOrDefault(0),
                ).let { RemoteEntry(it.name, it.path.replace("//", "/"), it.isDir, it.size, it.modified) }
            }
            url = result.optString("@odata.nextLink").ifEmpty { null }
        }
        return out
    }

    override fun mkdir(path: String): Boolean {
        val parent = path.trim('/').substringBeforeLast('/', "")
        val body = JSONObject().put("name", path.trim('/').substringAfterLast('/')).put("folder", JSONObject()).put("@microsoft.graph.conflictBehavior", "fail")
        return runCatching { json(authorised(item(parent) + "/children") { post(body.toString().toRequestBody(JSON)) }); true }.getOrDefault(false)
    }

    override fun delete(path: String) = ok(authorised(item(path)) { delete() })

    override fun rename(from: String, to: String): Boolean {
        val parent = "/drive/root:" + (if (to.trim('/').contains('/')) encode(to.trim('/').substringBeforeLast('/')) else "")
        val body = JSONObject().put("name", to.trim('/').substringAfterLast('/')).put("parentReference", JSONObject().put("path", parent.ifEmpty { "/drive/root:" }))
        return ok(authorised(item(from)) { patch(body.toString().toRequestBody(JSON)) })
    }

    override fun openRead(path: String): InputStream = execute(authorised(item(path) + "/content")).body!!.byteStream()

    override fun upload(path: String, file: File) {
        if (file.length() <= 4L * 1024 * 1024) {
            execute(authorised(item(path) + "/content") { put(file.asRequestBody(BINARY)) }).close()
            return
        }
        // a larger file goes up in pieces through an upload session (the pieces have to be a multiple of 320 KiB)
        val session = json(authorised(item(path) + "/createUploadSession") {
            post(JSONObject().put("item", JSONObject().put("@microsoft.graph.conflictBehavior", "replace")).toString().toRequestBody(JSON))
        }).getString("uploadUrl")
        val total = file.length()
        val chunk = 10 * 320 * 1024
        RandomAccessFile(file, "r").use { raf ->
            var start = 0L
            while (start < total) {
                val size = minOf(chunk.toLong(), total - start).toInt()
                val buffer = ByteArray(size)
                raf.seek(start); raf.readFully(buffer)
                // the upload address is pre-authorised and takes no token
                val request = Request.Builder().url(session)
                    .header("Content-Range", "bytes $start-${start + size - 1}/$total")
                    .put(buffer.toRequestBody(BINARY)).build()
                http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("The upload failed (${it.code})") }
                start += size
            }
        }
    }
}

/**
 * Google Drive. A file has an ID rather than a path, so the path is followed folder by folder. Documents,
 * sheets and slides made in Google's own apps are offered as PDF or Office files.
 */
class GoogleDriveClient(tokens: AccessTokens, private val cacheDir: File) : CloudClient(tokens, cacheDir) {
    private val api = "https://www.googleapis.com/drive/v3/files"
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val folder = "application/vnd.google-apps.folder"
    private val ids = HashMap<String, String>()

    private val exports = mapOf(
        "application/vnd.google-apps.document" to ("pdf" to "application/pdf"),
        "application/vnd.google-apps.spreadsheet" to ("xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        "application/vnd.google-apps.presentation" to ("pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        "application/vnd.google-apps.drawing" to ("png" to "image/png"),
    )

    private fun q(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

    private fun children(parentId: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var token: String? = null
        do {
            val url = StringBuilder("$api?q=").append(Uri.encode("'$parentId' in parents and trashed=false"))
                .append("&fields=").append(Uri.encode("nextPageToken,files(id,name,mimeType,size,modifiedTime)")).append("&pageSize=1000")
            token?.let { url.append("&pageToken=").append(Uri.encode(it)) }
            val result = json(authorised(url.toString()))
            val files = result.getJSONArray("files")
            for (i in 0 until files.length()) out += files.getJSONObject(i)
            token = result.optString("nextPageToken").ifEmpty { null }
        } while (token != null)
        return out
    }

    /** The name as shown: an exported Google document gets its extension */
    private fun shownName(f: JSONObject): String {
        val export = exports[f.optString("mimeType")]
        return if (export != null) f.getString("name") + "." + export.first else f.getString("name")
    }

    private fun resolve(path: String): String {
        val clean = "/" + path.trim('/')
        if (clean == "/") return "root"
        ids[clean]?.let { return it }
        var current = "root"
        var walked = ""
        for (segment in clean.trim('/').split('/')) {
            walked += "/$segment"
            current = ids[walked] ?: children(current).firstOrNull { shownName(it) == segment || it.getString("name") == segment }?.getString("id")
                ?: throw IOException("Not found: $walked")
            ids[walked] = current
        }
        return current
    }

    override fun list(path: String): List<RemoteEntry> {
        val dir = "/" + path.trim('/')
        val parentId = resolve(dir)
        return children(parentId).map { f ->
            val name = shownName(f)
            val isDir = f.getString("mimeType") == folder
            val p = (if (dir == "/") "" else dir) + "/" + name
            ids[p] = f.getString("id")
            RemoteEntry(name, p, isDir, f.optString("size").toLongOrNull() ?: -1, runCatching { iso.parse(f.optString("modifiedTime").substringBefore('.').removeSuffix("Z"))!!.time }.getOrDefault(0))
        }
    }

    override fun mkdir(path: String): Boolean = runCatching {
        val parent = resolve("/" + path.trim('/').substringBeforeLast('/', ""))
        val body = JSONObject().put("name", path.trim('/').substringAfterLast('/')).put("mimeType", folder).put("parents", JSONArray().put(parent))
        json(authorised("$api?fields=id") { post(body.toString().toRequestBody(JSON)) })
        true
    }.getOrDefault(false)

    override fun delete(path: String) = runCatching {
        ids.clear()
        execute(authorised("$api/${resolve(path)}") { delete() }).close(); true
    }.getOrDefault(false)

    override fun rename(from: String, to: String): Boolean = runCatching {
        val id = resolve(from)
        val oldParent = resolve("/" + from.trim('/').substringBeforeLast('/', ""))
        val newParent = resolve("/" + to.trim('/').substringBeforeLast('/', ""))
        val body = JSONObject().put("name", to.trim('/').substringAfterLast('/'))
        ids.clear()
        json(authorised("$api/$id?addParents=$newParent&removeParents=$oldParent") { patch(body.toString().toRequestBody(JSON)) })
        true
    }.getOrDefault(false)

    override fun openRead(path: String): InputStream {
        val id = resolve(path)
        val info = json(authorised("$api/$id?fields=mimeType"))
        val export = exports[info.optString("mimeType")]
        val url = if (export != null) "$api/$id/export?mimeType=${Uri.encode(export.second)}" else "$api/$id?alt=media"
        return execute(authorised(url)).body!!.byteStream()
    }

    override fun upload(path: String, file: File) {
        val name = path.trim('/').substringAfterLast('/')
        val parent = resolve("/" + path.trim('/').substringBeforeLast('/', ""))
        val existing = children(parent).firstOrNull { it.getString("name") == name && it.getString("mimeType") != folder }?.getString("id")
        val id = existing ?: json(authorised("$api?fields=id") {
            post(JSONObject().put("name", name).put("parents", JSONArray().put(parent)).toString().toRequestBody(JSON))
        }).getString("id")
        // the content goes up through a resumable session, in pieces that are a multiple of 256 KiB
        val session = execute(authorised("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=resumable") {
            patch(ByteArray(0).toRequestBody(null))
            header("X-Upload-Content-Length", file.length().toString())
        }).use { it.header("Location") ?: throw IOException("The upload could not be started") }
        val total = file.length()
        if (total == 0L) { ids.clear(); return }
        val chunk = 16 * 256 * 1024
        RandomAccessFile(file, "r").use { raf ->
            var start = 0L
            while (start < total) {
                val size = minOf(chunk.toLong(), total - start).toInt()
                val buffer = ByteArray(size)
                raf.seek(start); raf.readFully(buffer)
                val request = Request.Builder().url(session)
                    .header("Content-Range", "bytes $start-${start + size - 1}/$total")
                    .put(buffer.toRequestBody(BINARY)).build()
                http.newCall(request).execute().use { if (it.code != 308 && !it.isSuccessful) throw IOException("The upload failed (${it.code})") }
                start += size
            }
        }
        ids.clear()
    }
}
