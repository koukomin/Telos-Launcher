# Telos Music

A music player for the songs stored on your phone, with synchronized lyrics, scrobbling and a tag editor. It plays
local files only: there is no streaming service and no account of its own.

::: tip At a glance
Library by songs, albums and artists, a play queue with shuffle and repeat, a sleep timer, lyrics from
[LRCLIB](https://lrclib.net), scrobbling to Last.fm, Libre.fm and ListenBrainz, and a tag editor.
:::

::: info This section
- This page: what it is, permissions, the screens, privacy and the feature matrix.
- [Library and playback](./library-playback): the library tabs, search, the queue, player controls and the sleep timer.
- [Tags, lyrics and scrobbling](./tags-lyrics-scrobbling): the tag editor, LRCLIB lyrics, and the three scrobbling services.
:::

## What it is

Telos Music reads the audio files that Android has indexed (the system media library) and plays them with a media
session service built on Media3 / ExoPlayer. Because it is a standard media session, the notification, the lock
screen and headset buttons control it, and it also shows up in the launcher's
[media control](../../integrations/mediacontrol). It is a [virtual app](../#how-the-built-in-apps-work) and is
guarded by the crash guard. Switching it off in [Telos Store](../store/) disables its services and screens, so it
costs no memory or CPU.

## Getting started and permissions

| Permission | Why | When |
| --- | --- | --- |
| Audio files (`READ_MEDIA_AUDIO`, or storage access on Android 12 and older) | Build the library | Asked on first open. Without it the screen only shows "Allow access to your music to build the library" and an **Allow** button |
| Notifications | Playback notification and controls | Android 13 and later |
| Foreground service of type media playback | Keep playing with the screen off | Declared by the app |
| Internet | Lyrics and scrobbling only | Never used for playing local files |
| Write access to a file | Tag editor | Asked per file on Android 11 and later, see [Tags](./tags-lyrics-scrobbling#tag-editor) |

Open **Telos Music**, tap **Allow**, and the library is built from your device.

::: warning Only indexed music is listed
The library comes from the system media library and includes only files that Android marks as music and that have a
duration. Files in a folder with a `.nomedia` marker, or files that the media scanner has not found yet, do not
appear. Voice recordings and ringtones are normally excluded.
:::

## A tour of the screens

### Library screen

| Element | What it does |
| --- | --- |
| Title "Music" | The screen name |
| Search field "Search music" | Filters the current tab, see [Library and playback](./library-playback#search) |
| Scrobbling button in the top bar | Opens the scrobble dialog |
| Tabs **Songs**, **Albums**, **Artists** | The three views of the library |
| Mini player at the bottom | Appears while something is loaded. Tap it to open the full player |

### Full player

Cover (or lyrics), title, artist, a progress bar with times, and the buttons Shuffle, Previous, Play or Pause, Next,
Repeat, and Lyrics, Sleep and Edit. See [Library and playback](./library-playback#the-full-player).

### Dialogs

- **Edit tags** with fields for title, artist, album, album artist, genre, year, track number and **Change cover...**
- **Sleep timer** with 15, 30, 45, 60 and 90 minutes
- **Scrobbling** with one block per service and a status line

## Settings

Telos Music has no settings page. The few options are in the top bar and the player:

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Shuffle | Full player | off | Random order of the current queue |
| Repeat | Full player | off | Off, repeat all, repeat one |
| Sleep timer | Full player | off | Pauses playback after the chosen time |
| Scrobbling services | Scrobble dialog | not connected | Per service on or off, with credentials |
| Enable or disable the app | [Telos Store](../store/) | on | Hides the icon and disables the services |

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
| Scrobbling | `ws.audioscrobbler.com` (Last.fm), `turtle.libre.fm`, the ListenBrainz server | Only for services you connected, with artist, title, album, length and time of play |

If you do not connect a scrobbling service, no listening data leaves the phone.

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Songs, albums and artists | Library tabs | Unknown album and artist are grouped |
| Accent-insensitive search, also Greek | Search field | |
| Queue from the list you tapped | Tap a track | No user playlists |
| Notification, lock screen and headset control | Media session | Pauses when headphones are unplugged |
| Shuffle, repeat, seek | Full player | |
| Sleep timer | Full player | 15 to 90 minutes |
| Synchronized lyrics | Full player | LRCLIB |
| Scrobbling with offline queue | Scrobble dialog | Last.fm, Libre.fm, ListenBrainz |
| Tag editor and cover change | Full player | MP3, FLAC, M4A, OGG <Badge type="warning" text="changes your files" /> |
| Playlists, podcasts, streaming, equalizer | | Not available |

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
Switch the app off in the [Store](../store/) if you do not use it. Its services and screens are then disabled.
:::
