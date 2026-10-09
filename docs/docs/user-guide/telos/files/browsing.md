# Browsing and file operations

Everyday use of Telos Files on the phone's own storage and on remote storages: navigation, views, sorting, search,
selection, copy and move, delete, rename, compress, properties, checksums, sharing and opening files. Archives are
on [Archives and vaults](./archives-vaults), connections on [Network and cloud](./network-cloud) and system
folders on [Root explorer](./root).

## Navigation

| Control | Behavior |
| --- | --- |
| Storage icon (top left) | Opens the drawer with Home, volumes, favorites, connections and tools |
| Breadcrumbs | Home > volume or connection > each folder. Tap any part to jump there. The last part is the current folder |
| Tap a folder | Opens it |
| Back | Closes the drawer, a search, or a selection first, then goes one folder up, then to Home, then leaves the app |
| Volume root | One step up from a volume root goes to Home |
| **Refresh** (More menu) | Reloads the folder and re-reads the storage volumes |
| Loading | A thin progress bar under the top bar |
| Error | A lock icon with the message, for example "Cannot open this folder" |

### Volumes and quick access

The internal storage is always listed. Other volumes (SD card, USB drive) are found from the app folders Android
creates on them and are named "SD card / USB (name)". Each card shows used space as a bar with "N free of M".

Quick access creates and opens **Downloads**, **Camera** (the `DCIM` folder), **Pictures**, **Music**, **Movies**
and **Documents** of the internal storage.

### Favorites

Open a folder, then **More > Add to favorites** (or **Remove from favorites**). You can also select a single
folder and choose **Add to favorites** in the selection menu. Favorites appear on Home and in the drawer. They are
stored per phone and are not part of the launcher backup.

## Views, sorting and hidden files

| Setting | Where | Default | Options |
| --- | --- | --- | --- |
| View | More > Grid view or List view | List | List with name, size and date, or an adaptive grid of cells 112 dp wide |
| Sort by | More > Sort by... | Name | Name, Date, Size, Type |
| Descending | Sort by | off | Reverses the order |
| Folders first | Sort by | on | Keeps folders above files |
| Hidden files | More > Show hidden files | hidden | Entries whose name starts with a dot |

Every file shows an icon and colour by kind: folder, image, video, audio, document, archive, app, code, other.
Pictures (except SVG) show a thumbnail on local storage. Sorting by Type sorts by file extension, then by name. The
choices are saved and used for every folder.

### Kinds by extension

| Kind | Extensions |
| --- | --- |
| Image | `jpg jpeg png gif webp bmp heic heif avif svg ico tif tiff dng raw arw cr2 nef` |
| Video | `mp4 mkv webm avi mov 3gp m4v ts flv wmv mpg mpeg` |
| Audio | `mp3 m4a aac ogg oga opus flac wav wma amr mid midi` |
| Document | `pdf doc docx xls xlsx ppt pptx odt ods odp rtf txt md csv epub log` |
| Archive | `zip rar 7z tar gz tgz bz2 xz jar cab iso` |
| App | `apk apks xapk` |
| Code | `kt java py js json xml html css sh c cpp h rs go yml yaml toml ini conf prop gradle sql` |

## Search

Tap the magnifier to search **in this folder**.

| Where | How it searches |
| --- | --- |
| Local storage | Looks through the folder and all sub folders for names that contain your text, ignoring case. At most 300 results |
| Root explorer | The same with the superuser `find`, at most 300 results |
| Network and cloud | Only filters the entries already shown in the current folder |
| Archives and vaults | Not supported, the search finds nothing there |

Tap the arrow to close the search. "Nothing found" appears when there are no results.

## Selecting

| Action | How |
| --- | --- |
| Start selecting | Long-press an entry |
| Add or remove | Tap entries while selecting |
| Select all | Selection bar > More > **Select all** (everything visible, respecting hidden files) |
| Close | The X in the selection bar, or Back |

The selection bar offers **Copy**, **Cut**, **Delete**, and in More: **Select all**, **Share**, **Compress to zip**.
With exactly one entry it adds **Rename**, **Properties** and, for a folder, **Add to favorites**.

## Copy, cut and paste

1. Select the entries and tap **Copy** or **Cut**. A message says "Open the target folder and paste."
2. Browse to the target folder (it can be on another storage, a network storage or a cloud).
3. Tap **Paste here** (copy) or **Move here** (cut) in the bar at the bottom.

| Topic | Behavior |
| --- | --- |
| Progress | A task bar shows "Copying", "Moving" and so on with a bar, and **Cancel** |
| Same name | The copy gets a free name: `photo (1).jpg`. Nothing is overwritten |
| Into itself | A folder cannot be pasted into itself or into its own sub folder. It is counted as failed |
| Move on the same volume | A fast rename of the entry |
| Move across volumes | Copy, then delete the original |
| Across storages | Streams the data through the phone, so the phone must stay connected. Cloud to cloud copies run at the phone's network speed |
| Server-side | Within one connection a move is a rename on the server for every protocol. A copy is made on the server only for WebDAV, Nextcloud, ownCloud and Dropbox. Everything else copies through the phone |
| Clipboard | One clipboard for the whole app. Cutting clears it after a successful move. **Cancel** in the bar clears it |
| Result | "Done", or "N could not be copied or moved" |

::: tip Cloud to cloud
Copy from one provider's connection and paste in another's to move data between providers without a computer.
:::

## Create, rename, delete

| Action | How | Notes |
| --- | --- | --- |
| New folder, new file | **+** button | Names cannot be empty or contain `/`. An existing name gets a free variant |
| Rename | One selected entry > More > Rename | "Could not rename (does the name exist already?)" if the name is taken |
| Delete | Selection bar > Delete | A confirmation "This cannot be undone." There is **no trash** and no undo. In root mode system paths get a stronger warning |

Compress to zip and extract are described in [Archives and vaults](./archives-vaults#zip-and-extract).

## Opening and sharing files

| Kind | Opens in |
| --- | --- |
| Image | [Telos Viewer](../photos/viewer-editor). The viewer pages through all pictures of the folder |
| Video | [Telos Video](../video/library-player). The folder's videos become a playlist starting at the one you tapped. The player runs in its own process when that Video option is on |
| PDF, text, code, RTF | The [document viewer](../photos/documents) of Telos Viewer |
| `docx xlsx pptx odt ods epub` | The archive dialog first (these files are zip containers). Choose **Open with...** and pick Telos Viewer to read and edit them as documents |
| Archive (zip, jar, apk, 7z, tar family) | A dialog: **Browse**, **Extract here** or **Open with...** |
| Cryptomator vault folder | An **Unlock vault** dialog |
| Anything else | Android's chooser |
| Remote, archive or vault files | Downloaded or extracted into the cache first, then opened |

For a file Android's file provider cannot cover (or one only root can read), Telos copies it into its cache for the
other app to read. **Share** sends the selected **files**; folders cannot be shared ("Folders cannot be shared.
Compress them first."). One file uses a normal share, several use a multiple share.

## Properties and checksums

Select one entry and choose **More > Properties**.

| Field | Meaning |
| --- | --- |
| Type | Kind and extension |
| Location | The parent folder |
| Size | Calculated, with the exact byte count |
| Contains | Number of entries in a folder |
| Modified | Date and time |
| Permissions, Owner, Link to | Shown when known (local and root listings) |
| Checksums | **Calculate MD5**, **SHA-1** or **SHA-256** for a file, on demand. Shows "unavailable" when the file cannot be read locally |

Files also get **Open with...** and **Share** buttons in the dialog.

## Privacy

| Topic | Behavior |
| --- | --- |
| Network | Only to storages you added |
| Cache | Files opened from remote storages, archives and vaults are copied to the app cache (`remote_open`, `files_open`) |
| Locking a vault | Clears `remote_open` |
| Favorites, view and sort | Stored in the private preferences `telos_files` |

## Limitations

- No trash, no undo, no overwrite option, no per-file permission editing outside root mode.
- Checksums and zip creation work on local files only.
- Remote search only filters the current folder.
- A `rar`, `iso` or `cab` file is shown as an archive but is not opened inside Telos; use **Open with...**.
- Very large folders on remote storages load slowly because every listing is fetched in full.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "Could not rename" | The name already exists or has a `/` |
| "N could not be copied" | Not enough space, a read-only target, or a lost connection |
| A copy seems stuck | Tap **Cancel** in the task bar. The partly copied file may remain |
| Folder shows empty | Hidden files may be hidden. Use **Show hidden files** |
| "Cannot open this picture" or video | The file could not be handed over. Try **Open with...** |
