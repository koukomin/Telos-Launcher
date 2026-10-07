# Telos Photos

A photo gallery, a metadata (EXIF) tool, a simple photo editor and a document viewer in one app.

::: tip At a glance
Gallery by date and by folder, full screen viewer with pinch zoom, EXIF viewer and editor, removal of
location, sharing without metadata, a non-destructive editor, and a viewer for PDF, text, Office,
OpenDocument, RTF and EPUB files.
:::

## What it is

Telos Photos shows the pictures that Android has indexed and opens images and documents sent to it by other
apps. It does not upload anything: it has no cloud, no account and no network features of its own.

## Getting started and permissions

| Permission | Why |
| --- | --- |
| Images (`READ_MEDIA_IMAGES`, or storage on Android 12 and older) | Build the gallery. Asked on first open |
| Write access to a file | Needed by Android for the first metadata change and for deleting. Asked by the system, per file |

Open **Telos Photos** and tap **Allow**. Pictures and documents that another app sends to Telos Photos
open directly, without browsing the gallery.

It is listed in other apps' open and share menus as **Telos Photos** for pictures, and for the document types
below.

## Gallery

| Tab | Details |
| --- | --- |
| **Photos** | All pictures, newest first, with day headings such as "Today", "Yesterday" or a date |
| **Albums** | One album per folder (for example Camera or Screenshots). Pictures without a folder go to "Other" |

Tap a picture to open the viewer.

## Viewer

- Swipe left and right to move to the next or previous picture. The counter shows "3 / 120".
- **Pinch** to zoom (up to 8 times). Double tap zooms in and out. Drag while zoomed to pan.
- Tap once on the picture to toggle the on-screen controls.

| Button | Action |
| --- | --- |
| Share | Sends the picture to another app |
| Edit | Opens the editor |
| Details | Shows and edits metadata |
| Delete | Asks Android to delete the picture, and you confirm in a system dialog |

## Metadata (EXIF)

Tap **Details** to see the image size, the GPS position (if present) and these fields: date taken, camera
make and model, lens, exposure, aperture, ISO, focal length, software, artist, description and copyright.

| Action | What it does |
| --- | --- |
| Edit | Change the text fields and save |
| Remove location | Deletes the GPS tags only |
| Remove all | Deletes all fields listed above and further identifying tags such as maker note, user comment and serial numbers |
| Share without metadata | Creates a clean copy and shares it. Your original is not touched |

::: warning These actions change the original file
Edit, **Remove location** and **Remove all** modify the picture file itself. Android asks for permission
the first time. If the format cannot hold EXIF data you see "This file format does not support
metadata changes". Use **Share without metadata** if you want to keep the original intact.
:::

::: details How "Share without metadata" works
The picture is decoded, rotated according to its orientation tag, and saved as a new JPEG (quality 95) in
the app's cache, then shared. A copy is always a JPEG, even if the original was another format, and the
re-encoding can slightly change file size.
:::

::: tip
Use "Share without metadata" before posting photos publicly. The location can reveal where you live.
:::

## Editor

<Badge type="info" text="non-destructive" /> Open it with **Edit** in the viewer. The result is always saved as a
**new file**, `Telos_<time>.jpg`, in `Pictures/Telos`. The original stays untouched.

| Tool | Options |
| --- | --- |
| Rotate | Left or right |
| Flip | Horizontal |
| Crop ratio | Free, 1:1, 4:3, 3:4, 16:9, 9:16 |
| Adjustments | Brightness, contrast, saturation |
| Filters | Original, Mono, Sepia, Warm, Cool, Invert |

The copy is saved as JPEG (quality 95), so editing a PNG or similar produces a JPEG.

## Document viewer

Telos Photos also opens documents. Android offers it for these types.

| Format | How it is shown |
| --- | --- |
| PDF | Page by page, pinch to zoom (1x to 4x), page counter. Password-protected PDFs cannot be opened |
| Plain text and code | Monospace, selectable. **Edit** and **Save** write back to the file |
| Word (`.docx`) | Extracted headings, paragraphs and tables |
| Excel (`.xlsx`) | Extracted cell text, one table per sheet |
| PowerPoint (`.pptx`) | Extracted slide text |
| OpenDocument (`.odt`, `.ods`, `.odp`) | Extracted text and tables |
| RTF | Extracted text |
| EPUB | Extracted readable text |

Recognised text and code extensions include `txt`, `md`, `csv`, `tsv`, `log`, `json`, `xml`, `html`, `yml`,
`ini`, `toml`, `sql`, `kt`, `java`, `py`, `js`, `css`, `sh`, `srt`, `vtt` and more.

::: warning Text extraction only
Office, OpenDocument, RTF and EPUB files are shown as **extracted text and tables**. Fonts, images, page
layout, formulas and formatting are not reproduced, and you cannot edit them. The old binary Office
formats **`.doc`, `.xls` and `.ppt` cannot be opened**. Use **Open with** to send the file to another app.
:::

- Text files are read up to **2 MB**. A note tells you when a file is longer.
- If a text file cannot be written back, Telos offers to save it as a new file.
- A file that contains nothing readable shows a message and the **Open with** option.
- Documents are copied to the app's cache to be read, and old copies are cleaned up.

## Settings

Telos Photos has no settings page of its own. Its behavior depends on the system permissions above.
Switching the app off in the [Store](./store) disables its screens.

## Privacy

| Item | Detail |
| --- | --- |
| Network | None. The gallery, editor and document viewer run on the device |
| Metadata | Removing it is done locally. Sharing sends only the clean copy you chose |
| Documents | Opened files are copied into the app's private cache for reading |
| App lock | The editor registers with the launcher's app lock for Telos apps, so it follows your App Lock settings |

## Supported formats

| Item | Support |
| --- | --- |
| Pictures in the gallery | Pictures Android has indexed and the image library can decode (JPEG, PNG, WebP and others) |
| EXIF editing | Formats that Android's EXIF interface can write, mainly JPEG. Others show an error |
| Editor output | JPEG |
| Documents | PDF, plain text and code, `.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`, RTF, EPUB |
| Not supported | `.doc`, `.xls`, `.ppt`, encrypted or password-protected files |

## Limitations

- Office and OpenDocument files are text extraction only, with no layout.
- No search inside documents, and no annotations on PDFs.
- No cloud albums, face recognition or slideshows.
- The editor always saves JPEG copies and has no per-pixel crop tool, only the listed crop ratios.
- Metadata removal cleans the known EXIF fields. It does not guarantee that every kind of embedded data is gone.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Allow access to your photos" stays | Grant the image permission in Android settings |
| An album is missing | The pictures must be indexed by Android. Folders with `.nomedia` are hidden |
| "This file format does not support metadata changes" | Use **Share without metadata** instead |
| Document shows "Nothing to show" | The file has no readable text. Use **Open with** |
| PDF says it is protected | Open it in an app that supports passwords |
| Related files | Pictures and videos opened from [Telos Files](./files) use this app and [Telos Video](./video) |
