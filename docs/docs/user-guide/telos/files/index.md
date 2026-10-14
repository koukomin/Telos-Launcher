# Telos Files

A file manager for your phone, for network storages and for cloud storages. Its layout takes inspiration from
Solid Explorer and MiXplorer (no code was copied).

::: info At a glance
<Badge type="tip" text="local storage" /> <Badge type="tip" text="WebDAV, Nextcloud, ownCloud" />
<Badge type="tip" text="SFTP, SMB, FTP" /> <Badge type="tip" text="Dropbox, Google Drive, OneDrive" />
<Badge type="warning" text="Cryptomator (read only, experimental)" /> <Badge type="warning" text="root explorer (optional, untested)" />
:::

## Pages in this section

| Page | Contents |
| --- | --- |
| [Σύγκριση MiXplorer και Solid Explorer](./feature-comparison) | Αναλυτικός πίνακας δυνατοτήτων, ελλείψεων και σταδίων υλοποίησης |
| [Browsing and file operations](./browsing) | Home page, drawer, breadcrumbs, views, sorting, search, selection, copy and paste, properties, opening files |
| [Root explorer](./root) <Badge type="warning" text="root" /> | Superuser mode, warnings, permissions, remounting `/system` |
| [Network and cloud storages](./network-cloud) | WebDAV, Nextcloud, ownCloud, SFTP, SMB, FTP, Dropbox, Google Drive, OneDrive, with per-provider client ID setup |
| [Archives and Cryptomator vaults](./archives-vaults) | Browsing and unpacking archives, zip, read-only vaults |

For how the code is organised see the developer page [Telos Files internals](../../../developer-guide/project-structure/telos-files).

## How to open it

Telos Files is a [virtual app](../#how-the-built-in-apps-work). Tap its icon in the app grid or find it in search.
You can switch it on and off in [Telos Store](../store/). It registers no file type of its own with Android, but
it opens pictures, videos and documents in the other Telos apps.

## Permissions

| Needed for | Permission | Notes |
| --- | --- | --- |
| Showing the files on the phone | **All files access** (`MANAGE_EXTERNAL_STORAGE`) on Android 11 and later; storage read and write on older versions | A gate screen "To show and manage the files on your phone, Telos needs access to all files." has **Allow access** and **Not now** |
| Network and cloud storages | Internet | Used only for the storages you added |
| Root explorer | A superuser grant from your root manager | Optional, see [Root explorer](./root) |
| Opening files in other apps | None | Files are handed over through a file provider |

Without All files access, only the gate screen is shown.

## A tour of the screens

### Gate screen

Shown until the permission is granted. **Allow access** opens Android's "All files access" page for Telos (or the
legacy storage permission prompt on Android 10 and older).

### Home page

| Section | Contents |
| --- | --- |
| Storage | One card per volume: **Internal storage**, and SD cards or USB drives, with a usage bar and "N free of M" |
| Quick access | Downloads, Camera, Pictures, Music, Movies and Documents of the internal storage (created if missing) |
| Favorites | Folders you added to favorites, each with a remove button |
| Network and cloud | Your saved [connections](./network-cloud) |
| Tools | **Root file system**, enabled only while the [root explorer](./root) is on |

### Storage drawer

The drawer (storage icon at the top left) lists Home, every volume with its free space, Favorites, Network and
cloud connections (with **Add or manage...**), and Tools with the root explorer switch.

### Folder view

Top bar with the folder name, a **search** icon and a three-dot **More** menu. Under it a **breadcrumb** path bar
(Home > storage > folders) where every part is tappable. A **+** button creates a folder or a file. A progress bar
under the title shows loading. See [Browsing](./browsing).

### Selection bar

Long-press an entry to start selecting. The bar shows "N selected", **Copy**, **Cut**, **Delete** and a **More** menu.

### Bottom bars

A task bar with a progress and **Cancel** while copying, moving, deleting, compressing, extracting or downloading,
and a paste bar "N items to copy" with **Paste here** or **Move here** and **Cancel**.

## Feature matrix

| Feature | Local | Root | Network and cloud | Archive | Cryptomator vault |
| --- | --- | --- | --- | --- | --- |
| Browse and open | yes | yes | yes (downloaded first) | yes (extracted first) | yes (decrypted first) |
| New folder, new file | yes | yes | yes | no | no |
| Rename | yes | yes | yes | no | no |
| Delete | yes | yes | yes | no | no |
| Copy and move | yes | yes | yes, also across storages | copy out only | copy out only |
| Compress to zip | yes | no | no | no | no |
| Extract here | yes | no | no | from the local file | no |
| Search | recursive | recursive | current listing only | no | no |
| Properties and checksums | yes | yes | partly | partly | partly |
| Change permissions | no | yes | no | no | no |
| Share | yes | yes | yes (downloaded first) | yes | yes |
| Status | stable | <Badge type="info" text="untested" /> | stable | stable | <Badge type="warning" text="experimental" /> |

## Settings and stored state

Telos Files has no separate settings screen. View, sorting and hidden-file choices are remembered per folder; defaults from earlier versions are preserved.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| View | View button | List | List or two grid sizes, remembered per folder |
| Sort | More > Sort by... | Name, ascending, folders first | Name, date, size or type, remembered per folder |
| Hidden files | More > Show hidden files | hidden | Shows entries starting with a dot |
| Favorites | More > Add to favorites | none | Folders on the home page and in the drawer |
| Connections | Settings > Integrations > Cloud and network storage, or drawer > Add or manage | none | See [Network and cloud](./network-cloud) |

## Security of stored secrets

Passwords, private keys, client secrets and tokens of saved connections are encrypted with a key held in the Android
Keystore, which does not leave the device. Cryptomator passwords are never stored.

## Not supported

| Not supported | Reason |
| --- | --- |
| Mega | Its own encryption protocol is not implemented |
| iCloud | Apple has no public API |
| gocryptfs, EncFS, VeraCrypt | Not implemented |
| Writing to Cryptomator vaults | Read only |
| Shared client IDs for cloud storages | You need your own |
| Trash or undo | Delete is permanent |
| Writing into archives | Archives are read only |

## Limitations

- Everything runs in the launcher process, so a very large copy keeps the launcher busy; use **Cancel** if needed.
- Remote storages have no thumbnails and no recursive search.
- File names cannot contain `/`.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| Only the gate screen shows | Tap **Allow access** and enable All files access for Telos |
| A folder says "Cannot open this folder" | The app cannot read it (for example `Android/data` on newer Android). Use the root explorer if you have root |
| The SD card is missing | Tap **Refresh** in the More menu to re-read the volumes |
| A file opens in the wrong app | Telos opens pictures in Photos, videos in Video and documents in Telos Viewer. Others use Android's chooser |
| Related apps | [Photos](../photos/), [Video](../video/) |

## Καρτέλες και δύο πάνελ

Η επάνω γραμμή ανοίγει νέες καρτέλες και ενεργοποιεί τα «Δύο πάνελ». Κάθε καρτέλα διατηρεί τον δικό της φάκελο και τις επιλογές της. Τα πάνελ εμφανίζονται δίπλα-δίπλα σε αρκετά μεγάλη οθόνη και το ένα πάνω από το άλλο σε μικρότερη. Το ενεργό πάνελ επισημαίνεται με το χρώμα του Telos. Copy/Cut/Paste μοιράζονται μεταξύ καρτελών· drag-and-drop δεν έχει προστεθεί ακόμη.

Με επιλογή πολλών αρχείων, το μενού προσφέρει «Μαζική μετονομασία»: όνομα/επέκταση, αρίθμηση με padding, εύρεση και αντικατάσταση με προαιρετική κανονική έκφραση. Εμφανίζεται preview και ελέγχονται οι συγκρούσεις πριν ξεκινήσει η αλλαγή. Σε αποτυχία επιχειρείται επαναφορά των αρχικών ονομάτων· αν η πηγή δεν επιτρέψει την επαναφορά, εμφανίζεται σχετικό μήνυμα.


## Ανάλυση χώρου και διπλότυπα

Ανοίξτε τοπικό φάκελο και επιλέξτε «Ανάλυση χώρου» από το μενού. Θα δείτε χώρο ανά τύπο και τα δέκα μεγαλύτερα αρχεία. Η εύρεση πανομοιότυπων αρχείων συγκρίνει μέγεθος, SHA-256 και τελικά τα ίδια τα bytes. Πατήστε διαδρομή για να ανοίξετε τον φάκελο· δεν γίνεται αυτόματη διαγραφή. Η σάρωση δεν ακολουθεί symlinks, σταματά στις 50.000 εγγραφές και αναφέρει μερικά αποτελέσματα όταν υπάρχουν μη προσβάσιμοι φάκελοι. Κλείσιμο του διαλόγου ακυρώνει την εργασία. Δεν καλύπτει root, remote, archive ή vault φακέλους.

### Storage analysis

The local-folder menu offers storage analysis, the ten largest files and byte-verified duplicate groups. No files are automatically deleted. Scans are cancellable, skip symlinks and stop at 50,000 entries; inaccessible folders and limits produce an explicit partial-results note. Root, remote, archive and vault paths are excluded.


## Κοινός κάδος Telos

Πριν από διαγραφή επιλέγετε μεταφορά στον κάδο ή οριστική διαγραφή. Τα τοπικά αρχεία μεταφέρονται ατομικά σε ιδιωτικό φάκελο του app στον ίδιο χώρο αποθήκευσης· αποτυχία μεταφοράς δεν διαγράφει το πρωτότυπο. Προεπιλεγμένη διατήρηση: 30 ημέρες, ρυθμιζόμενη σε 1–365 ημέρες για επόμενες διαγραφές. Οι ήδη αποθηκευμένες ημερομηνίες λήξης δεν αλλάζουν. Ο καθημερινός καθαρισμός αφαιρεί ληγμένα στοιχεία όταν το Android εκτελέσει την εργασία. Ο κάδος είναι διαθέσιμος από Files, Viewer και Media, με επαναφορά στην αρχική θέση χωρίς αντικατάσταση υπάρχοντος αρχείου.

Χωρίς δικαίωμα πρόσβασης στο τοπικό αρχείο, φωτογραφίες/βίντεο/ήχος μπορούν να χρησιμοποιήσουν τον κάδο του Android (Android 11+), με συναίνεση του συστήματος και σταθερή διατήρηση 30 ημερών. Εμφανίζονται έως 1.000 στοιχεία του συστήματος που επιτρέπουν τα τρέχοντα δικαιώματα πολυμέσων. Remote/root/archive/vault πηγές και providers που δεν επιτρέπουν ασφαλή μεταφορά δεν έχουν γενικό κάδο· η οριστική διαγραφή ζητά επιβεβαίωση. Ο ιδιωτικός κάδος αφαιρείται όταν απεγκατασταθεί το Telos ή διαγραφούν τα δεδομένα του app.

### Shared trash

Files, Viewer and Media share a delete/trash choice and restore UI. Local files use an atomic same-volume move with durable metadata, a default 30-day retention configurable to 1–365 days for future deletions, and daily expiry cleanup. Restore never overwrites an existing file. Android-managed media trash uses the system’s fixed 30-day retention and permission dialog. Unsupported storages offer explicitly confirmed permanent deletion. The private trash is removed on app uninstall or app-data deletion.
