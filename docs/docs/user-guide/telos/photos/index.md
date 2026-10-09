# Telos Viewer

A photo gallery, a metadata (EXIF) tool, a simple photo editor and a document viewer in one app.

::: tip At a glance
Gallery by date and by folder, full screen viewer with pinch zoom, EXIF viewer and editor, removal of location,
sharing without metadata, a non-destructive editor, and a viewer and editor for PDF, text, Office, OpenDocument, RTF and EPUB
files, with 20 PDF tools.
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

## How it is reached

| From | What opens |
| --- | --- |
| The Telos Viewer icon in the app grid or search | The gallery |
| "Open with" for an image (`image/*`) | The viewer for that picture only |
| Share an image to "Telos Viewer" | The viewer for the shared picture |
| "Open with" for a PDF, text, Office, OpenDocument, RTF or EPUB file | The [document viewer](./documents) |
| A picture tapped in [Telos Files](../files/browsing#opening-and-sharing-files) | The viewer, paging through that folder's pictures |

It is listed as **Telos Viewer** in the menus of other apps, for pictures and for the document types.

## The gallery

The screen has two tabs.

| Tab | Details |
| --- | --- |
| **Photos** | All pictures, newest first, in a grid of 4 columns with square thumbnails. A heading starts every day: "Today", "Yesterday", a weekday and date such as `Tue, 3 Mar`, or a full date for other years |
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
| Cache | A clean copy for sharing is written to the cache as a JPEG |
| App lock | The editor follows your App Lock settings |

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Photos by date | Photos tab | |
| Albums by folder | Albums tab | |
| Full screen viewer, swipe, pinch zoom | Viewer | Zoom 1x to 8x |
| Share, Edit, Details, Delete | Viewer bottom bar | Delete needs Android 11 and a system picture |
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
| Documents | PDF, plain text and code, `.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`, RTF, EPUB |
| Read-only | `.doc`, `.xls`, `.ppt` (can be converted to `.docx`, `.xlsx`, `.pptx`) |
| Not supported | Encrypted or password-protected Office files |

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
