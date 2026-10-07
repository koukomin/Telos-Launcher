# Telos Files internals

Telos Files (`:app:ui`, package `de.mm20.launcher2.ui.files`) hides every kind of storage behind one
small interface, so the screen, copy and paste work the same everywhere.

## The Fs interface

`Fs` (in `Fs.kt`) works on string paths. Main operations: `list`, `mkdir`, `createFile`, `rename`,
`delete`, `copy`, `move`, `chmod`, `exists`, `totalSize`, and optional `openRead` / `openWrite` streams
(default: unsupported).

| Implementation | Storage |
| --- | --- |
| `LocalFs` | The phone's file system, via `java.io.File` |
| `RootFs` | System files through a superuser shell (`RootShell`), quoted commands |
| `RemoteFs` | Network and cloud storages, backed by a `RemoteClient` |
| `ArchiveFs` | The inside of an archive file |
| `CryptomatorFs` | The inside of an unlocked Cryptomator vault (read only) |

## Path schemes

| Scheme | Form |
| --- | --- |
| plain path | `/storage/emulated/0/...` |
| `rem://` | `rem://<connection id>/<path>` (`RemotePath`) |
| `arc://` | `arc://<archive path, encoded>!/<path inside>` |
| `vlt://` | `vlt://<vault folder, encoded>!/<path inside>` |

## Remote clients

`RemoteClient` is what a protocol implements: `list`, `mkdir`, `delete`, `rename`, `openRead`,
`openWrite` and an optional server-side `copy` (returns false if not possible).
Implementations: WebDAV (also Nextcloud and ownCloud), SFTP (sshj), SMB (smbj), FTP/FTPS (Commons Net),
and `DropboxClient`, `OneDriveClient`, `GoogleDriveClient` via `CloudClient`. Connections are described by
`RemoteConnection` and saved by `ConnectionStore`.

## Copying across storages

`FsOps.copyAcross(src, srcPath, srcIsDir, dst, dstPath, cancel, progress)` copies between any two `Fs`
instances by streaming `openRead` into `openWrite`, recursing into directories, with a `CancelFlag` and a
progress callback.

## Secrets

Passwords, private keys, client secrets and refresh tokens in `RemoteConnection` are encrypted by
`ConnectionStore` before they are persisted. `SecretBox` (`:services:comms`) encrypts with AES/GCM using a
key held in the Android Keystore, which never leaves the device.

## OAuth for cloud storages

`OAuthProviders` describes Dropbox, Google Drive and OneDrive. Users register their own client ID. The
flow is authorization code with PKCE (`Pkce`: random verifier, SHA-256 challenge, state). A small
loopback HTTP server on port `53682` receives the redirect: `http://localhost:53682/` (Dropbox,
OneDrive) or `http://127.0.0.1:53682/` (Google, which also needs a client secret). `AccessTokens`
refreshes access tokens from the stored refresh token.

## Cryptomator

`CryptomatorFs` and `CryptomatorCrypto` read vault formats 7 and 8, implemented from the published format
description. Read only. Decrypted temporary files are removed when the vault is locked.

For user-facing behavior see [Telos Files](../../user-guide/telos/files/).
