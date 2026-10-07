# Telos Music

A player for the music stored on your phone, with lyrics, scrobbling and a tag editor.

## Library

- Browse by **songs, albums and artists**.
- Accent-insensitive search, also for Greek.

## Playback

| Feature | Details |
| --- | --- |
| Play queue | Shuffle and repeat |
| Notification and lock screen | Standard media controls, also usable from [media control](../integrations/mediacontrol) |
| Sleep timer | Stops playback after a set time |

## Lyrics

Synchronized lyrics from [LRCLIB](https://lrclib.net). Lines are highlighted while the song plays.
Lyrics are looked up online, so this needs a network connection.

## Scrobbling

Reports what you listen to to these services:

| Service | Notes |
| --- | --- |
| [Last.fm](https://www.last.fm) | Sign in from the scrobble dialog |
| [Libre.fm](https://libre.fm) | Same |
| [ListenBrainz](https://listenbrainz.org) | Same |

Scrobbles are queued when you are offline and sent later.

## Tag editor

Edit tags in **MP3, FLAC, M4A and OGG** files: title, artist, album, genre, year, track number and cover art.

::: warning
The tag editor changes your files. Keep a backup of music you care about.
:::

## Permissions

Access to audio files (media permission) and notifications.

## Tips

- If a lyric is missing, check the tags; lookup uses title and artist.
- Switch the app off in the [Store](./store) to free all resources.
