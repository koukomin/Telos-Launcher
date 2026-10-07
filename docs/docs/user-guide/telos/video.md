# Telos Video

A video library and player that also plays web streams and torrents.

## Library

| Section | Details |
| --- | --- |
| Movies | Detected from your files |
| Series | Recognised from names like `Show.S01E02.mkv` |
| Folders | Browse by folder |
| Continue watching | Resume where you stopped |

Posters and descriptions come from [Wikipedia](https://www.wikipedia.org), or from
[TMDB](https://www.themoviedb.org) if you enter your own free key.

## Player

### Gestures

| Gesture | Action |
| --- | --- |
| Swipe horizontally | Seek |
| Swipe on the left | Brightness |
| Swipe on the right | Volume |
| Double tap | Jump 10 seconds |
| Press and hold | Double speed |

### Controls

Speed presets, picture size (fit, fill, zoom), audio and subtitle track choice, repeat, sleep timer and
picture in picture. FFmpeg software decoders handle AC3, E-AC3, DTS, TrueHD and more.

The player is also used when another app opens a video.

::: tip Separate process
In Video services you can turn on "Play in a separate process", so a crash of the player does not close the launcher.
:::

## Subtitles

- Select embedded subtitle tracks.
- Load external subtitle files.
- Search, download or fetch automatically from [OpenSubtitles](https://www.opensubtitles.com), also for
  torrents. This needs **your own API key**.

## Web streams

HLS, DASH, RTSP and plain video links.

## Torrents

Magnet links, `.torrent` addresses and files.

- The video downloads in order while it plays.
- It is served through a local-only address, on Wi-Fi only by default.
- Everything is deleted when the player closes.
- Magnet links open in Telos Video from any app or browser. A magnet link or address on the clipboard is filled in automatically.

::: warning
Only play content you are allowed to watch. Torrenting shares your IP address with peers.
:::

## Trakt.tv

Sign in with a device code. Scrobbling of movies and episodes, watched marks in the library, and adding
titles to your watchlist.

## Privacy

Online features (posters, subtitles, Trakt) contact those services only if you use them and, where
needed, enter your keys.

## Limitations

- TMDB and OpenSubtitles need your own keys.
- Streaming quality depends on the source and your connection.
