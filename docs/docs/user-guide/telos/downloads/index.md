# Telos Downloads

A download manager for links: several connections per file, resume, a queue and a folder of your choice.

::: tip At a glance
Paste or share a link, Telos downloads it with up to 16 connections at once, keeps going after a restart or a lost
network, and puts the file in the folder you choose or in `Downloads/Telos`. HTTP and HTTPS links, and torrents and magnet links
with file selection, seeding and IP block lists ([Torrents](torrents.md)), video and audio from web sites
([Video and audio sites](media.md), optional in the build), a schedule, a backup of the list and zip extraction.
:::

## What it is

Telos Downloads is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. The list of downloads is kept in
the private storage of Telos. Switching it off in [Telos Store](../store/) hides its icon.

## Adding downloads

| Way | How |
| --- | --- |
| In the app | The **Add** button. Paste one or more links (one per line). A link in the clipboard is offered as a chip. A single video site link opens the [media sheet](media.md) |
| Clipboard | Optional (Settings > **Detect links in the clipboard**, off by default): a link, video site link or magnet link that you copied is offered with an **Add** button when the Downloads screen opens |
| From a web app | **Download with Telos** in the menu of a [web app](../launcher/desktop-and-overlays.md#web-apps) sends the current address |
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

- Filters: **All**, **Active**, **Queued** (waiting and paused), **Completed**, **Failed**, **Torrents** and **Media** (cards with ratio, peers, up and down speed; see [Torrents](torrents.md)). The magnifier searches names and links.
- Each card shows the file type, name, state, size, speed and remaining time, with a progress ring and bar. The ring button pauses and resumes.
- Swipe a card to the right to pause or resume, to the left to remove it. The menu has pause, resume, retry, open, share, **move to the top / bottom of the queue** (waiting and paused downloads), **extract here** (finished zip files), copy link, details and remove. A finished download whose file was deleted or not restored shows **File missing**.
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
| Proxy | HTTP or SOCKS host and port for HTTP and media downloads. **Torrents and magnet links use the proxy too** (Telos Downloads and Telos Video streaming): for peers, trackers and host name lookups, with anonymous mode on and local discovery, UPnP and NAT-PMP off. With an HTTP proxy, DHT and uTP are also off because HTTP cannot carry UDP; with SOCKS5 they work only if the proxy supports UDP. Proxy user name and password are not supported. **Torrents through Telos Network WireGuard:** the proxy dialog can instead send torrents (Telos Downloads and Telos Video) through one of your [WireGuard](../network/wireguard.md) configs of Telos Network, using a local HTTP proxy that exists only while Telos Network is on and that config is connected. Otherwise torrents wait (the status is shown in the proxy dialog and the Add torrent sheet) and nothing connects directly. This replaces the proxy above for torrents, HTTP and media downloads are unaffected, DHT and uTP are off, and other apps on the device could also use the local proxy while it runs. Not yet verified on a device If the host or port is missing, torrents stop with a validation error instead of connecting directly |
| Schedule | Only download between two times of day on the days you tick (also over midnight, for example 22:00 to 07:00; the days are the days the window starts on). Outside the window downloads wait in the queue and a banner says so; they start at the opening time |
| Extract archives | Unpacks finished **zip** files into a folder named like the file, next to it (the **Extract here** action does it for one download). Limits: 20 000 files and 20 GB per archive, unsafe paths (`..`) are dropped. tar.gz, 7z and rar are not supported |
| Detect links in the clipboard | Off by default, see above |
| Video and audio sites | Update yt-dlp, its version and last update, supported sites, cookies, see [Video and audio sites](media.md) |
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

## Backup

[Backup and restore](../launcher/privacy-protection.md#backup-and-restore) has a part **Downloads**: the list of downloads and the settings of
this app. **Not** included: the downloaded files and partial data, cookies (the imported cookies.txt and the cookie of a download), `Authorization` and `Cookie`
headers, and the default folder (its permission does not exist on another phone). After a restore finished downloads whose file is not there are marked
**File missing**; downloads that were not finished come back **paused** and start from the beginning when you resume them.

## Status and limitations

- **Implemented:** HTTP and HTTPS downloads as described above, torrents ([Torrents](torrents.md)), video and audio sites ([Video and audio sites](media.md), optional in the build), clipboard capture (optional), a schedule, zip extraction, the backup part and queue order (move to the top or bottom).
- **Not there:** FTP, extracting tar.gz, 7z or rar, downloads on behalf of other apps (it is not the system download manager), a browser that finds media on pages, and drag and drop ordering. Nothing of phase 2 and 3 was tested on a device (see the Torrents and media pages).
- Downloads need the app process. If the system ends the process, downloads continue when Telos starts again (after a boot, when the launcher starts).
