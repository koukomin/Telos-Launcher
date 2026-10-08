# Telos Downloads

A download manager for links: several connections per file, resume, a queue and a folder of your choice.

::: tip At a glance
Paste or share a link, Telos downloads it with up to 16 connections at once, keeps going after a restart or a lost
network, and puts the file in the folder you choose or in `Downloads/Telos`. HTTP and HTTPS links, and torrents and magnet links
with file selection, seeding and IP block lists ([Torrents](torrents.md)).
:::

## What it is

Telos Downloads is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. The list of downloads is kept in
the private storage of Telos. Switching it off in [Telos Store](../store/) hides its icon.

## Adding downloads

| Way | How |
| --- | --- |
| In the app | The **Add** button. Paste one or more links (one per line). A link in the clipboard is offered as a chip |
| From another app | **Share** a link or text to **Telos Downloads**. The add sheet opens with the links, nothing starts before you confirm |
| Open with | Links to files that the browser hands over as zip, apk, 7z, rar, gz, iso or binary can be opened with Telos Downloads. Magnet links, `.torrent` addresses and `.torrent` files are offered too, next to [Telos Video](../video/streams-torrents-subtitles.md): you choose |

The add sheet also asks for the **folder** (any folder you pick, or `Downloads/Telos`), the **category** (automatic, or Video, Audio,
Documents, Archives, Programs, Other) and the number of **connections** (default from the settings). **More options**: file name
(for one link), user agent, referer, cookies, extra headers (`Name: value`), mirrors, a checksum (MD5, SHA-1 or SHA-256, checked when the
download is done), a speed limit for this download, and adding it paused.

::: info Magnet links and .torrent files
Paste a magnet link or a `.torrent` address into **Add** (or use the **Torrents** tab) and the torrent sheet opens, where you
see the files and choose which to download. Details on the [Torrents page](torrents.md). When you open a magnet link from
another app, Android may ask whether to use Telos Video (streams while it downloads, deletes everything afterwards) or Telos
Downloads (keeps the files and can seed). The **Video** dialog "Play from the web" also has **Download in Telos Downloads**.
:::

## The list

- Filters: **All**, **Active**, **Queued** (waiting and paused), **Completed**, **Failed**, and **Torrents** (cards with ratio, peers, up and down speed; see [Torrents](torrents.md)). The magnifier searches names and links.
- Each card shows the file type, name, state, size, speed and remaining time, with a progress ring and bar. The ring button pauses and resumes.
- Swipe a card to the right to pause or resume, to the left to remove it. The menu has pause, resume, retry, open, share, copy link, details and remove.
- **Remove** a finished download keeps the file or deletes it, your choice. A download that is not finished loses its partial file.
- A tap opens the **details**: link, address after redirects, size, folder, whether the server supports resuming, one bar for each connection, headers (cookies and passwords are hidden), errors, retries.

## How a download works

- Telos asks the server for the size and whether it accepts byte ranges. If it does, the file is split and fetched over up to 16 connections; a connection that is finished helps with the biggest part that is left.
- A server that does not support ranges gets one connection and a download that cannot be resumed (it starts again after an interruption).
- After a restart or a lost network the download continues where it stopped, if the file on the server is still the same (size, ETag, Last-Modified). If it changed, the download starts from zero.
- A failed attempt is tried again after 5 seconds, then 10, 20 and so on up to 5 minutes, as many times as the settings say (default 5). Errors like "not found" are not retried.
- The file name comes from the server (Content-Disposition), then from the link. A name that already exists gets a number.

## Settings

Menu > **Settings**.

| Setting | What it does |
| --- | --- |
| Downloads at the same time | 1 to 10 (default 3), the others wait in the queue |
| Connections per download | 1 to 16 (default 8) |
| Speed limit for all downloads | None, or 256 KB/s up to 10 MB/s |
| Automatic retries | 0 to 20 |
| Wi-Fi only | Downloads wait while only mobile data is available |
| Pause when the battery is low | Waits at 15 % or less, unless the phone is charging |
| Default folder | A folder you pick, or `Downloads/Telos` |
| When a download finishes | Nothing, open or share the file. Works while the Downloads screen is open; the notification always has **Open** and **Share** |
| User agent | Empty means a browser-like default |
| Proxy | HTTP or SOCKS host and port |
| Show notifications | Progress with **Pause** and **Cancel**, and the result |
| Torrents | A section with the torrent settings, limits, seeding defaults and the peer block lists, see [Torrents](torrents.md#settings) |

When downloads are waiting because of the network, Wi-Fi only or the battery, a banner on the list says so.

## Notifications and the service

While downloads run, Telos shows a notification (a foreground service of the type "data sync") with the speed, and one for each of
the first five active downloads with **Pause** and **Cancel**. On Android 13 and newer the first time you open the app asks for the
notification permission; without it the downloads still run, only the notifications are hidden. Android 15 limits this kind of service
to about six hours a day; when it ends the running downloads are paused and you resume them.

## Where the files go

| Where | Details |
| --- | --- |
| Your folder | Chosen with the system folder picker; Telos only gets access to that folder |
| `Downloads/Telos` | The default on Android 10 and newer, in the public Downloads folder. A file that is not finished is hidden from other apps and is not removed for 60 days |
| App folder | Below Android 10 the default is the app's own Downloads folder (`Android/data/...`) |

## Status and limitations

- **Implemented:** HTTP and HTTPS downloads as described above, and torrents ([Torrents](torrents.md)).
- **Planned:** downloads from video sites, capture from the browser or the clipboard, a backup of the list, schedules and extracting archives.
- **Not there:** FTP, schedules by time of day, extracting archives, a backup of the download list, downloads on behalf of other apps (it is not the system download manager), and nothing was tested on a device yet for torrents (see the Torrents page).
- Downloads need the app process. If the system ends the process, downloads continue when Telos starts again (after a boot, when the launcher starts).
