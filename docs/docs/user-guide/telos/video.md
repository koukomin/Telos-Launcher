# Telos Video

A video library and player. It plays the videos on your phone, web streams, and torrents while they download.

::: tip At a glance
Library with movies, series, folders and continue watching. Full screen player with gestures, subtitles
and picture in picture. Optional posters (Wikipedia or TMDB), subtitles (OpenSubtitles) and Trakt.tv.
:::

## What it is

Telos Video reads the videos Android has indexed, groups them into a library, and plays them with
Media3 / ExoPlayer. It is also registered with Android as a handler for video files, `magnet:` links and
`.torrent` files, so other apps and browsers can open them in it.

## Getting started and permissions

| Permission | Why |
| --- | --- |
| Video files (`READ_MEDIA_VIDEO`, or storage on Android 12 and older) | Build the library. Asked on first open |
| Internet | Web streams, torrents, posters, subtitles, Trakt. Used only when you use those features |
| Notifications | Android 13 and later, for the foreground service |

Open **Telos Video** and tap **Allow**. The library permission is only needed for the library.

## Library

| Tab | Details |
| --- | --- |
| **Library** | "Continue watching" (up to 10 videos you stopped part-way) above "All videos", newest first |
| **Movies** | Poster grid of titles detected from file names |
| **Series** | Poster grid of shows, recognised from names like `Show.S01E02.mkv` or `Show 1x02` |
| **Folders** | Videos grouped by the folder they are in |

- **Search videos** filters the current list.
- Name recognition works on the file name: the title is what comes before `S01E02` (or `1x02`) or before a
  year such as `2019`. Dots, underscores and dashes are treated as spaces.
- Resume positions are remembered per file, and a video is shown with a progress mark.
- When you open a title, **Add to Trakt watchlist** adds it to your Trakt watchlist (needs a connected Trakt account).

::: details Posters and descriptions
Without any key, posters and descriptions come from **Wikipedia** (English). With your own free
**TMDB API key**, TMDB is used instead, which finds more titles and adds ratings. Answers are saved
on the device, so each title is asked for only once. This product uses the TMDB API but is not
endorsed or certified by TMDB.
:::

## Playing from the web

Tap the **link** icon in the top bar ("Play from the web").

- Enter the address of a video or stream (HLS, DASH, MP4 and other plain video links), a magnet link, or the
  address of a `.torrent` file. **Paste** inserts the clipboard.
- A magnet link, `http://` or `https://` address on the clipboard is filled in automatically when the
  dialog opens.
- **Choose .torrent file** opens a file picker.

## Torrents

Telos Video uses libtorrent (through libtorrent4j) to fetch the pieces of the chosen video in order. The
player reads them through a small web server that listens only on `127.0.0.1` of your phone.

- Nothing starts until you open a torrent.
- By default it **only runs on Wi-Fi**. On mobile data it refuses to start. This can be changed in Video services.
- The player shows peers, speed and progress. Opening can take a minute.
- Everything is stopped and the downloaded data is **deleted when the player closes**.

::: warning Only play content you may watch
Torrents are a way to receive files. Use them only for content you are allowed to watch. Torrent
networks show your IP address to other peers.
:::

## Player

### Gestures

| Gesture | Action |
| --- | --- |
| Swipe horizontally | Seek |
| Swipe up or down on the left half | Brightness |
| Swipe up or down on the right half | Volume |
| Double tap left or right | Jump 10 seconds back or forward |
| Press and hold | Double speed while held |

### Playback options (the three-dot menu)

| Option | Details |
| --- | --- |
| Speed | Presets, selected with chips |
| Picture | Fit, Fill or Zoom |
| Audio | Choose between the audio tracks of the file |
| Subtitles | Off or one of the embedded tracks |
| Repeat this video | On or off |
| Sleep timer | Off or a number of minutes |

- **Picture in picture** starts when you leave the app (for example with the home gesture) while a video is playing.
- FFmpeg software decoders take over for audio such as AC3, E-AC3, DTS and TrueHD when the phone has no decoder.
- A playlist of videos from the library plays in order.

## Subtitles

Open the menu and choose **Subtitles...**:

- **From a file**: pick a subtitle file. SRT, VTT, ASS / SSA and TTML are recognised.
- **Search online**: searches OpenSubtitles using the title (and season and episode for series) in the languages you set.

OpenSubtitles needs **your own API key and your account** (user name and password), because their API
requires them for downloads. Without a key the dialog tells you to add it in Video services.
Subtitles can also be fetched automatically if you switch that on, including for torrents.

## Trakt.tv

<Badge type="info" text="optional" /> Create an application at trakt.tv/oauth/applications (redirect address
`urn:ietf:wg:oauth:2.0:oob`), enter its client id and secret, tap **Connect Trakt** and enter the shown code
at trakt.tv/activate. Then:

- movies and episodes are scrobbled while you watch,
- watched videos are marked in the library,
- you can add a title to your watchlist.

You can pause scrobbling with the switch or **Sign out**.

## Settings

Tap the **gear** icon ("Video services").

| Setting | Meaning |
| --- | --- |
| TMDB API key | Use TMDB for posters, descriptions and ratings |
| OpenSubtitles key, user, password | Subtitle search and download |
| Languages | For example `el,en` |
| Download subtitles automatically | Fetches a subtitle without asking |
| Trakt | Client id and secret, connect, scrobbling switch, sign out |
| Torrents: Only on Wi-Fi | On by default |
| Play in a separate process | <Badge type="warning" text="experimental" /> A player crash then does not close the launcher. Videos opened from other apps always use the normal player |

## Privacy

| Feature | Server contacted | When |
| --- | --- | --- |
| Local videos | None | Always local |
| Posters, descriptions | `en.wikipedia.org`, or `api.themoviedb.org` and `image.tmdb.org` with a key | Once per title, then cached |
| Subtitles | `api.opensubtitles.com` | Only when you search, or if automatic download is on |
| Trakt | `api.trakt.tv` | Only if you connected Trakt |
| Web streams | The address you entered | When you play it |
| Torrents | Trackers and peers of that torrent | Only while the torrent plays |

Video titles taken from file names are sent to the metadata, subtitle and Trakt services you use.
Secrets such as keys are entered by you and kept on the device.

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
