# Archives and Cryptomator vaults

Archives open like folders in Telos Files, and you can pack folders into zip files or unpack archives next to
themselves. [Cryptomator](https://cryptomator.org) vaults on the phone's storage can be unlocked and **read**.

## Archives

### What opens

| Group | Extensions | Notes |
| --- | --- | --- |
| zip family | `zip jar apk` | Read through a random-access index |
| 7-Zip | `7z` | Entries are read in order |
| tar family | `tar tar.gz tgz tar.bz2 tar.xz` | Entries are read in order, the compression is handled for you |
| zip-based documents | `epub docx xlsx pptx odt ods` | Offered like archives. See the note on documents below |

`rar`, `iso`, `cab`, `gz`, `bz2` and `xz` on their own are shown with an archive icon but are **not** opened inside
Telos. Use **Open with...** for them.

### Open an archive

1. Tap the archive file on the phone's storage (not inside a remote storage or another archive).
2. The dialog asks "Look inside the archive, or unpack it into a folder next to it?" with **Browse**, **Extract
   here** and **Open with...**.
3. **Browse** shows the contents as a read-only folder. The breadcrumb starts with the archive name, and Back from the
   top of the archive returns to its folder.

Inside an archive:

| Action | Behavior |
| --- | --- |
| Open a file | The file is extracted into the cache and opened with the right app, like a remote file |
| Copy | Select entries, **Copy**, open a local folder and paste. The data is streamed out of the archive |
| Cut, rename, delete, new folder, new file, compress | Not possible. Archives are read only |
| Properties | Size and date of the entry |
| Search | Not supported inside an archive |

Folders that the archive does not list explicitly are created in the view when a file lies below them. Entries whose
path contains `..` are ignored, so an archive can never reach outside itself.

::: tip Documents inside the archive dialog
A `docx`, `xlsx`, `pptx`, `odt`, `ods` or `epub` file asks the archive question first. Pick **Open with...** and
choose Telos Viewer to read it as a document, see [Photos documents](../photos/documents).
:::

### Zip and extract

| Task | Steps | Result |
| --- | --- | --- |
| Compress to zip | Select one or more entries, More > **Compress to zip**, confirm or edit the name | A `.zip` in the current folder, with a free name if one exists. A progress bar and **Cancel** are shown. A failed or cancelled run deletes the half-written zip |
| Extract here | Tap an archive > **Extract here** | A folder next to the archive, named like the file up to its first dot, with a free name if it exists. For example `holiday.photos.zip` becomes `holiday` |

Compress works on local files and folders. It keeps folders and file dates. The suggested name is the first
selected entry's name without extension.

Extraction works for every supported archive type through the same streaming code as copying out of an archive. It
never overwrites: if the target folder exists a numbered name is used.

### Limits

- Password-protected archives are not supported and fail to open.
- `7z` and `tar` need to read through the archive to find an entry, so opening a file near the end of a large archive
  is slow. Extracting a big archive takes time proportional to its size.
- Archives are read only. There is no way to add a file to an existing archive.
- Only local archives can be opened, not those on cloud or network storages. Copy the archive to the phone first.
- Archives are indexed once per session and the index is kept in memory.

## Cryptomator vaults <Badge type="warning" text="experimental" /> <Badge type="warning" text="read only" />

[Cryptomator](https://cryptomator.org) vaults (format 7 and 8) in a folder on the phone's storage can be unlocked
and read. A folder counts as a vault when it contains `masterkey.cryptomator` and a `d` folder. The check is made
for **local folders** only.

### Unlock a vault

1. Tap the vault folder. An **Unlock vault** dialog says "(name) is a Cryptomator vault (read only, experimental). The
   password is only used to unlock it and is not stored."
2. Enter the password and tap **Unlock**. The key derivation is slow on purpose and runs in the background
   ("Unlocking..."). A wrong password shows "Wrong password".
3. Browse and open files like in any folder. File and folder names are decrypted for display.
4. When you are done, open **More > Lock vault**. Telos forgets the key, deletes the decrypted temporary files and
   returns to the parent folder.

### What works inside a vault

| Action | Behavior |
| --- | --- |
| List folders and files | Yes, with decrypted names and sizes |
| Open a file | Decrypted into the cache (`remote_open`) and opened in the right app |
| Copy out | Select, Copy, paste into a local or remote folder |
| Share | Yes, the decrypted copy is shared |
| Properties | Size and date. Checksums are calculated on local files only |
| New folder, new file, rename, delete, move, paste into the vault | **Not possible**. The vault is read only |
| Search | Not supported |

### Security notes

::: warning
- Read only: you cannot add or change files in a vault.
- The password is only used to unlock the vault and is not stored. The key stays in memory and is gone when
  Android ends the launcher process or you tap **Lock vault**.
- Files you open from a vault are **decrypted into a temporary cache**. **Lock vault** removes them. Until then they
  are readable by the app they were handed to, and by anything with access to the app cache.
- A shared decrypted file leaves the vault. Delete it from the other app when you no longer need it.
- Experimental. Do not rely on it as your only way to reach important data. Use the official Cryptomator app to
  create or change vaults.
:::

### Compatibility

| Item | Support |
| --- | --- |
| Vault formats | 7 and 8 |
| Location | A folder on the phone's own or SD card storage |
| Cloud-synced vault | Works if the cloud app keeps the vault folder as plain files on the phone |
| Vault on a Telos network or cloud storage | Not detected |
| Writing | No |
| Other encrypted containers | gocryptfs, EncFS and VeraCrypt are not supported |

The format code is implemented from the published Cryptomator format description. See
[Telos Files internals](../../../developer-guide/project-structure/telos-files#cryptomator).

## Limitations

- Only local archives and vaults.
- No passwords for archives.
- No writing into archives or vaults.
- Cryptomator support is experimental and read only.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| The archive dialog does not appear | The entry is inside a remote storage or another archive, or the extension is not supported. Use **Open with...** |
| "Not found in the archive" | The archive changed while it was open. Go back and open it again |
| "Wrong password" for a vault | Check the password. Vault passwords are case sensitive |
| "Could not open the vault" | The vault has an unsupported format or damaged files |
| Vault folder opens as a normal folder | It lacks `masterkey.cryptomator` or the `d` folder, or it is on a remote storage |
| Decrypted files remain | Tap **Lock vault** from the vault view. Closing the app alone does not delete the temporary copies |
