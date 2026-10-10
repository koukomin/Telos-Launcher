# Telos Video

A video library and player. It plays the videos on your phone, web streams, and torrents while they download.

::: tip At a glance
Library with movies, series, folders and continue watching. Full screen player with gestures, subtitles and picture
in picture. Optional posters (Wikipedia or TMDB), subtitles (OpenSubtitles) and Trakt.tv.
:::

::: info This section
- This page: what it is, permissions, the screens, privacy and the feature matrix.
- [Library and player](./library-player): library tabs, name recognition, resume, gestures, playback options, picture
  in picture and the separate process.
- [Streams, torrents and subtitles](./streams-torrents-subtitles): the web address dialog, torrents, OpenSubtitles,
  TMDB and Trakt.tv.
:::

## What it is

Telos Video reads the videos Android has indexed, groups them into a library, and plays them with Media3 /
ExoPlayer. It is also registered with Android as a handler for video files, `magnet:` links and `.torrent` files,
so other apps and browsers can open them in it. It is a [virtual app](../#how-the-built-in-apps-work), is guarded
by the crash guard, and its services and screens are disabled when you switch it off in [Telos Store](../store/).

## Getting started and permissions

| Permission | Why | When |
| --- | --- | --- |
| Video files (`READ_MEDIA_VIDEO`, or storage on Android 12 and older) | Build the library | Asked on first open ("Allow access to your videos to build the library") |
| Internet | Web streams, torrents, posters, subtitles, Trakt | Used only when you use those features |
| Notifications | Foreground service | Android 13 and later |

Open **Telos Video** and tap **Allow**. The library permission is only needed for the library: a video opened from
another app, from Telos Files, or a stream, plays without it.

## How it is reached

| From | What opens |
| --- | --- |
| The Telos Video icon | The library |
| "Open with" or share of a video (`video/*`) | The player with that video |
| A `magnet:` link, an `http(s)` link to a `.torrent`, or a `.torrent` file | The player, which starts the torrent |
| [Telos Files](../files/browsing#opening-and-sharing-files) | The folder's videos become a playlist |

## A tour of the screens

### Library screen

| Element | What it does |
| --- | --- |
| Title "Videos" | The screen name |
| Link icon in the top bar | "Play from the web": addresses, magnet links, `.torrent` files |
| Gear icon | "Video services": keys, subtitles, Trakt, torrent and player options |
| Search field "Search videos" | Filters the current list |
| Tabs **Library**, **Movies**, **Series**, **Folders** | See [Library and player](./library-player#the-library) |
| Detail view | Opened from a movie, series or folder. Back arrow, poster, title, description, **Add to Trakt watchlist**, and the list of files |

### Player

Full screen video with gesture controls, a top bar with the title, **Subtitles...** and a three-dot **Playback
options** menu, a transport bar, and for torrents a line with peers, speed and progress. See
[Library and player](./library-player#the-player).

### Video services dialog

One dialog with the sections Posters and descriptions, Subtitles, Torrents, Player and Trakt.tv. See
[Streams, torrents and subtitles](./streams-torrents-subtitles#video-services).

## Settings summary

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| TMDB API key | Video services | empty | Posters, descriptions and ratings from TMDB instead of Wikipedia |
| Subtitle sources | Video services | OpenSubtitles (no account), Podnapisi (no account), OpenSubtitles.com | Order and switches of the subtitle sources |
| OpenSubtitles User-Agent | Video services | empty (generic) | Agent name for the account-free OpenSubtitles API |
| OpenSubtitles.com key, user, password | Video services | empty | Optional source |
| Network folders | Video services | none | Folders of Telos Files storages shown in the library |
| Subtitle size, colour, edge, match frame rate, picture size | Playback options | normal, white, outline, on, fit | Remembered between videos |
| Languages | Video services | `en` | Comma separated codes, most wanted first |
| Download subtitles automatically | Video services | off | Fetches a subtitle without asking |
| Trakt | Video services > Trakt scrobbling | not connected, scrobbling off | Client id and secret, sign in, scrobbling switch, queue, sign out. See [Trakt scrobbling](trakt.md) |
| Torrents: Only on Wi-Fi | Video services | on | Refuses to start a torrent on mobile data |
| Play in a separate process | Video services | off <Badge type="warning" text="experimental" /> | A player crash does not close the launcher |

## Privacy

| Feature | Server contacted | When |
| --- | --- | --- |
| Local videos | None | Always local |
| Posters, descriptions | `en.wikipedia.org`, or `api.themoviedb.org` and `image.tmdb.org` with a key | Once per title, then cached |
| Subtitles | `rest.opensubtitles.org` and `dl.opensubtitles.org` (old API, no account), `www.podnapisi.net`, and `api.opensubtitles.com` only if you entered a key | Only when you search, or if automatic download is on. Sent: title, season, episode, year, languages, and the file hash (a number computed from the video, not the video) of local files |
| Network videos | The server of the storage you set up in Telos Files | When you scan or play them |
| Trakt | `api.trakt.tv` | Only if you connected Trakt |
| Web streams | The address you entered | When you play it |
| Torrents | Trackers and peers of that torrent | Only while the torrent plays |

Subtitle requests follow at most 5 redirects and refuse redirects to loopback, link-local or private addresses unless the first address was one too.

Video titles taken from file names are sent to the metadata, subtitle and Trakt services you use. Secrets such as keys
are entered by you and kept on the device, encrypted with the Android Keystore.

## Feature matrix

| Feature | Where | Status |
| --- | --- | --- |
| Library by movies, series, folders | Tabs | stable |
| Continue watching and resume | Library tab | stable |
| Posters and descriptions | Movies, Series | stable, needs network |
| Gestures, speed, picture size, audio tracks, repeat | Player | stable |
| Picture in picture | Player | stable |
| FFmpeg audio decoders | Player | stable |
| Web streams (HLS, DASH, MP4, RTSP) | Link icon | stable |
| Torrents | Link icon, magnet links | stable, depends on peers |
| Subtitles from a file, and online search without an account | Player | stable (Podnapisi untested) |
| Subtitle sources with order, file hash search, UTF-8 conversion, cache per video | Video services | stable |
| Subtitle delay, size, colour and edge | Playback options | stable (delay: external subtitles only) |
| Picture modes (fit, fill, zoom, fixed width or height), frame rate matching | Playback options | stable, frame rate depends on the phone |
| Mark as watched, delete from phone | Long press in lists | stable |
| Videos from network storages, library folders with rescan | Video services, Telos Files | new, not tested on every protocol |
| Trakt.tv | Video services | optional |
| Separate player process | Video services | <Badge type="warning" text="experimental" /> |
| Chromecast, DLNA, downloads for offline viewing | | Not available |

## Supported formats

| Item | Support |
| --- | --- |
| Video containers and codecs | Whatever Android can decode (typically MP4, MKV, WebM, 3GP) |
| Audio extras | FFmpeg decoders for AC3, E-AC3, DTS, TrueHD and more |
| Streams | HLS, DASH, RTSP, plain links |
| Subtitles | SRT, VTT, ASS / SSA, TTML, embedded tracks |
| Torrents | Magnet links, `.torrent` addresses and files |

## Limitations

- Video decoding depends on your phone hardware. Some codecs may not play.
- TMDB needs your own key for ratings. Subtitle search works without an account, but the free sources have daily download limits, and the old OpenSubtitles API may be switched off by its owner. Only OpenSubtitles.com needs an account.
- Not available: audio delay, a switch for audio passthrough, forced screen mode switching for frame rate matching, an Android TV interface, UPnP / DLNA browsing, automatic scraping of network videos for durations and thumbnails.
- Title detection relies on file names. Poorly named files can get a wrong poster or none.
- Torrent streaming needs enough healthy peers. Quality depends on the source and your connection.
- The torrent and stream features show no catalog: you must supply the address.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Library empty | Grant the video permission and make sure the files are indexed by Android |
| Series not recognised | Name files like `Show.S01E02.mkv` |
| "Torrent streaming is set to Wi-Fi only" | Connect to Wi-Fi or switch the option off |
| Torrent does not start | The torrent may have no peers. Try another source |
| No subtitle results | Check the language codes and that at least one source is switched on. A source that fails is named in the dialog. Try **Search all sources**, or an OpenSubtitles User-Agent of your own |
| No sound for a track | Pick another audio track in the options |
