package de.mm20.launcher2.ui.files.remote

import android.content.Context
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.ui.files.Fs
import de.mm20.launcher2.ui.files.FsEntry
import de.mm20.launcher2.ui.files.joinPath
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

enum class RemoteType(val label: String, val defaultPort: Int, val cloud: Boolean, val hint: String) {
    Nextcloud("Nextcloud", 443, false, "Server address, for example cloud.example.com"),
    Owncloud("ownCloud", 443, false, "Server address, for example cloud.example.com"),
    WebDav("WebDAV", 443, false, "Server address of any WebDAV server (also Synology, Box, Yandex, ...)"),
    Sftp("SFTP / SSHFS (SSH)", 22, false, "Server address of an SSH server. SSHFS is SFTP too, so any SSHFS server works. Log in with a password or a private key."),
    Smb("Windows / Samba share (SMB)", 445, false, "Computer name or address, and the name of the shared folder"),
    Ftp("FTP / FTPS", 21, false, "Server address of an FTP server"),
    Dropbox("Dropbox", 0, true, "Needs your own Dropbox app key"),
    GoogleDrive("Google Drive", 0, true, "Needs your own Google OAuth client ID"),
    OneDrive("OneDrive", 0, true, "Needs your own Microsoft app (client) ID");
}

/** A saved connection to a network or cloud storage. Secrets are kept encrypted on the phone. */
data class RemoteConnection(
    val id: String,
    val type: RemoteType,
    val name: String,
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val password: String = "",
    /** SMB: name of the share. WebDAV, Nextcloud, ownCloud, SFTP, FTP: the folder to start in. */
    val path: String = "",
    val tls: Boolean = true,
    /** SFTP: the private key text (optional), the password then protects the key */
    val privateKey: String = "",
    /** SFTP: the fingerprint of the server's key, saved the first time and checked afterwards */
    val fingerprint: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val refreshToken: String = "",
)

/** Saves the connections. Passwords, keys and tokens are encrypted with the Android Keystore. */
class ConnectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telos_connections", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<RemoteConnection> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { runCatching { fromJson(array.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun get(id: String): RemoteConnection? = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(connection: RemoteConnection) {
        val list = all().filter { it.id != connection.id } + connection
        write(list)
    }

    @Synchronized
    fun delete(id: String) {
        write(all().filter { it.id != id })
        RemoteRegistry.reset(id)
    }

    private fun write(list: List<RemoteConnection>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun toJson(c: RemoteConnection) = JSONObject()
        .put("id", c.id).put("type", c.type.name).put("name", c.name).put("host", c.host).put("port", c.port)
        .put("user", c.user).put("password", SecretBox.encrypt(c.password)).put("path", c.path).put("tls", c.tls)
        .put("key", SecretBox.encrypt(c.privateKey)).put("fingerprint", c.fingerprint)
        .put("clientId", c.clientId).put("clientSecret", SecretBox.encrypt(c.clientSecret))
        .put("refresh", SecretBox.encrypt(c.refreshToken))

    private fun fromJson(o: JSONObject) = RemoteConnection(
        id = o.getString("id"), type = RemoteType.valueOf(o.getString("type")), name = o.optString("name"),
        host = o.optString("host"), port = o.optInt("port"), user = o.optString("user"),
        password = SecretBox.decrypt(o.optString("password")), path = o.optString("path"), tls = o.optBoolean("tls", true),
        privateKey = SecretBox.decrypt(o.optString("key")), fingerprint = o.optString("fingerprint"),
        clientId = o.optString("clientId"), clientSecret = SecretBox.decrypt(o.optString("clientSecret")),
        refreshToken = SecretBox.decrypt(o.optString("refresh")),
    )
}

class RemoteEntry(val name: String, val path: String, val isDir: Boolean, val size: Long, val modified: Long, val permissions: String = "")

/** What a protocol (SFTP, SMB, a cloud API, ...) has to offer. Paths look like "/folder/file.txt". */
interface RemoteClient : Closeable {
    fun list(path: String): List<RemoteEntry>
    fun mkdir(path: String): Boolean
    fun delete(path: String): Boolean
    fun rename(from: String, to: String): Boolean
    fun openRead(path: String): InputStream
    /** Opens a file for writing, a new one or one that is overwritten. Closing the stream finishes the upload. */
    fun openWrite(path: String): OutputStream
    /** Copies on the server, or false when that is not possible (the data then goes through the phone) */
    fun copy(from: String, to: String): Boolean = false
}

/** The address of something on a remote storage: rem://<connection id>/<path> */
object RemotePath {
    private const val PREFIX = "rem://"
    fun isRemote(path: String) = path.startsWith(PREFIX)
    fun idOf(path: String) = path.removePrefix(PREFIX).substringBefore('/')
    fun innerOf(path: String): String = "/" + path.removePrefix(PREFIX).substringAfter('/', "").trim('/')
    fun build(id: String, inner: String) = PREFIX + id + "/" + inner.trim('/')
    fun isRoot(path: String) = isRemote(path) && innerOf(path) == "/"
}

/** Makes a remote storage look like the other file systems of Telos Files. */
class RemoteFs(private val id: String, private val context: Context) : Fs {
    override val isRoot = false
    override val isRemote = true

    private fun <T> run(block: (RemoteClient) -> T): T {
        val client = RemoteRegistry.client(context, id)
        return try {
            block(client)
        } catch (e: IOException) {
            // the connection may be stale: connect again once
            RemoteRegistry.reset(id)
            block(RemoteRegistry.client(context, id))
        }
    }

    private fun inner(path: String) = RemotePath.innerOf(path)

    override fun list(path: String): List<FsEntry> = run { c ->
        c.list(inner(path)).map {
            FsEntry(
                path = RemotePath.build(id, it.path), name = it.name, isDir = it.isDir,
                size = if (it.isDir) -1 else it.size, modified = it.modified, permissions = it.permissions,
            )
        }
    }

    override fun mkdir(parent: String, name: String) = run { it.mkdir(inner(joinPath(parent, name))) }
    override fun createFile(parent: String, name: String) = run { c -> c.openWrite(inner(joinPath(parent, name))).use { }; true }
    override fun rename(path: String, newName: String) = run {
        val from = inner(path)
        it.rename(from, from.substringBeforeLast('/', "").let { p -> "$p/$newName" })
    }
    override fun delete(path: String) = run { it.delete(inner(path)) }
    override fun copy(src: String, dstDir: String, newName: String): Boolean =
        RemotePath.isRemote(src) && RemotePath.idOf(src) == id && run { it.copy(inner(src), inner(joinPath(dstDir, newName))) }
    override fun move(src: String, dstDir: String, newName: String): Boolean =
        RemotePath.isRemote(src) && RemotePath.idOf(src) == id && run { it.rename(inner(src), inner(joinPath(dstDir, newName))) }
    override fun chmod(path: String, mode: String) = false
    override fun exists(path: String): Boolean {
        val i = inner(path)
        if (i == "/") return true
        val parent = i.substringBeforeLast('/', "").ifEmpty { "/" }
        return run { c -> c.list(parent).any { it.path.trimEnd('/') == i.trimEnd('/') } }
    }
    override fun totalSize(path: String): Long = -1
    override fun openRead(path: String): InputStream = run { it.openRead(inner(path)) }
    override fun openWrite(path: String): OutputStream = run { it.openWrite(inner(path)) }
}

/** Keeps one connection per saved storage open. */
object RemoteRegistry {
    private val clients = ConcurrentHashMap<String, RemoteClient>()

    fun client(context: Context, id: String): RemoteClient {
        clients[id]?.let { return it }
        val connection = ConnectionStore(context).get(id) ?: throw IOException("This connection does not exist any more")
        val created = ClientFactory.create(context, connection)
        return clients.putIfAbsent(id, created) ?: created
    }

    fun fs(context: Context, id: String): Fs = RemoteFs(id, context)

    fun reset(id: String) {
        clients.remove(id)?.let { runCatching { it.close() } }
    }
}
