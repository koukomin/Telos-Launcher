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
| OpenSubtitles key, user, password | Video services | empty | Subtitle search and download |
| Languages | Video services | `en` | Comma separated codes, most wanted first |
| Download subtitles automatically | Video services | off | Fetches a subtitle without asking |
| Trakt | Video services | not connected | Client id and secret, connect, scrobbling switch, sign out |
| Torrents: Only on Wi-Fi | Video services | on | Refuses to start a torrent on mobile data |
| Play in a separate process | Video services | off <Badge type="warning" text="experimental" /> | A player crash does not close the launcher |

## Privacy

| Feature | Server contacted | When |
| --- | --- | --- |
| Local videos | None | Always local |
| Posters, descriptions | `en.wikipedia.org`, or `api.themoviedb.org` and `image.tmdb.org` with a key | Once per title, then cached |
| Subtitles | `api.opensubtitles.com` | Only when you search, or if automatic download is on |
| Trakt | `api.trakt.tv` | Only if you connected Trakt |
| Web streams | The address you entered | When you play it |
| Torrents | Trackers and peers of that torrent | Only while the torrent plays |

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
| Subtitles from a file or OpenSubtitles | Player | needs your own key |
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
- TMDB and OpenSubtitles need your own keys, and OpenSubtitles needs an account.
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
| No subtitle results | Add your OpenSubtitles key, user and password, and check the language codes |
| No sound for a track | Pick another audio track in the options |
