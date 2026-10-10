# Tags, lyrics and scrobbling

The tag editor, synchronized lyrics from LRCLIB, and scrobbling to Last.fm, Libre.fm and ListenBrainz. These are the
parts of Telos Music that write files or use the network. The library and the player are on
[Library and playback](./library-playback).

## Tag editor

<Badge type="warning" text="changes your files" /> Tap **Edit** in the full player to change the tags of the **current song**.

| Field | Stored as | Notes |
| --- | --- | --- |
| Title | `TITLE` | |
| Artist | `ARTIST` | Scrobbling needs title and artist |
| Album | `ALBUM` | |
| Album artist | `ALBUMARTIST` | |
| Genre | `GENRE` | |
| Year | `DATE` | |
| Track number | `TRACKNUMBER` | |
| **Change cover...** | Front cover picture | Pick a JPEG or PNG picture, it replaces the cover of the file |

### Steps

1. Play the song and open the full player, then tap **Edit**. The dialog "Edit tags" opens with the current values.
2. Change the fields. A field you empty **removes that tag** from the file.
3. Tap **Save**. On Android 11 and later the system first asks for permission to change that file.
4. Optionally use **Change cover...** and choose a picture. It is written to the file at once.

### Details and limits

- The editor writes **to the audio file itself** through the TagLib library. Keep a backup of music you care about.
- It is intended for **MP3, FLAC, M4A and OGG** files. Other formats may fail with "Could not save the tags".
- The library list may need a moment, or a rescan by Android, before it shows the new tags.
- The cover replaces all existing pictures of the file with one "Front Cover".
- Reading and writing use the same permission: Android 11 and later ask per file, older versions use the storage
  permission.
- The editor does not change file names or folders.

## Lyrics

Tap **Lyrics** in the full player to replace the cover with the lyrics.

| Case | What you see |
| --- | --- |
| Synchronized lyrics found | The current line is highlighted in the theme colour and bold, and the list scrolls to keep it near the top |
| Only plain lyrics found | The text as a normal scrolling page |
| Nothing found | "No lyrics found" |

### How lookup works

- Lyrics are looked up as soon as a song starts, not only when you tap **Lyrics**.
- The request uses title, artist, album and duration (in whole seconds) of the track, so correct tags give better
  results. A song without a title is skipped.
- The server is [LRCLIB](https://lrclib.net), an open database that needs no account or key. The request is
  `GET https://lrclib.net/api/get` with the user agent "Telos Launcher" and an 8 second timeout.
- A successful answer is cached on the device (in the app cache, one file per artist, title and album) so the song is
  asked for only once. A song with no result is **not** cached and is asked for again the next time.
- Timestamps in the `[mm:ss.xx]` form are read, also several stamps on one line. Lines are sorted by time.

### Tips

- Fix the title and artist tags with the tag editor if lyrics do not show.
- Lyrics need a network connection the first time. Clearing the Telos cache removes the cached lyrics.

## Scrobbling

Scrobbling reports what you listen to. Open the **Scrobbling** screen with the button (a gear icon) in the top bar of the
Music screen, from the overflow menu of Telos Radio, or from Settings > Comms > Tools. The screen has separate switches
**Scrobble music** (after an upgrade it is on only if a service was already active, otherwise off) and **Scrobble radio**
(off), a status line, and the queue with **Clear queue**. Nothing is sent until a service is signed in and switched on.
Track titles and artists go to the service you choose (opt-in network traffic). Redirects are not followed, and a
ListenBrainz server must use https (http only for a server in your own network).

| Service | What you enter | Notes |
| --- | --- | --- |
| [Last.fm](https://www.last.fm) | Your own API key and shared secret (free, from `last.fm/api/account/create`); **Sign in with browser** (set the Callback URL of your Last.fm API account to `telos-lastfm://callback`, shown in the app with a Copy button) or **Use password instead** with user name and password | The password is only used to sign in and is not stored. A session key is kept instead |
| [Libre.fm](https://libre.fm) | User name and password | Only an MD5 hash of the password is stored, because the protocol needs it |
| [ListenBrainz](https://listenbrainz.org) | User token from your profile page, optional server address | The server defaults to `https://api.listenbrainz.org`. Use your own server for self-hosted instances |

### Connect a service

1. Open the Scrobbling screen.
2. Fill in the block of the service and tap **Connect Last.fm**, **Connect Libre.fm** or **Save ListenBrainz**.
3. The status line says "Last.fm connected", "Libre.fm connected" or "ListenBrainz saved". A failure shows the
   reason, for example "login failed".
4. The block now says **Connected** and its switch can be turned on or off. **Not connected** keeps the switch off.

### What is sent and when

| Event | Action |
| --- | --- |
| A song starts playing | A "now playing" message is sent once per song |
| **Half of the song or 4 minutes** have been played, whichever comes first | The scrobble is sent, once per song |
| Song shorter than 30 seconds | Never scrobbled |
| Song without title or artist tag | Never scrobbled |
| Pausing | Counting stops. The played time only grows while the song is playing |
| Skipping before the threshold | No scrobble |

The scrobble carries the artist, title, album, duration and the **time the song started** (computed from now minus
the time played).

### Telos Radio

With **Scrobble radio** on, the current track of a station is reported when the stream announces it (ICY `StreamTitle` or
the stream's artist and title; "Artist - Title" is split). "Now playing" is sent when a track starts and the scrobble after
**60 seconds** of listening. Empty titles, titles equal to the station name and titles without an artist are ignored, and the
same track is not scrobbled twice within 10 minutes. Stations that do not announce the track never scrobble. Libre.fm
receives radio scrobbles with source `R` and no length.

### Offline queue

- If a scrobble cannot be sent (for example no network), it is kept in a queue of up to **500** entries.
- The queue is sent with the **next successful scrobble**, before the new one, in order. It is not retried on a
  schedule or when the network comes back.
- Each service can be switched on or off separately. A switched-off or disconnected service keeps its queued
  scrobbles until it is switched on again.

### Where scrobbling runs

The tracker lives in the player service. It checks every 5 seconds while a song is playing and works with the
screen off.

### Settings and storage

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Last.fm on or off | Scrobbling screen | off | Sends to Last.fm |
| Libre.fm on or off | Scrobbling screen | off | Sends to Libre.fm (`turtle.libre.fm`) |
| ListenBrainz on or off | Scrobbling screen | off | Sends to the ListenBrainz server |
| ListenBrainz server | Scrobbling screen | `https://api.listenbrainz.org` | Trailing slashes are removed |

Secrets (shared secret, session key, password hash, token) are stored **encrypted with a Keystore key** on the
device. The queue and the other fields are kept in the app's private preferences.

## Privacy

| Feature | Server | Data sent |
| --- | --- | --- |
| Lyrics | `lrclib.net` | Title, artist, album, duration |
| Last.fm | `ws.audioscrobbler.com` | Artist, title, album, duration, time, your session |
| Libre.fm | `turtle.libre.fm` | The same, your user name and a hash |
| ListenBrainz | Your chosen server | The same, with your token |

Nothing is sent when no service is connected and switched on.

## Limitations

- Only Last.fm, Libre.fm and ListenBrainz. No love, no history view, no import of old scrobbles.
- Scrobbles are not retried by time, only with the next scrobble.
- The tag editor needs a supported format and write permission.
- Lyrics depend on LRCLIB and are not editable.
- No "now playing" retry; if it fails it is skipped.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Scrobbles do not arrive | Open the dialog and check that it says "Connected" and the switch is on. For Last.fm use your own key and secret |
| "login failed" | Wrong user name or password, or a wrong API key or secret |
| ListenBrainz does not accept the token | Copy it again from `listenbrainz.org/profile` and check the server address |
| A song never scrobbles | It is shorter than 30 seconds, or its title or artist tag is empty |
| Tags cannot be saved | Accept the system permission prompt, and check that the format is supported |
| "Could not save the tags" | The format is not supported by the tag library, or write permission was denied |
| No lyrics | Check the title and artist tags and your network connection |
| Lyrics line is out of sync | The lyrics are for another version of the song. Nothing can be adjusted in the app |

### Last.fm browser sign-in

Last.fm opens in the browser, you log in and approve there, and you return to Telos; only the session key and the user name are stored (encrypted), the password is never seen. Last.fm sends no verification value back, so the answer is accepted only within 10 minutes of tapping the button, and only once. During that window another app could send a forged callback; it could only link a token that was authorized for your own API key.
