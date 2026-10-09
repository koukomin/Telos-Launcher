package de.mm20.launcher2.ui.files.remote

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.security.Security
import java.util.EnumSet

/** Android's own "BC" security provider is a cut-down one, SSH needs the complete one. */
internal object SshSupport {
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        installed = true
    }
}

/** SFTP over SSH. The server's key is remembered the first time and has to be the same afterwards. */
class SftpRemoteClient(private val connection: RemoteConnection, private val onFingerprint: (String) -> Unit) : RemoteClient {
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null
    /** The key trusted so far: remembered on the first connect, so a reconnect cannot accept a different one */
    @Volatile private var pinned: String = connection.fingerprint

    @Synchronized
    private fun client(): SFTPClient {
        sftp?.let { if (ssh?.isConnected == true) return it }
        SshSupport.install()
        val c = SSHClient()
        c.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = SecurityUtils.getFingerprint(key)
                if (pinned.isEmpty()) { pinned = fingerprint; onFingerprint(fingerprint); return true }
                return pinned == fingerprint
            }
            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })
        c.connectTimeout = 20_000
        c.timeout = 60_000
        try {
            c.connect(connection.host, if (connection.port > 0) connection.port else 22)
            if (connection.privateKey.isNotBlank()) {
                val passphrase = if (connection.password.isEmpty()) null else PasswordUtils.createOneOff(connection.password.toCharArray())
                c.authPublickey(connection.user, c.loadKeys(connection.privateKey, null, passphrase))
            } else {
                c.authPassword(connection.user, connection.password)
            }
        } catch (e: Exception) {
            runCatching { c.close() }
            throw IOException(e.message ?: "Could not connect", e)
        }
        ssh = c
        return c.newSFTPClient().also { sftp = it }
    }

    private fun mask(m: Int): String = buildString {
        for (shift in intArrayOf(6, 3, 0)) {
            val bits = (m shr shift) and 7
            append(if (bits and 4 != 0) 'r' else '-').append(if (bits and 2 != 0) 'w' else '-').append(if (bits and 1 != 0) 'x' else '-')
        }
    }

    override fun list(path: String): List<RemoteEntry> = client().ls(path.ifEmpty { "/" })
        .filter { it.name != "." && it.name != ".." }
        .map {
            RemoteEntry(
                name = it.name, path = it.path, isDir = it.isDirectory, size = it.attributes.size,
                modified = it.attributes.mtime * 1000, permissions = mask(it.attributes.mode.permissionsMask),
            )
        }

    override fun mkdir(path: String) = runCatching { client().mkdir(path); true }.getOrDefault(false)

    override fun delete(path: String): Boolean = runCatching {
        val c = client()
        if (c.stat(path).type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
            c.ls(path).filter { it.name != "." && it.name != ".." }.forEach { delete(it.path) }
            c.rmdir(path)
        } else c.rm(path)
        true
    }.getOrDefault(false)

    override fun rename(from: String, to: String) = runCatching { client().rename(from, to); true }.getOrDefault(false)

    override fun openRead(path: String): InputStream {
        val file: RemoteFile = client().open(path)
        val stream = file.RemoteFileInputStream()
        return object : FilterInputStream(stream) {
            override fun close() { runCatching { super.close() }; runCatching { file.close() } }
        }
    }

    override fun openWrite(path: String): OutputStream {
        val file: RemoteFile = client().open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        val stream = file.RemoteFileOutputStream()
        return object : FilterOutputStream(stream) {
            override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
            override fun close() { runCatching { super.close() }; runCatching { file.close() } }
        }
    }

    @Synchronized
    override fun close() {
        runCatching { sftp?.close() }
        runCatching { ssh?.disconnect() }
        sftp = null
        ssh = null
    }
}
