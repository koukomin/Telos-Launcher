# Telos Files

A file manager for your phone, for network storages and for cloud storages. Its layout takes inspiration
from Solid Explorer and MiXplorer (no code was copied).

::: info At a glance
<Badge type="tip" text="local storage" /> <Badge type="tip" text="WebDAV, Nextcloud, ownCloud" />
<Badge type="tip" text="SFTP, SMB, FTP" /> <Badge type="tip" text="Dropbox, Google Drive, OneDrive" />
<Badge type="warning" text="Cryptomator (read only, experimental)" /> <Badge type="warning" text="root explorer (optional)" />
:::

## Browsing

| Feature | Details |
| --- | --- |
| Storage overview | Drawer with each volume, free space out of total, SD cards and USB drives |
| Favorites | Add or remove any folder from the **More** menu |
| Network and cloud | Your saved connections are listed in the same drawer |
| Views | List or grid, switch from the **More** menu |
| Sorting | By name, date, size or type, ascending or descending, folders first |
| Hidden files | Shown or hidden from the **More** menu |
| Search | Search inside the current folder (magnifier button) |
| Selection | Long press to select, "select all", then act on many files at once |

## File operations

| Operation | Notes |
| --- | --- |
| Copy, move, paste | Shows progress and can be cancelled |
| Rename, delete | Work on a selection |
| New folder, new file | From the add menu |
| Compress to zip, extract here | See [Archives](#archives) |
| Share | System share sheet. Folders cannot be shared, compress them first |
| Open with | Choose another app |
| Properties | Size, dates, permissions and on-demand **MD5, SHA-1 and SHA-256** checksums |

Pictures and documents open in [Telos Photos](./photos) and videos in [Telos Video](./video). A video
opened from a folder is queued together with the other videos next to it.

## Root explorer <Badge type="warning" text="optional" />

An optional superuser mode for system folders. Enable it from **More > Root explorer**.

::: danger Be careful
Changing system files can make your phone stop working. Telos shows a warning and asks you to confirm
("I understand the risks") before it requests superuser access, shows a warning in system locations, and
adds a confirmation when you change permissions. Only use it if you know what you are doing.
:::

Your root manager (Magisk, KernelSU and similar) asks you to allow Telos first. Root access ends when you
turn the root explorer off. Root features are <Badge type="info" text="untested" /> on many devices.

## Network storages

Open **Settings > Integrations > Cloud and network storage**, tap add, pick a type and fill in the form.
Use **Test** to check the connection before you **Save**.

| Protocol | What you enter | Notes |
| --- | --- | --- |
| WebDAV | Server, port, user, password, optional folder | Any WebDAV server, also Synology, Box, Yandex. "Use https" switch |
| Nextcloud | Server, user, password | Uses the Nextcloud files endpoint. An app password is recommended |
| ownCloud | Server, user, password | |
| SFTP / SSHFS | Server, port 22, user, password or pasted private key | The key can be protected by the password. SSHFS servers are SFTP servers |
| SMB / CIFS | Computer name, shared folder name, user, password | Windows shares and NAS. Empty user means guest |
| FTP / FTPS | Server, port 21, user, password | "Encrypt with TLS (FTPS)" switch. Empty user means anonymous |

::: tip Host key check
SFTP saves the server's key fingerprint the first time you connect and compares it on every later
connection. The fingerprint is shown in the connection form.
:::

## Cloud storages

Dropbox, Google Drive and OneDrive sign in with OAuth and PKCE and need **your own client ID**: Telos
does not ship shared keys, so you register a small app with the provider once. The sign-in has no
password field. You approve access in your browser.

### How the sign-in works

1. Fill in the client ID (and the client secret for Google Drive) and tap **Sign in**.
2. Telos starts a short-lived listener on `127.0.0.1`, port **53682**, then opens your browser.
3. Approve access. The provider redirects the browser back to the redirect address, and Telos picks up the code.
4. The status line shows "Connected: signed in". **Save the connection**, otherwise the sign-in is lost.

The listener waits for three minutes. Use **Sign out** to forget the token and **Sign in again** to renew it.

| Provider | Field | Client secret | Redirect address to register |
| --- | --- | --- | --- |
| Dropbox | App key | no | `http://localhost:53682/` |
| OneDrive | Application (client) ID | no | `http://localhost:53682/` |
| Google Drive | Client ID | yes | `http://127.0.0.1:53682/` |

::: code-group
```text [Dropbox]
1. Dropbox App Console: create an app, Scoped access, Full Dropbox.
2. Permissions: files.metadata.read, files.metadata.write,
   files.content.read, files.content.write.
3. Settings: add the redirect URI http://localhost:53682/
4. Paste the app key into Telos and sign in.
```
```text [Google Drive]
1. Google Cloud console: create a project, enable the Google Drive API.
2. Create an OAuth client ID (type Desktop app).
3. Paste the client ID and the client secret into Telos.
4. Redirect address used: http://127.0.0.1:53682/
5. If the consent screen is in testing mode, add your account as a test user.
```
```text [OneDrive]
1. Azure portal: register an app for personal and work accounts.
2. Add a "Mobile and desktop" platform with the redirect
   address http://localhost:53682/
3. Paste the application (client) ID into Telos and sign in.
```
:::

::: details Sign-in does not return to Telos?
Check that the redirect address matches exactly, including `localhost` versus `127.0.0.1`, the port and
the trailing slash. Keep Telos in the foreground while signing in and finish within three minutes.
:::

### Working with remote files

Browse, open, download, upload, rename, move, delete and copy between any two storages (local, network,
cloud). Opened remote files are downloaded to a temporary cache first. When a server cannot copy by
itself, the data goes through the phone.

## Archives

zip, jar, apk, 7z, tar (also tar.gz, tgz, tar.bz2, tar.xz), epub and Office/OpenDocument files (docx,
xlsx, pptx, odt, ods) open like folders. Tapping one asks whether to look inside or to unpack it next to
the file. **Compress to zip** creates zip archives.

## Cryptomator vaults <Badge type="warning" text="experimental" />

[Cryptomator](https://cryptomator.org) vaults (format 7 and 8) in a folder on the phone's storage can be
unlocked and **read**. A folder counts as a vault when it contains `masterkey.cryptomator` and a `d` folder.

1. Tap the vault folder. An **Unlock vault** dialog asks for the password.
2. Browse and open files like in any folder. A wrong password shows "Wrong password".
3. When you are done, open **More > Lock vault**.

::: warning
- Read only: you cannot add or change files in a vault.
- The password is only used to unlock the vault and is not stored. The key stays in memory and is gone
  when Telos is closed by the system.
- Files you open from a vault are decrypted into a temporary cache. **Lock vault** removes the decrypted
  files and returns to the parent folder.
- Experimental. Do not rely on it as your only way to reach important data.
:::

## Security of stored secrets

Passwords, private keys and tokens of saved connections are encrypted with a key held in the Android
Keystore, which does not leave the device.

## Not supported

| Not supported | Reason |
| --- | --- |
| Mega | Its own encryption protocol is not implemented |
| iCloud | Apple has no public API |
| gocryptfs, EncFS, VeraCrypt | Not implemented |
| Writing to Cryptomator vaults | Read only |
| Shared client IDs for cloud | You need your own |

## Tips

- Use a cloud-to-cloud copy to move data between providers without a computer.
- Prefer SFTP or FTPS over plain FTP when you have a choice.
