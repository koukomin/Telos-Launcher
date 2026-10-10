# Library and player

The video library with its four tabs, how titles are recognized from file names, resume and continue watching, and the
full screen player: gestures, playback options, tracks, picture in picture and the optional separate process. Web
addresses, torrents, subtitles, TMDB and Trakt are on
[Streams, torrents and subtitles](./streams-torrents-subtitles).

## The library

The library reads every video Android has indexed, newest added first, with no minimum length.

| Tab | Details |
| --- | --- |
| **Library** | "Continue watching" (up to 10 videos you stopped part-way) above "All videos", newest first. Each row shows a progress bar and a watched mark |
| **Movies** | Poster grid of titles detected from file names. Files of the same title and year are grouped |
| **Series** | Poster grid of shows, recognised from names like `Show.S01E02.mkv` or `Show 1x02`, with "N episodes" |
| **Other** | Videos recognised as neither a movie (a year in the name) nor an episode, so a file like `Inception.mkv` is listed here |
| **Folders** | Videos grouped by the folder they are in ("Other" when there is none), with "N videos" |

- **Search videos** filters the current list.
- Tap a movie, series or folder to open its detail view: a back arrow, the poster, title and description, the button
  **Add to Trakt watchlist**, and the files. Tap a file to play. A series lists its episodes under **Season N**
  headings.
- Progress marks and watched marks: a video with more than 2 percent watched shows a bar, and a title that Trakt
  reported as watched shows a mark.
- Without the video permission the screen shows an **Allow** button. "No videos found on this device" and
  "No movies found" explain empty tabs; the Series tab says "No series recognised. Name files like
  Show.S01E02.mkv".

### Name recognition

Recognition works on the **file name** only, never on the contents.

| Pattern | Result |
| --- | --- |
| `Show.Name.S01E02.mkv`, `Show Name s1e2` | Series "Show Name", season 1, episode 2 |
| `Show Name 1x02` | Series, season 1, episode 2 |
| `Movie Title 2019 1080p.mp4`, `Movie.Title.(2019)` | Movie "Movie Title", year 2019 |
| Anything else | The whole name, cleaned, as a movie without a year |

Dots and underscores are treated as spaces, and the text before the season/episode marker or the year is the title.
Quality tags after the year are ignored. A poorly named file can get a wrong poster or none; rename it to fix this.

### Posters and descriptions

| Source | When | Result |
| --- | --- | --- |
| Wikipedia (English) | No TMDB key | Poster and description from the matching article |
| TMDB | You entered a TMDB API key | More titles found, plus ratings |

Answers are saved on the device (`video_metadata.json`), one entry per title and source, so each title is asked
for only once. Adding a TMDB key later fetches TMDB posters even for titles that already have a Wikipedia answer.
This product uses the TMDB API but is not endorsed or certified by TMDB.

## Resume and continue watching

- The position of every video is saved per file when you leave, pause or switch.
- A video resumes where it stopped when it was between **2 percent and 95 percent** watched. Outside that range it
  starts from the beginning.
- "Continue watching" lists started and unfinished videos, most recently watched first, up to 10.
- A playlist (a series folder, or the videos of a Telos Files folder) saves progress when an item ends and moves to the
  next one.

### Mark as watched, delete

Long press a video in a list: **Mark as watched** / **Mark as not watched** (a watched video shows a check mark and
does not come back under continue watching) and **Delete from phone**. Delete asks first and then Android asks for
permission again; a deleted video is gone, there is no trash. Videos of network storages cannot be deleted from here.

### Network storages

**Video services > Network folders** lets you add folders of the network storages you set up in Telos Files (SMB,
FTP, SFTP, WebDAV, Nextcloud, ownCloud, the cloud app on this phone and the other storages). **Scan again** lists the
video files below the folders (three levels deep, up to 3000 files) and shows them in the library, folder named after
the storage. Video titles are recognised from the file names like for local files, but a scan reads no durations or
thumbnails. A video in Telos Files on a network storage also plays directly instead of being downloaded first, and
the videos next to it form the playlist.

Playing reads the file through the storage connection. There is no true range request: a jump forward reads through
the data in between on protocols that cannot seek, so jumping far on a slow link can take a while. Subtitles for such
videos are found by name only. The connection data of Telos Files is used; see [Files](../files/).

## The player

Open a video from the library, Telos Files, another app, or a web address. The screen is full screen (the system bars are hidden)
and stays on while the player is open.

### Layout

| Part | Details |
| --- | --- |
| Top bar | Back arrow, title, **Subtitles...** and a three-dot **Playback options** button. Shown with the controls |
| Transport bar | The standard player bar with play, pause, seek, previous and next in a playlist |
| Torrent line | "Peers N, N KB/s, N% downloaded" while a torrent plays |
| Gesture hint | A short overlay for seek, brightness and volume |

### Gestures

| Gesture | Action |
| --- | --- |
| Swipe horizontally | Seek. A swipe across the whole width moves 90 seconds. The hint shows the target time and the change |
| Swipe up or down on the left half | Brightness, from 2 percent to 100 percent |
| Swipe up or down on the right half | Volume of the music stream |
| Double tap left or right | Jump 10 seconds back or forward |
| Press and hold | Double speed while held, then back to the chosen speed |
| Tap | Shows or hides the controls |

### Playback options (the three-dot menu)

| Option | Details |
| --- | --- |
| Speed | 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x, chosen with chips |
| Picture | Fit, Fill, Zoom, Fixed width or Fixed height. The choice is remembered |
| Match frame rate | On (default) lets Android switch the screen refresh rate to the video frame rate when that is possible without a visible switch (Android 11 and later). Off never asks for it. Remembered |
| Audio | Choose between the audio tracks of the file (shown when there is more than one) |
| Subtitles | Off, or one of the embedded tracks |
| Subtitle delay | -0.5 s, -0.1, +0.1, +0.5 s for subtitles loaded from a file or the internet |
| Subtitle size, colour, edge | Four sizes, four colours, edge none, outline, shadow or box. Remembered |
| Repeat this video | On or off (repeat one) |
| Sleep timer | Off, 15, 30 or 60 minutes |

**Subtitles...** in the top bar adds an external file or searches online, see
[Subtitles](./streams-torrents-subtitles#subtitles).

### Decoding

- Media3 / ExoPlayer plays what the phone's hardware decoders support.
- **FFmpeg software decoders** take over for audio such as AC3, E-AC3, DTS and TrueHD when the phone has no decoder.
- A playlist of videos from the library plays in order.

### Picture in picture

Leaving the app (for example with the home gesture) while a video is **playing** starts picture in picture with a 16:9
window and hides the controls. Paused videos do not enter picture in picture.

## Separate process <Badge type="warning" text="experimental" />

**Video services > Player > Play in a separate process** (off by default) runs the player in its own Android process
(`:player`). A crash of the player then does not close the launcher.

| Topic | Behavior |
| --- | --- |
| Which videos | Videos opened from Telos (library, Files, web address) use it. Videos opened from other apps always use the normal player |
| Settings | The player process cannot read the launcher's settings, so a copy of the Video services is mirrored into a private file (keys stay encrypted) |
| Trakt | The login stays in the main process. The player sends scrobbles to it |
| Cleanup | When the player closes, the process stops the torrent and ends itself so nothing stays in memory |
| Cost | A little extra memory and a slightly slower start |

## Permissions and privacy

See [overview](./#getting-started-and-permissions). The player itself does no network access for local files.

## Limitations

- Decoding depends on the phone. Some 10-bit or exotic codecs may not play smoothly.
- Brightness changes apply to the player window only.
- Resume positions are per file path, so renaming a file loses its position.
- There is no equalizer, no audio delay setting, and no casting.
- The subtitle delay does not work for subtitle tracks inside the video file.
- Frame rate matching only asks for seamless switches; it never forces a screen mode change with a black flicker.
- Audio passthrough (AC3, DTS to an HDMI receiver) is left to Android and Media3, which use it automatically when the output supports it. There is no switch for it.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Library empty | Grant the video permission and make sure the files are indexed by Android |
| A series is shown as movies | Name files like `Show.S01E02.mkv` |
| Wrong poster | Rename the file with the correct title and year, or add a TMDB key |
| Video does not resume | It was below 2 percent or above 95 percent watched |
| No sound for a track | Pick another audio track in the options. FFmpeg handles many audio codecs |
| Picture in picture does not start | Only a playing video enters it, and the phone must support it |
| The player crashes the launcher | Turn on **Play in a separate process** |

## Organise into Movies and Series folders

The folder icon in the top bar opens an optional action (Android 11 or newer). It lists the recognised films and episodes
that would move, and only after **Move files** and a second Android permission dialog it moves them within MediaStore,
from `Movies/` or `Download/` to `Movies/Movies/<Title (Year)>/` and `Movies/Series/<Show>/Season NN/`. Files are never
deleted or changed, watch progress is kept, and subtitle files next to the videos are not moved. Nothing happens unless you start it.

## Home, shelves and sharing

The **Library** tab is a home: category chips (Movies, Series, Other, Recent, Unwatched, Folders, shown only when they have items), **Continue watching** (2 to 95 percent, with the time left), **Recently added**, Movies and Series posters. The detail header is tinted with the poster colour (needs the TMDB posters option). Long-press a video for mark watched, **Share** and delete: local files are copied for sharing (confirmation above 50 MB), web and magnet items share their link, items on network storages cannot be shared. Telos Video also opens web video links, magnet links and `.torrent` links or files shared to it.

## What is recognised as a movie or a series

A film needs a real title followed by a standalone year, such as `Title (2010).mkv` or `Title.2010.1080p.mkv`. Dates and camera or messenger names (`VID-20240305-WA0001`, `video_2024-03-05_12-30-11`, `20240305_123011`, `Screen_Recording_...`) never count. Series need `S01E02` or `1x02`. Videos in messenger, camera, screenshot or screen-recording folders (Viber, WhatsApp, Telegram, Messenger, Facebook, Instagram, Snapchat, Signal, TikTok, DCIM/Camera, Screenshots, Screen recordings, Telos recordings) are never movies or series and are listed under **Other**.
