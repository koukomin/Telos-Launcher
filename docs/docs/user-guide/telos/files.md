# Telos Files

A file manager for your phone, for network storages and for cloud storages. Its layout takes inspiration
from Solid Explorer and MiXplorer (no code was copied).

## Browsing

| Feature | Details |
| --- | --- |
| Storage overview | Free space, SD cards and USB drives |
| Quick access | Standard folders |
| Path bar | Breadcrumbs |
| Views | List and grid, picture thumbnails and type colors |
| Sorting | Several orders, optional hidden files |
| Search | Search inside a folder |
| Favorites | Pin folders |

## File operations

| Operation | Notes |
| --- | --- |
| Copy, move, paste | With progress and cancel |
| Rename, delete | Multi-select supported |
| New folder, new file | |
| Zip, unzip | Compress and extract |
| Share | System share sheet |
| Properties | Permissions and MD5, SHA-1 and SHA-256 checksums |

Pictures open in [Telos Photos](./photos) and videos in [Telos Video](./video).

## Root explorer <Badge type="warning" text="optional" />

An optional superuser mode for system folders.

::: danger Be careful
Changing system files can make your phone stop working. Telos shows a warning before the root explorer
is switched on, a warning banner in system folders, and asks for extra confirmation when you change
files there, change permissions or make `/system` writable. Only use it if you know what you are doing.
:::

Root features are <Badge type="info" text="untested" /> on devices.

## Network storages

| Protocol | Notes |
| --- | --- |
| WebDAV | Any WebDAV server (also Synology, Box, Yandex) |
| Nextcloud | Server address and login |
| ownCloud | Server address and login |
| SFTP / SSHFS | Over SSH, password or private key, host key check on first use |
| SMB / CIFS | Windows shares and NAS, computer name and share name |
| FTP / FTPS | Plain or encrypted |

Manage connections in **Settings > Integrations > Cloud and network storage**. SFTP saves the server's
key fingerprint on first use and checks it afterwards.

## Cloud storages

Dropbox, Google Drive and OneDrive are supported. Sign-in uses OAuth with PKCE and **your own client
ID**, because Telos does not ship shared keys. You register a small app with the provider once.

::: tip How it works
While you sign in, a tiny server on the phone listens on port **53682** to receive the answer. The
redirect address must match exactly what you register.
:::

| Provider | Client ID | Client secret | Redirect address |
| --- | --- | --- | --- |
| Dropbox | App key | no | `http://localhost:53682/` |
| OneDrive | Application (client) ID | no | `http://localhost:53682/` |
| Google Drive | OAuth client ID | yes | `http://127.0.0.1:53682/` |

### Dropbox

1. Open the Dropbox App Console and create an app: **Scoped access**, **Full Dropbox**.
2. In Permissions enable `files.metadata.read`, `files.metadata.write`, `files.content.read` and `files.content.write`.
3. In Settings add the redirect URI `http://localhost:53682/`.
4. Copy the **app key** into Telos Files and sign in.

### Google Drive

1. In the Google Cloud console create a project and enable the **Google Drive API**.
2. Create an **OAuth client ID** of type **Desktop app**.
3. Copy the client ID and the **client secret** into Telos Files.
4. The redirect address used is `http://127.0.0.1:53682/`.
5. If the consent screen is in testing mode, add your account as a test user.

### OneDrive

1. In the Azure portal register an app that supports personal and work accounts.
2. Add a **Mobile and desktop** platform with the redirect address `http://localhost:53682/`.
3. Copy the **application (client) ID** into Telos Files and sign in.

::: details Sign-in does not return to Telos?
Check that the redirect address matches exactly, including `localhost` versus `127.0.0.1`, the port and
the trailing slash. Keep Telos in the foreground while signing in.
:::

### Working with remote files

Browse, open, download, upload, rename, move, delete, and copy between any two storages (local, network, cloud).
When a server cannot copy by itself, the data goes through the phone.

## Archives

zip, jar, apk, 7z, tar (also gz, bz2, xz), epub and Office files open like folders. Use "extract here"
to unpack.

## Cryptomator vaults <Badge type="warning" text="experimental" />

[Cryptomator](https://cryptomator.org) vaults (format 7 and 8) can be unlocked and **read**.

::: warning
- Read only: you cannot add or change files in a vault.
- Experimental. Do not rely on it as your only way to reach important data.
- Files you open from a vault are decrypted temporarily and removed when the vault is locked.
:::

## Security of stored secrets

Passwords, private keys and tokens are stored encrypted with a key held in the Android Keystore, which
does not leave the device.

## Not supported

| Not supported | Reason |
| --- | --- |
| Mega | Its own encryption protocol is not implemented |
| iCloud | Apple has no public API |
| gocryptfs, EncFS, VeraCrypt | Not implemented |
| Writing to Cryptomator vaults | Read only |
| Using shared client IDs for cloud | You need your own |

## Tips

- Use a cloud-to-cloud copy to move data between providers without a computer.
- Prefer SFTP over FTP when you have a choice.
