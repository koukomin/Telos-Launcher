# Library and playback

How the music library is built and searched, how tapping a track makes a queue, and every control of the mini player,
the full player, the notification and the sleep timer. Lyrics, tags and scrobbling are on
[Tags, lyrics and scrobbling](./tags-lyrics-scrobbling).

## Library

The library is read from Android's media library: every audio file that is marked as music and has a duration above
zero, sorted by title without regard to case.

| Tab | What you see |
| --- | --- |
| **Songs** | Every track, sorted by title, with its duration |
| **Albums** | Cover grid. The card title is the album and the subtitle the artist. Open an album to see its tracks in track-number order |
| **Artists** | Artists with a song count ("N songs"). Open one to see all their tracks by title |

- Tracks without an album or artist are grouped under "Unknown album" and "Unknown artist". The artist value
  `<unknown>` that Android uses is treated as empty.
- Albums are grouped by the album id of the media library, so two albums with the same name by different artists
  stay separate.
- The back arrow, or Android's Back, leaves an album or artist view first, then closes the full player, then leaves
  Telos Music.
- Cover art comes from the file (or from the media library's album art).
- The library loads when the screen opens. To pick up new files, leave and reopen the screen after Android has
  scanned them.

### Search

The field "Search music" filters title, artist and album at once.

| Property | Behavior |
| --- | --- |
| Case and accents | Ignored. Greek text matches without stress marks and without regard to final sigma |
| Scope | The current tab: songs, album names and artist names are filtered from the matching tracks |
| Clear | The X button in the field |

## Starting playback

Tap a track. The **list you tapped in becomes the play queue**, and playback starts at that track.

| Where you tap | Queue |
| --- | --- |
| Songs tab | All songs of the current (filtered) list |
| Inside an album | The album's tracks in track order |
| Inside an artist | The artist's tracks by title |

The queue, the position, shuffle and repeat are saved, so playback comes back paused where you left it after the player stops. There are no user-defined playlists and no "play next" or "add to queue". To change the queue, start again from a
different list.

## The mini player

Shown at the bottom while something is loaded. It has the title, the artist, the cover, play or pause and next.
Tap the bar to open the full player.

## The full player

| Control | Behavior |
| --- | --- |
| Cover | Large cover art. **Lyrics** replaces it with the lyrics. A placeholder is shown when there is no art |
| Progress bar | Drag to seek. The elapsed time and the duration are shown |
| Previous, Next | Skip within the queue |
| Play or pause | Toggles playback |
| Shuffle | Random order of the queue. The icon is highlighted when it is on |
| Repeat | Cycles through off, repeat all and repeat one |
| Lyrics | Shows or hides lyrics, see [Lyrics](./tags-lyrics-scrobbling#lyrics) |
| Sleep | The sleep timer, below. The button shows "Sleep" with a check mark while a timer runs |
| Edit | Opens the [tag editor](./tags-lyrics-scrobbling#tag-editor) for the current song |
| Close | Returns to the library |

### Sleep timer

Tap **Sleep** and choose "Stop in 15, 30, 45, 60 or 90 minutes", or **Turn timer off**. When the time is up, playback
is **paused**; nothing is closed or deleted. Starting a new timer replaces the previous one. The timer runs in the
launcher process together with the player service.

## System integration

| Surface | Behavior |
| --- | --- |
| Notification | Title, artist, cover and the play, pause, previous and next buttons, created by the media session |
| Lock screen | The same controls and a seek bar |
| Headset buttons | Play, pause, next, previous |
| Becoming noisy | Playback pauses when headphones are unplugged or a Bluetooth device disconnects |
| Audio focus | Other apps' audio (a call, a video) pauses or ducks the music and it resumes afterwards |
| Launcher media control | The launcher's [media control](../../integrations/mediacontrol) widget and the Music widget show and control it |
| Background | The service keeps playing with the screen off and stops by itself when nothing is playing |

Android 13 and later ask for the notification permission when Music opens.

## Performance and storage

- The queue holds media items for the tracks you started from. A very large library is loaded in one go.
- No audio is copied or converted. Files are streamed from the media library.
- The player needs no network. Lyrics and scrobbling use it separately.

## Settings summary

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Shuffle | Full player | off | Randomizes the queue |
| Repeat | Full player | off | Off, all, one |
| Sleep timer | Full player | off | 15, 30, 45, 60, 90 minutes |
| Allow access | Library | not granted | Reads the music library |

## Limitations

- No playlists, no queue editing, no crossfade, no equalizer, no gapless settings, no folder view.
- The library excludes non-music audio and files in `.nomedia` folders.
- No sorting options: songs by title, albums by name and artists by name.
- There is no saved queue: it exists only while the player service is running.
- Playback depends on the codecs of the phone.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "No music found on this device" | Files must be indexed as music. Restart the phone or trigger a media scan with a file manager |
| A song is missing | It has no duration tag, is in a `.nomedia` folder, or is marked as a ringtone or recording |
| A song will not play | The codec is not supported on this phone. Try another file |
| Playback stops by itself | The sleep timer ran out, or Android stopped the service in battery saver |
| Controls missing in the notification | Allow notifications for Telos |
| Album shows twice | The files have different album ids. Fix the tags so they match, then rescan |
| Search finds nothing in Greek | The text must exist in title, artist or album. Accents and capitals are ignored |
