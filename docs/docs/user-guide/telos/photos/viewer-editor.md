# Viewer, metadata and editor

The full screen picture viewer, the EXIF tools (view, edit, remove location, remove everything, share without
metadata) and the non-destructive editor. Documents are on [Document viewer](./documents), the gallery on the
[overview](./).

## The viewer

Open a picture from the gallery, from Telos Files, or from another app's "Open with" or share menu.

| Gesture or control | Effect |
| --- | --- |
| Swipe left or right | Next or previous picture. The counter shows `3 / 120` |
| Pinch | Zoom from 1x up to 8x |
| Double tap | Zooms to 2.5x, or back to fit when already zoomed |
| Drag while zoomed | Pans the picture, limited to its edges |
| Tap once | Hides or shows the on-screen controls |
| Back arrow (top left) | Closes the viewer |

A picture sent by another app opens alone, without the other pictures of the gallery. Opened from Telos Files, the
viewer pages through the pictures of that folder.

### Bottom bar

| Button | Action |
| --- | --- |
| Share | Sends the picture, unchanged, to another app through Android's share sheet |
| Edit | Opens the [editor](#the-editor) for this picture |
| Details | Shows and edits [metadata](#metadata-exif) |
| Delete | Asks Android to delete the picture. You confirm in a system dialog. Shown only on Android 11 and later for pictures from the media library |

After a confirmed delete the viewer closes. The system delete request removes the picture for good.

## Metadata (EXIF)

Tap **Details** to see the image size, the GPS position (if present, as `Location: 12.34567, 23.45678`) and these
fields: date taken, camera make and model, lens, exposure, aperture, ISO, focal length, software, artist,
description and copyright. When nothing is stored the sheet says "No metadata available".

| Action | What it does |
| --- | --- |
| Edit | Turns the fields into text boxes. Change them and **Save**. A blank field removes that tag |
| Remove location | Deletes the GPS tags only (latitude, longitude, altitude, time, date, processing method, speed, direction and the destination position) |
| Remove all | Deletes the GPS tags, all fields listed above, and further identifying tags: date and time tags, maker note, user comment, body and lens serial numbers, camera owner name and the unique image id |
| Share without metadata | Creates a clean copy and shares it. Your original is not touched |

::: warning These actions change the original file
Edit, **Remove location** and **Remove all** modify the picture file itself. On Android 11 and later Android asks
for write permission for that picture the first time. If the format
cannot hold EXIF data you see "This file format does not support metadata changes". Use **Share without metadata**
if you want to keep the original intact.
:::

### How "Share without metadata" works

1. The picture is decoded. A very large picture is scaled down while it is read so that its longer side is **at most
   about 4096 pixels**. Without this a 50 megapixel photo would not fit in memory.
2. It is rotated according to its orientation tag.
3. It is saved as a new **JPEG at quality 95** in the app's cache and shared. Nothing is written to your gallery.

Consequences: the copy is always a JPEG, even if the original was a PNG or another format. It carries no metadata at
all. A very large picture can end up smaller in pixels, and re-encoding can change the file size.

::: tip
Use "Share without metadata" before posting photos publicly. The location can reveal where you live. For a quick fix
of the original you can also use **Remove location**.
:::

### What is not guaranteed

Removal cleans the **known EXIF fields** listed above. It does not guarantee that every kind of embedded data is
gone: XMP packets, thumbnails inside the file, and format-specific data may survive in the original. The clean copy is
re-encoded and is the safe option.

## The editor

<Badge type="info" text="non-destructive" /> Open it with **Edit** in the viewer. The result is always saved as a **new
file**, `Telos_<time>.jpg`, in `Pictures/Telos`. The original stays untouched. A message says "Saved to Pictures/Telos"
or "Saving failed". Cancel leaves without saving.

| Tool | Options |
| --- | --- |
| Rotate | Left or right, in 90 degree steps |
| Flip | Horizontal |
| Crop ratio | Free (no crop), 1:1, 4:3, 3:4, 16:9, 9:16. The crop is centered on the picture |
| Adjustments | Brightness, contrast and saturation sliders |
| Filters | Original, Mono, Sepia, Warm, Cool, Invert |

- Rotation and flip are applied first, then the crop, then the colour adjustments and the filter.
- The preview shows the same result that is saved.
- The copy is saved as JPEG (quality 95), so editing a PNG or similar produces a JPEG.
- **Save copy** shows "Saving..." while it works.
- The editor registers with the launcher's app lock for Telos apps, so it follows your App Lock settings.

### Typical edits

1. Open the picture, tap **Edit**.
2. Pick **16:9** to crop to a wide frame, then **Rotate right** if needed.
3. Raise **Saturation** a little, or choose **Sepia**.
4. Tap **Save copy**. Find `Telos_...jpg` in the **Telos** album in the gallery.

## Settings

| Item | Where | Default | Effect |
| --- | --- | --- | --- |
| Save folder of edits | fixed | `Pictures/Telos` | Not configurable |
| JPEG quality | fixed | 95 | Not configurable |
| Maximum side of clean copies | fixed | about 4096 px | Not configurable |

There are no preferences; behavior follows the system permissions.

## Privacy and permissions

- All work happens on the device. The clean copy is created in the app cache and shared only when you choose an app.
- Write access is requested per picture by the system (Android 11 and later), never for the whole library.
- The location permission for photos is only used to read GPS tags that Android would otherwise redact.

## Limitations

- Editing and deleting need a picture from the media library on Android 11 and later; for other sources only
  non-destructive actions work.
- No free crop, no brush, text or stickers, no RAW development, no batch editing.
- The editor output is always JPEG.
- EXIF writing works for the formats Android's EXIF interface can write, mainly JPEG.
- No undo after **Remove location** or **Remove all** on the original file. Keep a copy first if unsure.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "This file format does not support metadata changes" | The format cannot hold EXIF. Use **Share without metadata** |
| "Could not create a copy" | Not enough space in the cache, or the picture could not be read |
| Delete button missing | The picture is not from the media library, or Android is older than 11 |
| The saved edit is not in the gallery | Wait for the media scanner, then open the Albums tab and look for **Telos** |
| The copy is smaller than the original | Clean copies are scaled to at most about 4096 pixels on the longer side |
