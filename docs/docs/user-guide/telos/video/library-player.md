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
| **Folders** | Videos grouped by the folder they are in ("Other" when there is none), with "N videos" |

- **Search videos** filters the current list.
- Tap a movie, series or folder to open its detail view: a back arrow, the poster, title and description, the button
  **Add to Trakt watchlist**, and the files. Tap a file to play. A series lists its episodes sorted by season and
  episode.
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
| Picture | Fit, Fill or Zoom |
| Audio | Choose between the audio tracks of the file (shown when there is more than one) |
| Subtitles | Off, or one of the embedded tracks |
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
- There is no equalizer, no audio delay setting, no subtitle styling, and no casting.

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
