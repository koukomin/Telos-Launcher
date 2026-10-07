# Telos Music

A music player for the songs stored on your phone, with synchronized lyrics, scrobbling and a tag editor.
It plays local files only: there is no streaming service and no account of its own.

::: tip At a glance
Library by songs, albums and artists, a play queue with shuffle and repeat, a sleep timer, lyrics from
[LRCLIB](https://lrclib.net), scrobbling to Last.fm, Libre.fm and ListenBrainz, and a tag editor.
:::

## What it is

Telos Music reads the audio files that Android has indexed (the system media library) and plays them
with a media session service. Because it is a standard media session, the notification, the lock screen
and headset buttons control it, and it also shows up in
[media control](../integrations/mediacontrol).

## Getting started and permissions

| Permission | Why | When |
| --- | --- | --- |
| Audio files (`READ_MEDIA_AUDIO`, or storage access on Android 12 and older) | Build the library | Asked on first open. Without it the screen only shows an "Allow" button |
| Notifications | Playback notification and controls | Android 13 and later |
| Internet | Lyrics and scrobbling only | Never used for playing local files |
| Write access to a file | Tag editor | Asked per file on Android 11 and later, see below |

Open **Telos Music**, tap **Allow**, and the library is built from your device.

::: warning Only indexed music is listed
The library comes from the system media library and includes only files that Android marks as music
and that have a duration. Files in a folder with a `.nomedia` marker, or files that the media scanner
has not found yet, do not appear. Voice recordings and ringtones are normally excluded.
:::

## Library

| Tab | What you see |
| --- | --- |
| **Songs** | Every track, sorted by title, with its duration |
| **Albums** | Cover grid. Open an album to see its tracks in track-number order |
| **Artists** | Artists with a song count. Open one to see all their tracks by title |

- Tracks without an album or artist are grouped under "Unknown album" and "Unknown artist".
- **Search** at the top filters title, artist and album at once. It ignores accents and capital letters and
  also works for Greek text (for example without stress marks).
- Tap a track to start playing. The list you tapped in becomes the play queue.

## Playback

| Feature | How to use it |
| --- | --- |
| Mini player | Shown at the bottom while something is loaded. Tap it for the full player |
| Play, pause, previous, next | Buttons in the full player, the notification, lock screen and headset |
| Seek | Drag the progress bar |
| Shuffle | Toggle in the full player. The icon is highlighted when it is on |
| Repeat | Cycles through off, repeat all and repeat one |
| Sleep timer | **Sleep** button: stop after 15, 30, 45, 60 or 90 minutes, or turn the timer off. The button shows a check mark while a timer runs |

::: details What happens when the sleep timer ends
Playback is paused when the timer expires. Nothing is closed or deleted.
:::

## Lyrics

Tap **Lyrics** in the full player to replace the cover with the lyrics. Synchronized lyrics highlight the
current line while the song plays. If a song only has plain lyrics, they are shown as normal text.
If nothing is found, you see "No lyrics found" and the button stays disabled.

- Lookup uses title, artist, album and duration of the track, so correct tags give better results.
- Results are cached on the device, so a song is only looked up once.
- Lyrics are fetched from [LRCLIB](https://lrclib.net), which needs no account or key.

## Scrobbling

Scrobbling reports what you listen to. Open the scrobble dialog with the button in the top bar of the
Music screen.

| Service | What you enter | Notes |
| --- | --- | --- |
| [Last.fm](https://www.last.fm) | Your own API key and shared secret (free, from last.fm/api/account/create), user name, password | The password is only used to sign in and is not stored |
| [Libre.fm](https://libre.fm) | User name and password | Only a hash of the password is stored |
| [ListenBrainz](https://listenbrainz.org) | User token from your profile page, optional server address | Defaults to `https://api.listenbrainz.org` |

How it behaves:

- A "now playing" message is sent when a song starts.
- A scrobble is sent once **half of the song or 4 minutes** has been played, whichever comes first. Songs
  shorter than 30 seconds are never scrobbled.
- If a scrobble cannot be sent (for example no network), it is kept in a queue and sent with the next
  successful scrobble.
- Each service can be switched on or off separately. Switched-off services keep their queued scrobbles.
- Secrets are stored encrypted on the device.

## Tag editor

Tap **Edit** in the full player to change the tags of the current song: title, artist, album, album artist,
genre, year and track number, and the cover art (**Change cover**).

<Badge type="warning" text="changes your files" />

- The editor writes to the audio file itself. Keep a backup of music you care about.
- On Android 11 and later the system asks for permission to modify each file the first time.
- The editor reads and writes through the TagLib library. It is intended for **MP3, FLAC, M4A and OGG**
  files. Other formats may fail with "Could not save the tags".
- The library list may need a moment (or a rescan by Android) before it shows the new tags.

## Supported formats

| Item | Support |
| --- | --- |
| Playback | Formats the Android media player (Media3 / ExoPlayer) can decode on your device, typically MP3, AAC / M4A, FLAC, OGG, Opus and WAV |
| Tag editing | MP3, FLAC, M4A, OGG |
| Lyrics | Synchronized (`LRC` style) and plain text from LRCLIB |

## Privacy

| Feature | Server contacted | When |
| --- | --- | --- |
| Playing, searching, browsing | None | Everything runs on the device |
| Lyrics | `lrclib.net` | When a song is shown the first time. Sends title, artist, album and duration. Cached afterwards |
| Scrobbling | `ws.audioscrobbler.com` (Last.fm), `turtle.libre.fm`, ListenBrainz server | Only for services you connected, with artist, title, album, length and time of play |

If you do not connect a scrobbling service, no listening data leaves the phone.

## Limitations

- Local files only. There is no online catalog, playlist import or podcast support.
- No user-defined playlists: the queue is the list you started from.
- The tag editor needs a supported file type and write permission.
- Lyrics depend on what LRCLIB knows, and need a network connection the first time.
- Queued scrobbles are only retried when the next scrobble is sent, not on a schedule.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Allow access to your music" keeps showing | Grant the audio permission in Android settings for Telos |
| "No music found on this device" | Files must be indexed as music. Restart the phone or use a file manager to trigger a media scan |
| No lyrics | Check the title and artist tags and your network connection |
| Scrobbles do not arrive | Open the scrobble dialog and check that it says "Connected". For Last.fm make sure the key and secret are yours |
| Tags cannot be saved | Accept the system permission prompt, and check that the format is supported |
| Playback controls missing | Allow notifications for Telos |

::: tip
Switch the app off in the [Store](./store) if you do not use it. Its services and screens are then disabled.
:::
