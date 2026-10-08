# Telos Downloads: research and design (phase 1 of 3)

Status of this note: written 2026-10-08 during phase 1. "Verified" means read on the project's own
GitHub page (README and the license shown there) on that date. "Not verified" means we could not
read it or the page does not say. Nothing here was checked by running the other projects.

## 1. The five reference projects

| Project | License (verified) | Language and stack | Real features (verified from its README) | UI (verified) |
| --- | --- | --- | --- | --- |
| [Gopeed](https://github.com/GopeedLab/gopeed) | GPL-3.0 | Go backend, Flutter frontend (the page names both) | Multi-connection HTTP/HTTPS, BitTorrent and magnet (DHT, uTP, web seeds, selective files, trackers, seeding limits), ed2k; pause, resume, retry, batch, search, status filters, categories, recovery after restart; custom headers and User-Agent, proxies, notifications, automatic archive extraction; JavaScript extensions for more sources, browser extension, REST API, CLI, MCP endpoint, webhooks | Native Flutter, task lists, navigation, settings and detail views; phone, tablet and desktop layouts; light and dark, eight accent colours. Single concept screenshot only, no screen-by-screen description |
| [Ketch](https://github.com/linroid/Ketch) | Apache-2.0 | Kotlin Multiplatform (modules `core`, `ktor`, `remote`) | Parallel connections that can be added or removed during a download, pause and resume across restarts, HTTP/HTTPS, FTP/FTPS, BitTorrent (pure Kotlin), HLS and DASH; speed modes and schedule, per-download limits, priorities, queue with per-site limits and scheduled starts; clipboard link detection, browser extension, CLI and MCP, headless server with REST API, device pairing | Desktop: sidebar with devices and live speed, "all downloads" table, a view with one lane per connection, command palette (Ctrl+K); light and dark, four accents. Roadmap (not shipped): Metalink, WebDAV, checksums, proxy, category folders |
| [Kite](https://github.com/zenzer0s/kite) | GPLv3 | Flutter (the page does not name Dart), Riverpod, Drift (SQLite); core is yt-dlp through youtubedl-android | Downloads from hundreds of sites via yt-dlp with engine updates, 4K/8K video and audio formats, WebView that syncs session cookies, optional Telegram sending, queue with history and background processing | Material 3, dynamic colour, six screenshots (not described) |
| [AIO Video Downloader](https://github.com/shibaFoss/AIO-Video-Downloader) | "Custom Open Source License" in `license.md`, text not read: treat as not GPL compatible | 100 % Kotlin (the page says so) | NextGen version, partly goals not shipped: built-in browser with video grabber, 1000+ sites via yt-dlp (youtubedl-android, NewPipe Extractor as alternative), parallel connections, quality and subtitle choice, torrent support, private vault, player | Screenshots are of the legacy app; NextGen UI is being rebuilt |
| [LibreTorrent](https://github.com/proninyaroslav/libretorrent) | GPL-3.0 or later | The page does not name the language; "based on libtorrent4j" (so JVM) | BitTorrent 2.0, WebTorrent, magnet, HTTP(S); file selection, move while downloading, auto-move on completion, create torrents, sequential download for streaming, scheduling, RSS auto download, DHT, PeX, encryption, LSD, UPnP, NAT-PMP, uTP, IP filters (eMule dat, PeerGuardian), proxy; Android TV, Material, dark and black themes, tablet UI | Five screenshots (phone, create torrent, RSS, tablet), not described |

Not verified for any project: exact screen names, internal architecture, code quality. Details about
what is "real" for AIO NextGen are limited by its own README, which lists goals.

### What we take, per project

| Idea | From | Phase | Decision |
| --- | --- | --- | --- |
| Segmented HTTP download that survives restarts, filters All/Active/Queued/Completed/Failed, batch add, headers, User-Agent, proxy, categories | Gopeed | 1 | Ideas only. Gopeed is GPL-3.0, so code would be allowed, but its code is Go and Dart: nothing to reuse in a Kotlin launcher |
| Connection lanes in the detail view, clipboard link detection, queue limits, speed limits per download, priorities, checksums (on their roadmap) | Ketch | 1 | Ideas only. Apache-2.0 would allow code, but Ketch is Kotlin Multiplatform with its own pipeline; the engine here is written fresh on OkHttp |
| Torrent client behaviour (file selection, sequential download, IP filters, scheduling) with libtorrent4j that Telos already uses | LibreTorrent | 2 | Ideas and behaviour. GPL-3.0-or-later code could be adapted if useful; not done yet |
| yt-dlp as extraction engine through youtubedl-android, cookie sync from a WebView | Kite, AIO | 3 | Ideas. Kite is Flutter/Dart (no code to reuse). AIO's license is custom and not read: **do not copy any code** |
| Built-in browser that finds media, private vault | AIO | 3 | Ideas, to be decided |
| Extensions (JavaScript plugins) | Gopeed | later | Out of scope; Telos has its own plugin SDK |

## 2. Feasibility in Telos

- HTTP/HTTPS multi-connection: yes, OkHttp is already a dependency; Range requests are all that is needed.
- FTP: not in phase 1 (Telos Files has commons-net; could be added as an engine later).
- Torrent: libtorrent4j is already used by Telos Video for streaming; a download engine can share the session code (phase 2). The block lists of Telos Video can be reused.
- Video sites (yt-dlp): needs a native Python/yt-dlp runtime (youtubedl-android bundles Python and FFmpeg, tens of MB per ABI). Feasible but large; decision in phase 3. Alternative: NewPipe Extractor (GPL-3.0) for a few sites.
- Foreground service type: `dataSync` (Android 14 requires a type; Android 15 limits dataSync to 6 hours per day and calls `Service.onTimeout`, handled by pausing).
- Storage: Storage Access Framework tree (user folder), MediaStore Downloads (`Download/Telos`, Android 10+), app folder below Android 10 (minSdk is 26). No broad storage permission.

## 3. Architecture (phase 1)

```
app/ui  ui/downloads/                    Compose screens, share target
  DownloadsScreen, AddDownloadSheet, TaskDetailSheet, DownloadsSettingsScreen, DownloadShareActivity
services/downloads  (:services:downloads, package de.mm20.launcher2.downloads)
  DownloadTask (model, @Serializable)    id, type, url, mirrors, name, treeUri, fileUri, state, progress, speed, size,
                                         error, category, headers, cookies, segments ...
  DownloadEngine / EngineSession         the plug-in point of phase 2 and 3
  engine/HttpDownloadEngine              OkHttp, segments, Range, resume, validation, limits, checksum
  DownloadManager                        scheduler + API: queue rules, retry/backoff, conditions, events
  DownloadStore                          JSON file filesDir/downloads/tasks.json (atomic, debounced)
  DownloadSettings                       SharedPreferences "telos_downloads"
  DownloadFiles                          SAF / MediaStore / app folder, random access writer
  DownloadService + DownloadNotifier     foreground service (dataSync), notifications and actions
  ConditionsMonitor                      network type, battery
  logic/                                 pure Kotlin, unit tested: SegmentPlanner, FileNames, MimeTypes,
                                         QueueRules, RetryPolicy, HttpRanges, Checksums, LinkParser, Formatting
```

Decisions:

- **New module** `:services:downloads` (like `:services:tags`): low risk, depends only on `:core:base` (icon, deep link constants), `:core:i18n` (strings), OkHttp, Koin. The UI stays in `:app:ui` (the only Compose module).
- **JSON store instead of Room**: the data is a short list of tasks that is rewritten as a whole; no Room migration risk; plain enough to add to the Telos backup as a group later (not done in phase 1; downloaded files are not backed up anyway).
- Scheduler runs in the process (a Koin singleton started from `LauncherApplication`). The service keeps the process alive and shows notifications; it does not run the downloads.
- Engines are chosen by `supports(task)`; `DownloadType` has `Http`, `Torrent`, `Media`. `EngineSession` gives an engine the settings, the global speed limiter, the file access and `update`/`progress`.
- Resume data lives in the task (`segments`, `etag`, `lastModified`, `totalBytes`, `fileUri`) so any engine can persist its own resume state the same way.

### HTTP engine behaviour

1. Probe with `GET Range: bytes=0-0` (HEAD is unreliable): 206 gives total size and range support; 200 means no ranges; redirects are followed by hand (max 10) and cookies/Authorization are dropped when the host changes. Mirrors are tried in order.
2. Name from Content-Disposition (`filename*` before `filename`), then the final URL, then `download`; extension from the Content-Type if missing; sanitized, uniqueness by the file system (SAF, MediaStore) or a counter.
3. Plan 1 to 16 ranges of at least 512 KB; every connection is HTTP/1.1 (HTTP/2 would multiplex them onto one socket). An idle connection splits the biggest remaining range in half (at least 256 KB each).
4. Validation on resume: size, ETag, Last-Modified compared with the stored values; `If-Range` on every ranged request; a `200` answer to a ranged request restarts from zero once. Lost or shortened file also restarts.
5. Errors: network errors retry a segment 3 times, then the task is queued again with backoff 5 s doubling to 5 min, up to the retry limit (default 5); HTTP 408/425/429/5xx are retryable, other 4xx are not; no network means "wait", no retry used up.
6. Speed limit: a token-spacing limiter per task and one global.
7. Checksum (MD5, SHA-1, SHA-256) verified after the download; a mismatch fails the task and keeps the file.

### Scheduler rules

Max parallel downloads (1 to 10), Wi-Fi only, pause on low battery (not while charging), automatic retry, order by priority then age. Blocked tasks are queued, not failed, and the UI shows why. After a process death active tasks are queued again on the next start.

## 4. Feature matrix (what Telos has after phase 1)

Legend: yes = implemented in phase 1 and compiled, but see "untested" in the phase 1 report; plan = phase 2 or 3; no = not planned.

| Feature | Gopeed | Ketch | Kite | AIO | LibreTorrent | Telos phase 1 | Later |
| --- | --- | --- | --- | --- | --- | --- | --- |
| HTTP/HTTPS multi-connection | yes | yes | no | yes | no | yes (1 to 16) | |
| Resume after restart | yes | yes | no | n/a | yes | yes | |
| Queue, max parallel, retry | yes | yes | yes | yes | yes | yes | |
| Speed limit | n/s | yes | no | n/s | yes | yes (global and per task) | |
| Custom headers, UA, cookies, referer | yes | n/s | cookies | n/s | no | yes | |
| Proxy | yes | roadmap | no | n/s | yes | yes (HTTP, SOCKS) | |
| Checksum | n/s | roadmap | no | n/s | no | yes | |
| Categories | yes | roadmap | no | n/s | no | yes (6) | |
| Batch add, clipboard detection | yes | yes | n/s | n/s | no | yes | |
| FTP | no | yes | no | no | no | no | maybe |
| BitTorrent, magnet | yes | yes | no | planned | yes | placeholder tab | phase 2 |
| Block lists / IP filter | no | no | no | no | yes | no | phase 2 |
| Video sites (yt-dlp) | no | no | yes | yes | no | no | phase 3 |
| Browser/clipboard capture | extension | extension | WebView | browser | no | clipboard in the add sheet, share target | phase 3 |
| Extensions / scripting | JS | no | no | no | no | no | no |
| Schedule (time based) | no | yes | no | n/s | yes | no | later |

n/s = not stated on the project page.

## 5. Phase 2 and 3 plan

Phase 2 (torrents and block lists): add `TorrentDownloadEngine` (`type = Torrent`, `supports` magnet and `.torrent`), based on the libtorrent4j session of Telos Video; fill the Torrents tab (file selection list, peers, seeding limits stored as extra fields or a side object in the task); take the block lists from Telos Video; `.torrent` and magnet intent filters must be coordinated with Telos Video (it owns them today); `DownloadFiles` needs a directory based sink (torrents write many files), the HTTP sink is single-file.

Phase 3 (media): `MediaDownloadEngine` over yt-dlp (decision about the runtime size first), format picker in the add sheet, cookie import from a WebView, capture from the browser/clipboard, extras: schedule, categories folders, auto extraction, backup group for the task list.
