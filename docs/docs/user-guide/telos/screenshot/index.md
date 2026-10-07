# Telos Screenshot

Takes full, partial and scrolling screenshots and edits them: crop, hide parts with pixelate or blur, and draw.

::: tip At a glance
Full screenshot, partial screenshot (rectangle, oval or free shape), scrolling screenshot (experimental), an editor,
a notification with share, edit and delete, a delay timer, and a list of your screenshots.
:::

## What it is

Telos Screenshot is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. It takes the pictures
through the launcher's **accessibility service**, so it can edit them before they are saved. Nothing is uploaded and
Telos does not read the text in a screenshot.

::: warning Needs the accessibility service
Turn on the Telos accessibility service in Android Settings > Accessibility (the app has a button for it). It needs
the permissions to **take screenshots** and to **perform gestures** (for the scrolling screenshot). The service only
listens to window changes, see [Privacy and protection](../launcher/privacy-protection). On Android 13 and newer
see [Restricted settings](../../troubleshooting/restricted-settings).
:::

## Taking a screenshot

Open **Telos Screenshot** and tap a card. The app steps aside and, after a moment, takes the picture of what was
behind it. You can also use the tools of the [floating launcher](../launcher/desktop-and-overlays#floating-launcher):
**Screenshot**, **Partial screenshot** and **Scrolling screenshot**.

| Mode | What it does | Needs |
| --- | --- | --- |
| Screenshot | The whole screen, saved, with a notification | Android 11 or newer for Telos to get the picture. On Android 9 and 10 the system takes the screenshot instead and Telos cannot edit it |
| Partial screenshot | The editor opens on the picture, you choose a rectangle, an oval or a free shape and tap **Apply** | Android 11 or newer |
| Scrolling screenshot (experimental) | Takes a picture, swipes up, takes the next one and joins them, up to 12 screens. It stops at the end of the content | Android 11 or newer |

The scrolling screenshot finds where two pictures overlap by comparing rows. It can fail where a page has a fixed
header, a video or an animation, and then puts the pictures one below the other. Do not touch the screen while it
works.

## The notification and the editor

After a screenshot a notification shows it with **Share**, **Edit** and **Delete**. **Edit** (or a tap on a
screenshot in the app) opens the editor:

| Tool | What it does |
| --- | --- |
| Select | Only in a partial screenshot: keep a rectangle, an oval or a free shape (the outside is transparent, so a JPEG fills it with black) |
| Crop | Drag a rectangle and tap **Apply** |
| Pixelate, Blur | Drag over text, faces or numbers that should not be seen |
| Draw | Draw with your finger, six colors and a pen width |
| Undo | Goes back up to 8 steps |

**Save** writes a new picture and keeps the original, **Share** saves it and opens the share menu.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| File format | PNG | PNG or JPEG |
| Delay before a capture | None | 3, 5 or 10 seconds, to open a menu or the keyboard first |
| Notification after a capture | On | The notification with share, edit and delete |

Screenshots are saved in `Pictures/Screenshots` as `Screenshot_<date>_<time>_Telos.png`. The app lists the ones that
Telos made.

## Limitations

- No three-finger gestures and no button combinations: those belong to the system. The **Screenshot** tool of the
  floating launcher, the app and the Quick Settings tile of your phone are the ways to start a capture.
- No text extraction, no translation of a screenshot and no AI features. They would need a text recognition
  library that does not fit a launcher that is built for F-Droid.
- Content that an app protects against screenshots is black.
