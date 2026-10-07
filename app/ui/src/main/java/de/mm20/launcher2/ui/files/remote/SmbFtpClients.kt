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

    @Synchronized
    private fun share(): DiskShare {
        share?.let { if (smb?.isConnected == true) return it }
        close()
        if (connection.path.isBlank()) throw IOException("Enter the name of the shared folder")
        try {
            val c = SMBClient()
            val conn = c.connect(connection.host, if (connection.port > 0) connection.port else 445)
            val user = connection.user.substringAfter('\\')
            val domain = if (connection.user.contains('\\')) connection.user.substringBefore('\\') else ""
            val context = if (user.isEmpty()) AuthenticationContext.guest() else AuthenticationContext(user, connection.password.toCharArray(), domain)
            val s = conn.authenticate(context)
            val sh = s.connectShare(connection.path.trim('/', '\\')) as DiskShare
            client = c; smb = conn; session = s; share = sh
            return sh
        } catch (e: Exception) {
            close()
            throw IOException(e.message ?: "Could not connect", e)
        }
    }

    private fun p(path: String) = path.trim('/').replace('/', '\\')

    override fun list(path: String): List<RemoteEntry> = share().list(p(path))
        .filter { it.fileName != "." && it.fileName != ".." }
        .map {
            val dir = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            RemoteEntry(it.fileName, ("/" + p(path).replace('\\', '/') + "/" + it.fileName).replace("//", "/"), dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
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
        share = null; session = null; smb = null; client = null
    }
}

/** FTP, and FTPS (FTP over TLS) when the connection asks for TLS. */
class FtpRemoteClient(private val connection: RemoteConnection) : RemoteClient {
    private var shared: FTPClient? = null

    private fun connect(): FTPClient {
        val c: FTPClient = if (connection.tls) FTPSClient("TLS", false) else FTPClient()
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
            throw if (e is IOException) e else IOException(e.message, e)
        }
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
            RemoteEntry(it.name, ("/" + path.trim('/') + "/" + it.name).replace("//", "/"), it.isDirectory, it.size, it.timestamp?.timeInMillis ?: 0)
        }
    }

    override fun mkdir(path: String) = withClient { it.makeDirectory(abs(path)) }

    override fun delete(path: String): Boolean = withClient { c ->
        val target = abs(path)
        val children = c.listFiles(target)
        if (children != null && children.isNotEmpty() || c.changeWorkingDirectory(target)) {
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
