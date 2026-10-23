# Telos Viewer: Photos and Documents

![Telos Viewer](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-08-viewer.webp)


**Telos Viewer** is one app with two tabs: **Photos** (the photo gallery: photos, albums, EXIF, editor) and **Documents** (a library with category chips (All, Documents, PDF, Spreadsheets, Presentations, E-books, Other), search, sort by date, name or size, a **Recent** shelf (up to 50 files you opened from it), **Open file…** and a long-press menu (Share, Open with, Info). Both tabs support sharing selected files together. Shared files are copied to Telos' private share cache and handed off with temporary read access. The tab you used last is remembered. Telos Photos used to be a separate app: there is no separate icon anymore, and a Photos icon that was on the home screen or in the dock opens the Viewer. To list the documents on the phone it needs the same **All files access** as Telos Files; without it, Recent and **Open file…** still work, and "Open with" from other apps always works. The sections below describe both tabs.

A photo gallery, a metadata (EXIF) tool, a simple photo editor and a document viewer in one app.

::: tip At a glance
Gallery by date and by folder, full screen viewer with pinch zoom and glass chrome, EXIF viewer and editor, removal of location,
sharing without metadata, a non-destructive editor, PDF with text highlight, comics (CBZ/CBR/CBT/CB7), SVG/PSD/AI/CDR previews,
and a viewer and editor for text, Office, OpenDocument, RTF and EPUB, with 20 PDF tools.
:::

::: info This section
- This page: what it is, permissions, the gallery, entry points, privacy and the feature matrix.
- [Viewer, metadata and editor](./viewer-editor): the picture viewer, EXIF tools and the editor.
- [Document viewer](./documents): PDF, text, Office, OpenDocument, RTF and EPUB.
- [Office editing and PDF search](./office-editing): editing and saving documents, old Office files, search in a PDF.
- [PDF tools](./pdf-tools): 20 on-device PDF tools.
:::

## What it is

Telos Viewer shows the pictures that Android has indexed (the system media library) and opens images and documents
sent to it by other apps. It does not upload anything: it has no cloud, no account and no network features of its
own. It is a [virtual app](../#how-the-built-in-apps-work), part of the launcher, and it is covered by the
[crash guard](../#how-the-built-in-apps-work). Switching it off in [Telos Store](../store/) disables its screens
and removes it from the "Open with" and share menus.

## Getting started and permissions

| Permission | Why | When |
| --- | --- | --- |
| Images (`READ_MEDIA_IMAGES`, or storage on Android 12 and older) | Build the gallery | Asked on first open |
| Location access for photos (`ACCESS_MEDIA_LOCATION`) | Read the GPS position of photos on recent Android versions | Declared by the app, used by the metadata view |
| Write access to a file | Needed by Android for the first metadata change, and for deleting | Asked by the system, **per picture**, on Android 11 and later |

Open **Telos Viewer** and tap **Allow** ("Allow access to your photos"). Pictures and documents that another app
sends to Telos Viewer open directly, without browsing the gallery and without the permission.

Video frames and document previews (PDF, Office) in the lists are **on** by default. You can turn them off in Viewer settings
(the gear on the Photos / Documents tab bar). Image thumbnails in the gallery stay on.

## How it is reached

| From | What opens |
| --- | --- |
| The Telos Viewer icon in the app grid or search | The gallery |
| "Open with" for an image (`image/*`) | The viewer for that picture only |
| Share an image to "Telos Viewer" | The viewer for the shared picture |
| "Open with" for a PDF, text, Office, OpenDocument, RTF or EPUB file | The [document viewer](./documents) |
| A picture tapped in [Telos Files](../files/browsing#opening-and-sharing-files) | The viewer, paging through that folder's pictures |

It is listed as **Telos Viewer** in the menus of other apps, for pictures and for the document types.

## Formats

| Kind | Extensions |
| --- | --- |
| Pictures | `jpg jpeg png gif webp bmp heic heif hif avif svg ico tif tiff jxl` |
| Camera RAW | `dng raw arw cr2 cr3 nef nrw orf raf rw2 pef sr2` (preview from the embedded JPEG when Android cannot decode the RAW) |
| Graphics | `psd` thumbnail, `ai` as PDF or embedded bitmap, `cdr` embedded PNG/JPEG when present |
| Comics | `cbz cbr cbt cb7` |
| PDF | `pdf` with highlight, search, night pages and PDF tools |
| Office / OpenDocument | `docx xlsx pptx odt ods odp` (edit), `doc xls ppt` (read, convert), `rtf epub` |
| Text | `txt md markdown csv tsv log json xml html` and the code extensions on [Document viewer](./documents) |

The same picture and document list drives Telos Files thumbnails through one catalog. HEVC/H.265 video (`.hevc` `.h265` `.hvc`) is played in Telos Media, not in the still viewer.

## The gallery

The screen has two tabs.

| Tab | Details |
| --- | --- |
| **Photos** | Pictures and videos, newest first. Month headings, a stories strip (Recents, Screenshots, Favorites), pinch on the grid for 3–5 columns. Long-press to multi-select and share one or more files together. Glass bars follow the launcher blur theme |
| **Albums** | One album per folder (for example Camera or Screenshots), as 2 columns of cards with the picture count. Albums are sorted by how many pictures they hold. Pictures without a folder name go to "Other" |

- The list comes from the system media library, ordered by the time Android added the picture. The heading date is
  the date the picture was taken, or the date added when that is unknown.
- Tap a picture to open the viewer. Swiping pages through the list you came from (all pictures, or the album).
- Tap the back arrow in an album to return to the Albums tab.
- Thumbnails are loaded at 320 pixels and cached by the image library.

::: warning Only indexed pictures are listed
A picture appears only when Android's media scanner has found it. Folders that contain a `.nomedia` file are hidden.
New files from other apps can take a moment to appear. Use the viewer through **Telos Files** for pictures that are
not indexed.
:::

## Settings

Telos Viewer has **no settings page of its own**. Its behavior depends on the system permissions above and on your
[App Lock](../launcher/privacy-protection#app-lock) settings.

| Item | Where | Default | Effect |
| --- | --- | --- | --- |
| Enable or disable the app | [Telos Store](../store/) | on | Hides or shows the icon and disables the screens |
| App lock | Launcher settings | off | The editor registers with the launcher's app lock for Telos apps |

## Privacy

| Item | Detail |
| --- | --- |
| Network | None. The gallery, editor and document viewer run on the device |
| Metadata | Removing it is done locally. Sharing sends only the clean copy you chose |
| Documents | Opened files are copied into the app's private cache for reading and old copies are cleaned up |
| Cache | Files shared from Photos and Documents are copied to the app's private share cache and exposed through its FileProvider. **Share without metadata** separately creates a clean JPEG copy |
| App lock | The editor follows your App Lock settings |

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Photos by date | Photos tab | |
| Albums by folder | Albums tab | |
| Full screen viewer, swipe, pinch zoom, swipe down to close | Viewer | Zoom 1x to 8x; glass chrome |
| Videos and motion clips in the same pager | Viewer | Looping playback |
| Share, Edit, Details, Favorite | Viewer pill bar | Delete and wallpaper in the overflow menu |
| PDF night pages, drag-to-text highlights, EPUB type size | Document viewer | Marks stay on the device |
| Comics CBZ/CBR/CBT/CB7 | Document viewer | CBR not in F-Droid |
| SVG, PSD, AI, CDR preview | Document viewer | Flattened preview, not an editor |
| EXIF viewer and editor | Details | Mostly JPEG |
| Remove location, remove all metadata | Details | Changes the original file |
| Share without metadata | Details | Makes a JPEG copy |
| Editor with rotate, flip, crop ratio, adjustments, filters | Edit | Saves a copy in `Pictures/Telos` |
| Document viewer and editor | Open with | PDF, text, Office, OpenDocument, RTF, EPUB; Office and text are editable |
| Search in a PDF | Document viewer | Greek, accents and Greeklish, see [Office editing and PDF search](./office-editing) |
| PDF tools | Document viewer | 20 tools, see [PDF tools](./pdf-tools) |
| Search the gallery | Photos tab | By file name, folder and date, see [Search in the apps](../search-in-apps) |
| Cloud albums, face recognition, slideshows | | Not available |

## Supported formats

| Item | Support |
| --- | --- |
| Pictures in the gallery | Pictures Android has indexed and the image library can decode (JPEG, PNG, WebP and others) |
| EXIF editing | Formats that Android's EXIF interface can write, mainly JPEG. Others show an error |
| Editor output | JPEG |
| Documents | PDF, plain text and code, `.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`, RTF, EPUB, comics CBZ/CBR/CBT/CB7, SVG, PSD/AI/CDR preview |
| Read-only | `.doc`, `.xls`, `.ppt` (can be converted to `.docx`, `.xlsx`, `.pptx`) |
| Not supported | Encrypted or password-protected Office files |

Photos and documents open in Telos Viewer, which is part of the launcher. There is no downloadable
viewer add-on.

## Limitations

- Office and OpenDocument files are shown with structure but without page layout or fonts.
- Search works inside an open PDF only, and there are no annotations on PDFs.
- No cloud albums, face recognition, slideshows, favorites or sorting options.
- The editor always saves JPEG copies and has no free crop tool, only the listed crop ratios.
- Metadata removal cleans the known EXIF fields. It does not guarantee that every kind of embedded data is gone.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Allow access to your photos" stays | Grant the image permission in Android settings |
| An album is missing | The pictures must be indexed by Android. Folders with `.nomedia` are hidden |
| A new picture does not show | Wait for the media scanner, or restart the phone |
| "This file format does not support metadata changes" | Use **Share without metadata** instead |
| Document shows "Nothing to show" | The file has no readable text. Use **Open with** |
| PDF says it is protected | Open it in an app that supports passwords |
| Related files | Pictures and videos opened from [Telos Files](../files/) use this app and [Telos Video](../video/) |


## Κοινός κάδος Telos

Πριν από διαγραφή επιλέγετε μεταφορά στον κάδο ή οριστική διαγραφή. Τα τοπικά αρχεία μεταφέρονται ατομικά σε ιδιωτικό φάκελο του app στον ίδιο χώρο αποθήκευσης· αποτυχία μεταφοράς δεν διαγράφει το πρωτότυπο. Προεπιλεγμένη διατήρηση: 30 ημέρες, ρυθμιζόμενη σε 1–365 ημέρες για επόμενες διαγραφές. Οι ήδη αποθηκευμένες ημερομηνίες λήξης δεν αλλάζουν. Ο καθημερινός καθαρισμός αφαιρεί ληγμένα στοιχεία όταν το Android εκτελέσει την εργασία. Ο κάδος είναι διαθέσιμος από Files, Viewer και Media, με επαναφορά στην αρχική θέση χωρίς αντικατάσταση υπάρχοντος αρχείου.

Χωρίς δικαίωμα πρόσβασης στο τοπικό αρχείο, φωτογραφίες/βίντεο/ήχος μπορούν να χρησιμοποιήσουν τον κάδο του Android (Android 11+), με συναίνεση του συστήματος και σταθερή διατήρηση 30 ημερών. Εμφανίζονται έως 1.000 στοιχεία του συστήματος που επιτρέπουν τα τρέχοντα δικαιώματα πολυμέσων. Remote/root/archive/vault πηγές και providers που δεν επιτρέπουν ασφαλή μεταφορά δεν έχουν γενικό κάδο· η οριστική διαγραφή ζητά επιβεβαίωση. Ο ιδιωτικός κάδος αφαιρείται όταν απεγκατασταθεί το Telos ή διαγραφούν τα δεδομένα του app.

### Shared trash

Files, Viewer and Media share a delete/trash choice and restore UI. Local files use an atomic same-volume move with durable metadata, a default 30-day retention configurable to 1–365 days for future deletions, and daily expiry cleanup. Restore never overwrites an existing file. Android-managed media trash uses the system’s fixed 30-day retention and permission dialog. Unsupported storages offer explicitly confirmed permanent deletion. The private trash is removed on app uninstall or app-data deletion.


Static photos chosen with **Set wallpaper** now offer **Home**, **Lock**, or **Both** directly through Telos. Home/Both also creates the small private snapshot used by shared frosted backgrounds. Lock-only changes do not replace that snapshot. Animated GIFs and video retain the external wallpaper handoff.
