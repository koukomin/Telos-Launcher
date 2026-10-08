package de.mm20.launcher2.ui.files.remote

import android.content.Context
import java.io.IOException

/** Creates the client for a saved connection. */
object ClientFactory {
    private fun tokens(context: Context, c: RemoteConnection) =
        AccessTokens(c) { updated -> ConnectionStore(context).save(updated) }

    fun create(context: Context, c: RemoteConnection): RemoteClient {
        val scheme = if (c.tls) "https" else "http"
        val authority = c.host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/') +
            if (c.port > 0 && c.port != c.type.defaultPort) ":${c.port}" else ""
        val sub = "/" + c.path.trim('/').let { if (it.isEmpty()) "" else "$it" }
        return when (c.type) {
            RemoteType.WebDav -> WebDavClient("$scheme://$authority${sub.trimEnd('/')}", c.user, c.password, context.cacheDir)
            RemoteType.Nextcloud -> WebDavClient(
                "$scheme://$authority/remote.php/dav/files/${android.net.Uri.encode(c.user)}${sub.trimEnd('/')}", c.user, c.password, context.cacheDir,
            )
            RemoteType.Owncloud -> WebDavClient("$scheme://$authority/remote.php/webdav${sub.trimEnd('/')}", c.user, c.password, context.cacheDir)
            RemoteType.Sftp -> SftpRemoteClient(c) { fingerprint -> ConnectionStore(context).save(c.copy(fingerprint = fingerprint)) }
            RemoteType.Smb -> SmbRemoteClient(c)
            RemoteType.Ftp -> FtpRemoteClient(c)
            RemoteType.System -> SafClient(context, c.host)
            RemoteType.Dropbox -> DropboxClient(tokens(context, c), context.cacheDir)
            RemoteType.GoogleDrive -> GoogleDriveClient(tokens(context, c), context.cacheDir)
            RemoteType.OneDrive -> OneDriveClient(tokens(context, c), context.cacheDir)
        }
    }
}
