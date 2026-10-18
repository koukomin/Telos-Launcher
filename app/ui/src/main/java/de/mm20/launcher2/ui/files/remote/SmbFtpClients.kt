package de.mm20.launcher2.ui.files.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/** A shared folder of a Windows computer, a NAS or a Samba server. */
class SmbRemoteClient(private val connection: RemoteConnection) : RemoteClient {
    private var client: SMBClient? = null
    private var smb: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var boundShare: String? = null

    @Synchronized
    private fun share(): DiskShare {
        share?.let { if (smb?.isConnected == true) return it }
        close()
        val (host, urlShare) = SmbLan.parseHost(connection.host)
        val shareName = connection.path.ifBlank { urlShare }.trim('/', '\\')
        if (shareName.isBlank()) throw IOException("Enter the name of the shared folder, or leave it empty to list shares")
        try {
            val c = SMBClient()
            val conn = c.connect(host, if (connection.port > 0) connection.port else 445)
            val user = connection.user.substringAfter('\\')
            val domain = if (connection.user.contains('\\')) connection.user.substringBefore('\\') else ""
            val context = if (user.isEmpty()) AuthenticationContext.guest() else AuthenticationContext(user, connection.password.toCharArray(), domain)
            val s = conn.authenticate(context)
            val sh = s.connectShare(shareName) as DiskShare
            client = c; smb = conn; session = s; share = sh; boundShare = shareName
            return sh
        } catch (e: Exception) {
            close()
            throw IOException(e.message ?: "Could not connect", e)
        }
    }

    private fun p(path: String) = path.trim('/').replace('/', '\\')

    private fun listShares(): List<RemoteEntry> {
        val (host, _) = SmbLan.parseHost(connection.host)
        val c = SMBClient()
        val conn = c.connect(host, if (connection.port > 0) connection.port else 445)
        val user = connection.user.substringAfter('\\')
        val domain = if (connection.user.contains('\\')) connection.user.substringBefore('\\') else ""
        val context = if (user.isEmpty()) AuthenticationContext.guest() else AuthenticationContext(user, connection.password.toCharArray(), domain)
        val s = conn.authenticate(context)
        val names = LinkedHashSet<String>()
        runCatching {
            val ipc = s.connectShare("IPC$")
            ipc.close()
        }
        listOf("share", "public", "Users", "homes", "media", "photos", "backup", "data", "files", "Documents", "Music", "Video", "IPC$").forEach { n ->
            runCatching {
                val sh = s.connectShare(n)
                if (sh is DiskShare) names += n
                runCatching { sh.close() }
            }
        }
        runCatching { s.close() }; runCatching { conn.close() }; runCatching { c.close() }
        if (names.isEmpty()) throw IOException("Enter the name of the shared folder")
        return names.filter { it != "IPC$" }.map { RemoteEntry(it, "/$it", true, -1, 0) }
    }

    override fun list(path: String): List<RemoteEntry> {
        val (_, urlShare) = SmbLan.parseHost(connection.host)
        if (connection.path.isBlank() && urlShare.isBlank()) {
            if (path.trim('/').isEmpty()) return listShares()
            val shareName = path.trim('/').substringBefore('/')
            val rest = path.trim('/').substringAfter('/', "")
            val sh = connectShare(shareName)
            return sh.list(p(rest))
                .filter { it.fileName != "." && it.fileName != ".." }
                .map {
                    val dir = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    RemoteEntry(it.fileName, ("/" + path.trim('/') + "/" + it.fileName).replace("//", "/"), dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
                }
        }
        return share().list(p(path))
        .filter { it.fileName != "." && it.fileName != ".." }
        .map {
            val dir = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            RemoteEntry(it.fileName, ("/" + p(path).replace('\\', '/') + "/" + it.fileName).replace("//", "/"), dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
        }
    }

    @Synchronized
    private fun connectShare(name: String): DiskShare {
        share?.let { if (smb?.isConnected == true && boundShare == name) return it }
        close()
        val (host, _) = SmbLan.parseHost(connection.host)
        val c = SMBClient()
        val conn = c.connect(host, if (connection.port > 0) connection.port else 445)
        val user = connection.user.substringAfter('\\')
        val domain = if (connection.user.contains('\\')) connection.user.substringBefore('\\') else ""
        val context = if (user.isEmpty()) AuthenticationContext.guest() else AuthenticationContext(user, connection.password.toCharArray(), domain)
        val s = conn.authenticate(context)
        val sh = s.connectShare(name) as DiskShare
        client = c; smb = conn; session = s; share = sh; boundShare = name
        return sh
    }

    override fun mkdir(path: String) = runCatching { share().mkdir(p(path)); true }.getOrDefault(false)

    override fun delete(path: String) = runCatching {
        val s = share()
        if (s.folderExists(p(path))) s.rmdir(p(path), true) else s.rm(p(path))
        true
    }.getOrDefault(false)

    override fun rename(from: String, to: String) = runCatching {
        val s = share()
        val access = EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE)
        val dir = s.folderExists(p(from))
        if (dir) s.openDirectory(p(from), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { it.rename(p(to)) }
        else s.openFile(p(from), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { it.rename(p(to)) }
        true
    }.getOrDefault(false)

    override fun openRead(path: String): InputStream {
        val file = share().openFile(p(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        return object : FilterInputStream(file.inputStream) {
            override fun close() { runCatching { super.close() }; runCatching { file.close() } }
        }
    }

    override fun openWrite(path: String): OutputStream {
        val file = share().openFile(
            p(path), EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null,
        )
        return object : FilterOutputStream(file.outputStream) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() { runCatching { super.close() }; runCatching { file.close() } }
        }
    }

    @Synchronized
    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { smb?.close() }
        runCatching { client?.close() }
        share = null; session = null; smb = null; client = null; boundShare = null
    }

    override fun space(): RemoteSpace? = runCatching {
        val s = share()
        val method = s.javaClass.methods.firstOrNull { it.name.equals("getShareInformation", true) } ?: return null
        val info = method.invoke(s) ?: return null
        val total = info.javaClass.methods.firstOrNull { it.name.contains("Total", true) && it.parameterCount == 0 }?.invoke(info) as? Number
        val free = info.javaClass.methods.firstOrNull { it.name.contains("Free", true) && it.parameterCount == 0 }?.invoke(info) as? Number
        if (total == null || free == null) null else RemoteSpace(total.toLong(), free.toLong())
    }.getOrNull()
}

/** FTP, and FTPS (FTP over TLS) when the connection asks for TLS. */
class FtpRemoteClient(private val connection: RemoteConnection, private val hostMismatchMessage: String = "") : RemoteClient {
    private var shared: FTPClient? = null

    private fun connect(): FTPClient {
        val c: FTPClient = if (connection.tls) FTPSClient("TLS", false) else FTPClient()
        if (c is FTPSClient) {
            // the certificate has to be issued for this host name, unless the connection says otherwise
            c.isEndpointCheckingEnabled = !connection.acceptAnyHost
        }
        c.connectTimeout = 20_000
        c.defaultTimeout = 60_000
        c.controlEncoding = "UTF-8"
        try {
            c.connect(connection.host, if (connection.port > 0) connection.port else 21)
            val user = connection.user.ifEmpty { "anonymous" }
            val password = if (connection.user.isEmpty()) "anonymous@" else connection.password
            if (!c.login(user, password)) throw IOException("Access denied. Check the user name and password.")
            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)
            if (c is FTPSClient) { c.execPBSZ(0); c.execPROT("P") }
            return c
        } catch (e: Exception) {
            runCatching { c.disconnect() }
            if (c is FTPSClient && !connection.acceptAnyHost && hostMismatchMessage.isNotEmpty() && isHostMismatch(e)) {
                throw IOException(hostMismatchMessage, e)
            }
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    /** True when TLS failed because the certificate does not belong to the host name (and not for another reason) */
    private fun isHostMismatch(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 8) {
            if (t is javax.net.ssl.SSLPeerUnverifiedException) return true
            val m = t.message.orEmpty().lowercase()
            if ((t is java.security.cert.CertificateException || t is javax.net.ssl.SSLHandshakeException) &&
                (m.contains("subject alternative") || m.contains("hostname") || m.contains("host name") || m.contains("not verified") || m.contains("identity"))
            ) return true
            t = t.cause
        }
        return false
    }

    @Synchronized
    private fun <T> withClient(block: (FTPClient) -> T): T {
        val c = shared?.takeIf { it.isConnected } ?: connect().also { shared = it }
        return try { block(c) } catch (e: IOException) { shared = null; runCatching { c.disconnect() }; throw e }
    }

    private fun abs(path: String) = if (connection.path.isBlank()) path else (connection.path.trimEnd('/') + "/" + path.trimStart('/'))

    override fun list(path: String): List<RemoteEntry> = withClient { c ->
        val dir = abs(path)
        (c.listFiles(dir) ?: emptyArray()).filter { it.name != "." && it.name != ".." }.map {
            RemoteEntry(it.name, ("/" + path.trim('/') + "/" + it.name).replace("//", "/"), it.isDirectory, it.size, it.timestamp?.timeInMillis ?: 0, isLink = it.isSymbolicLink)
        }
    }

    override fun mkdir(path: String) = withClient { it.makeDirectory(abs(path)) }

    override fun delete(path: String): Boolean = withClient { c ->
        val target = abs(path)
        val children = c.listFiles(target)
        // a plain file can list as itself, so only a folder that can be entered counts as one
        if (c.changeWorkingDirectory(target)) {
            c.changeWorkingDirectory("/")
            children?.filter { it.name != "." && it.name != ".." }?.forEach { delete(path.trimEnd('/') + "/" + it.name) }
            c.removeDirectory(target)
        } else c.deleteFile(target)
    }

    override fun rename(from: String, to: String) = withClient { it.rename(abs(from), abs(to)) }

    override fun openRead(path: String): InputStream {
        val c = connect()
        val stream = c.retrieveFileStream(abs(path)) ?: run { c.disconnect(); throw IOException("Could not open the file") }
        return object : FilterInputStream(stream) {
            override fun close() { runCatching { super.close() }; runCatching { c.completePendingCommand() }; runCatching { c.disconnect() } }
        }
    }

    override fun openWrite(path: String): OutputStream {
        val c = connect()
        val stream = c.storeFileStream(abs(path)) ?: run { c.disconnect(); throw IOException("Could not create the file") }
        return object : FilterOutputStream(stream) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() { runCatching { super.close() }; runCatching { c.completePendingCommand() }; runCatching { c.disconnect() } }
        }
    }

    @Synchronized
    override fun close() { runCatching { shared?.disconnect() }; shared = null }
}
