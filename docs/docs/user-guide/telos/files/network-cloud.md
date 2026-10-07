# Network and cloud storages

Telos Files can browse, open, download, upload, rename, move, delete and copy files on servers and cloud
accounts exactly like on the phone. This page lists the protocols, the fields of every form, the per-provider
setup for **your own client ID** (Dropbox, Google Drive, OneDrive), and how the sign-in and the stored secrets work.

## Manage connections

**Settings > Integrations > Cloud and network storage**, or in Telos Files **drawer > Network and cloud > Add or
manage...**. The screen "Cloud and network storage" lists your connections; tap a row to edit it and the **+**
button to add one.

1. Tap **+** and choose a type: Nextcloud, ownCloud, WebDAV, SFTP / SSHFS (SSH), Windows / Samba share (SMB), FTP /
   FTPS, Dropbox, Google Drive or OneDrive.
2. Fill in the form. A hint under the title explains the type.
3. Tap **Test**. It connects with the form as it is and says "Connected, N items in the start folder" or "Could not
   connect: reason".
4. Tap **Save**. **Delete** (in an existing connection) removes it and forgets its secrets.

Saved connections appear on the Files home page and in the drawer, sorted by name. A saved connection is opened as
`rem://` followed by its id. Names are free text; the default is the type name.

## Protocols at a glance

| Protocol | Default port | Auth | Encryption | Server-side copy |
| --- | --- | --- | --- | --- |
| WebDAV | 443 | User and password (HTTP basic) | **Use https** switch, on by default | yes |
| Nextcloud | 443 | User and password | **Use https**, on | yes |
| ownCloud | 443 | User and password | **Use https**, on | yes |
| SFTP / SSHFS | 22 | Password, or private key with an optional passphrase | SSH, host key pinned on first use | no |
| SMB / CIFS | 445 | User and password, or guest | SMB protocol level | no |
| FTP / FTPS | 21 | User and password, or anonymous | **Encrypt with TLS (FTPS)**, on by default | no |
| Dropbox | n/a | OAuth with PKCE, your app key | HTTPS | yes |
| Google Drive | n/a | OAuth with PKCE, your client ID and secret | HTTPS | no |
| OneDrive | n/a | OAuth with PKCE, your client ID | HTTPS | no |

## Form fields

| Field | Used by | Meaning |
| --- | --- | --- |
| Name | all | The label in the drawer |
| Server | all but cloud | Host name or address. A pasted `https://` or `http://` prefix is removed |
| Port | all but cloud | Empty or 0 uses the default of the protocol |
| User name | all but cloud | For SMB use `DOMAIN\user` when a domain is needed. Empty means guest (SMB) or anonymous (FTP) |
| Password | all but cloud | Stored encrypted. With an SFTP key it is the key's passphrase |
| Folder on the server (optional) | WebDAV, Nextcloud, ownCloud | A sub folder to start in |
| Start folder (optional) | SFTP, FTP | Folder opened first |
| Shared folder name | SMB | **Required.** The share, for example `media`. Without it "Enter the name of the shared folder" |
| Use https | WebDAV, Nextcloud, ownCloud | Off means plain `http`, not encrypted |
| Encrypt with TLS (FTPS) | FTP | Explicit TLS with a protected data channel. Off means plain FTP |
| Private key | SFTP | Paste the key file text. Optional |
| Server key | SFTP | Read-only line showing the saved host key fingerprint |

### How the address is built

| Type | Request goes to |
| --- | --- |
| WebDAV | `https://server[:port]/folder` |
| Nextcloud | `https://server/remote.php/dav/files/USER/folder` |
| ownCloud | `https://server/remote.php/webdav/folder` |

For Nextcloud and ownCloud an **app password** (created in the web interface under Security) is recommended
instead of your main password, especially with two-factor authentication.

## Per-protocol notes

### WebDAV, Nextcloud, ownCloud

- Uses PROPFIND, MKCOL, MOVE and COPY. Rename and copy never overwrite (`Overwrite: F`).
- Works with any WebDAV server, including Synology, Box and Yandex.
- Uploads are first written to a temporary file in the cache and sent when the stream is closed.
- A 401 or 403 answer shows "Access denied. Check the user name and password."

### SFTP / SSHFS

- Telos saves the server's key fingerprint the first time you connect and compares it on every later connection. A
  changed key makes the connection fail, which protects against a man-in-the-middle.
- The fingerprint is shown in the form under **Server key**. **Test** in a brand new draft does not save it; save the
  connection and connect once to pin it.
- SSHFS servers are SFTP servers, so any SSHFS server works.
- Permissions are shown in the properties.

### SMB / CIFS

- Windows shares and NAS devices. Guest access when the user name is empty.
- A share name is mandatory; use the form `DOMAIN\user` for domain accounts.
- Only the SMB2 and SMB3 family is supported by the library; very old SMB1 servers do not work.

### FTP / FTPS

- Passive mode, binary transfers, UTF-8 names. Anonymous login with the user "anonymous" when the user is empty.
- FTPS uses explicit TLS and trusts the certificates Android trusts, so a self-signed certificate is rejected.
- Prefer SFTP or FTPS over plain FTP when you have a choice.

## Cloud storages

Dropbox, Google Drive and OneDrive sign in with **OAuth and PKCE** and need **your own client ID**. Telos does not
ship shared keys, so you register a small app with the provider once. The sign-in has no password field, you approve
access in your browser, and Telos only receives a refresh token.

### How the sign-in works

1. Fill in the client ID (and the client secret for Google Drive) and tap **Sign in**.
2. Telos starts a short-lived listener on `127.0.0.1`, port **53682**, then opens your browser.
3. Approve access. The provider redirects the browser back to the redirect address, and Telos picks up the code. It
   accepts only a request carrying the random `state` it generated.
4. The status line shows "Connected: signed in. Save the connection." **Save the connection**, otherwise the
   sign-in is lost.

The listener waits for **three minutes**. **Sign out** forgets the token; **Sign in again** renews it. The access
token is refreshed from the stored refresh token when it is about to expire.

| Provider | Field | Client secret | Redirect address to register | Scope |
| --- | --- | --- | --- | --- |
| Dropbox | App key | no | `http://localhost:53682/` | Files metadata and content, read and write |
| OneDrive | Application (client) ID | no | `http://localhost:53682/` | `Files.ReadWrite.All offline_access` |
| Google Drive | Client ID | yes | `http://127.0.0.1:53682/` | `https://www.googleapis.com/auth/drive` |

### Client ID setup

::: code-group
```text [Dropbox]
1. Open the Dropbox App Console and create an app: Scoped access, Full Dropbox.
2. Permissions tab: enable files.metadata.read, files.metadata.write,
   files.content.read and files.content.write, then submit.
3. Settings tab: add the redirect URI http://localhost:53682/
4. Copy the App key.
5. In Telos: add a Dropbox connection, paste the App key, tap Sign in,
   approve in the browser, then Save.
```
```text [Google Drive]
1. In the Google Cloud console create a project and enable the Google Drive API.
2. Configure the OAuth consent screen. While it is in testing mode add your
   own Google account as a test user.
3. Create an OAuth client ID of type Desktop app.
4. In Telos: add a Google Drive connection, paste the Client ID and the
   Client secret.
5. Redirect address used by Telos: http://127.0.0.1:53682/
   (a Desktop app client accepts loopback addresses without registration).
6. Tap Sign in, approve in the browser, then Save.
```
```text [OneDrive]
1. In the Azure portal register an app for personal and work accounts
   (Microsoft Entra ID > App registrations).
2. Authentication: add the platform Mobile and desktop applications with the
   redirect address http://localhost:53682/
3. Copy the Application (client) ID.
4. In Telos: add a OneDrive connection, paste the client ID, tap Sign in,
   approve in the browser, then Save.
```
:::

::: warning Google apps in testing mode
Google may expire refresh tokens of apps whose consent screen is in *testing* mode after about a week. If Telos says
"Not signed in. Open the connection and sign in." or a sign-in error, open the connection and sign in again.
:::

::: details Sign-in does not return to Telos?
Check that the redirect address matches exactly, including `localhost` versus `127.0.0.1`, the port and the trailing
slash. Keep Telos in the foreground while signing in and finish within three minutes. Another app already using
port 53682 blocks the listener.
:::

### Cloud details

| Provider | Upload | Delete | Server-side copy |
| --- | --- | --- | --- |
| Dropbox | Single request up to 100 MB, an upload session in 8 MB pieces above that | Dropbox delete | yes |
| Google Drive | Through the Drive API. Lists items that are not in the trash | A direct Drive delete, which does **not** use the trash | no |
| OneDrive | Single request up to 4 MB, otherwise an upload session in pieces of 3.2 MB | OneDrive delete | no |

Uploads are first written to a temporary file in the cache, so a large upload needs that much free space.

## Working with remote files

- Browse, open, download, upload (paste into the folder), rename, move, delete and copy between any two storages
  (local, network, cloud). See [Browsing](./browsing#copy-cut-and-paste).
- Opened remote files are downloaded to the cache first, with a progress bar and **Cancel**. They are removed when
  you lock a vault; otherwise the cache is cleared by Android.
- When a server cannot copy by itself, the data goes through the phone.
- A stale connection is reconnected once automatically.
- Remote folders show no thumbnails and size shows as unknown for folders.

## Security of stored secrets

Passwords, private keys, client secrets and refresh tokens are encrypted with a Keystore key before they are saved.
The saved connections are private to Telos. They are not part of the launcher's settings backup, so after a
reinstall you add them again.

| Item | Stored as |
| --- | --- |
| Name, host, port, user, folder, client ID, SFTP fingerprint | Plain text in the private preferences `telos_connections` |
| Password, private key, client secret, refresh token | Encrypted with the Keystore |

## Limitations

- Mega, iCloud, gocryptfs, EncFS and VeraCrypt are not supported (see [Telos Files](./#not-supported)).
- No shared client IDs: every user registers an app.
- WebDAV, FTP and SFTP use certificate or key checks as described above; there is no option to accept an unknown TLS
  certificate.
- Remote search only filters the current folder.
- Google Drive delete is permanent.
- Timeouts: 20 seconds to connect, 60 to 90 seconds for transfers.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "Could not connect: ..." | Check server, port and that the phone can reach it. Test with a browser or another client |
| "Access denied. Check the user name and password." | Wrong credentials, or an app password is needed |
| SFTP says the host key changed | The server was reinstalled, or someone is intercepting. Delete the connection and add it again only if you trust the change |
| "Enter the name of the shared folder" | SMB needs the share name in **Shared folder name** |
| "Not signed in. Open the connection and sign in." | The refresh token is gone. Sign in again and Save |
| "Sign-in failed" or "Signing in took too long" | Redirect address mismatch, or you took more than three minutes |
| "The service did not give a refresh token" | Remove the app's earlier grant in the provider's account settings and sign in again |
| Cloud upload fails for a big file | Free space in the cache is too small, or the network dropped |
