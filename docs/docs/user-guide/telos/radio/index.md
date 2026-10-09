# Telos Radio

Internet radio with your own station collection. Its behavior and logic follow the
[Transistor](https://codeberg.org/y20k/transistor) project (compared with version 4.3.9). Transistor is MIT licensed
and credited in the project's third-party notices.

::: tip At a glance
Collection, search and history tabs. Add stations by address, import M3U or PLS playlists, find stations in the
Radio-Browser directory, see the current track, use a sleep timer, and record the playing station to a file.
:::

## What it is

Telos Radio streams live radio over the internet with a media session service built on Media3 / ExoPlayer. Playback
continues in the background with a notification, and a small player with the station name and current track is shown
while a station is loaded. It pauses when headphones are unplugged. It is a
[virtual app](../#how-the-built-in-apps-work), is guarded by the crash guard, and its service and screens are disabled
when you switch it off in [Telos Store](../store/). Radio has a single reference page; this is it.

## Getting started and permissions

| Permission | Why | When |
| --- | --- | --- |
| Internet | Streaming and station search | Always |
| Notifications | Playback notification | Android 13 and later |
| Foreground service of type media playback | Keep playing with the screen off | Declared by the app |
| Foreground service for recording | Keep a recording running in the background | Only while recording |

There is **no storage permission**. Import, export and backup use Android's file picker, so you choose each file.

To start: open **Telos Radio**, go to **Search**, type a station name and tap a result, or tap **+** to add a stream
address yourself.

## A tour of the screen

| Element | What it does |
| --- | --- |
| Title "Radio" and back arrow | The screen name and a way back |
| **+** (Add station) | Adds a station by address |
| Record button | Starts or stops [recording](#recording-a-station) the playing station |
| Sleep timer icon | Opens the sleep timer |
| Three-dot menu | Import playlist (M3U / PLS), Export playlist (M3U), Back up collection, Restore backup |
| Tabs **Collection**, **Search**, **History** | The three views |
| Mini player | At the bottom of the Radio dashboard while a station is loaded: station name, current track (with a sleep timer indicator when one is running), play or pause, **Record** and a close **X** that stops playback |

## Stations

| Tab | Contents |
| --- | --- |
| **Collection** | A **Recordings** row (see below) and your stations. Tap to play, long-press to rename or remove, the heart removes a station from the collection. An empty collection says "No stations yet. Search for a station, add one with its address, or import a playlist." |
| **Search** | A search field "Search by station name" with a progress bar. Results show a heart that saves a station to your collection |
| **History** | The tracks announced by the stations you listen to, newest first, with **Clear history** |

| Action | How |
| --- | --- |
| Add by address | **+** button: "Stream or playlist address" and an optional "Name". Playlist addresses are resolved when played |
| Rename or remove | Long-press a station in the collection: **Rename** ("Rename station" then **Save**) or **Remove** |
| Favorite | Heart on a station in the search results saves it to your collection |
| Discover | **Search** tab: search by station name through Radio-Browser. Results are the **50 most popular** matches by click count, broken streams hidden |
| Play | Tap a station |

### Station details that are kept

Each station keeps its name, its stream address (and fallback streams), and its icon address when it came from
Radio-Browser. The icon is used as artwork in the notification.

## Import and export

Use the three-dot menu in the top bar.

| Format | Import | Export |
| --- | --- | --- |
| M3U | Yes | Yes (`telos-radio.m3u`) |
| PLS | Yes | No |
| JSON | Restore backup | Back up collection (`telos-radio-backup.json`) |

- Only entries with `http` or `https` addresses are imported from a playlist.
- Use the JSON backup to move your collection to another device. Restoring reports how many stations it read.
- M3U export holds only names and addresses, which makes it better for sharing with other radio apps.

::: details Moving your collection to a new phone
Use **Back up collection** to create a JSON file, copy it to the new phone, and use **Restore backup**.
:::

## Playback

- **Playlist links are resolved.** If the address is a `.pls` or `.m3u` playlist (or the server says so with its
  content type), Telos opens it, finds the real stream, and keeps further streams of the playlist as fallbacks. If a
  stream fails it tries the next one, and only then says "This station cannot be played right now". Up to 6 redirects
  are followed and the first 64 KB of a playlist are read.
- `.m3u8` (HLS) streams are played directly.
- **Current track.** Many stations announce the track in the stream metadata. Telos shows it and writes each new
  track to the **History** tab, which keeps the newest 500 entries. **Clear history** empties it.
- **Next and previous** (notification, lock screen, headset, Android Auto) switch between the stations of your
  collection.
- **Sleep timer.** The sleep timer icon in the top bar offers 15, 30, 45, 60 or 90 minutes ("Stop playback in N
  minutes") and **Turn timer off**. When it ends, playback is paused.
- **Becoming noisy.** Playback pauses when headphones are unplugged.
- **Audio focus.** Calls and other apps' audio pause or duck the radio and it resumes afterwards.

::: tip
Which track information you see depends entirely on what the station sends. Stations without metadata only show the
station name or "Streaming live...".
:::

## Recording a station

The **Record** button in the top bar of the Radio dashboard and in the mini player records the stream of the playing
station. Tap it again to stop.

| Topic | Behavior |
| --- | --- |
| What is recorded | The raw stream, saved as `.mp3`, `.aac` or `.ogg` depending on the stream's `Content-Type`. HLS (`.m3u8`) streams cannot be recorded; Telos says so |
| Where | `Music/Telos Radio` (the app's own Music folder on Android 9 and older) |
| File name | `<Station> - yyyy-MM-dd HH-mm.ext` |
| While recording | A foreground notification with a timer and a **Stop** action. The elapsed time and the size so far are shown in the app |
| Ending | If the stream drops, the storage is nearly full, or you tap **Stop**, what was captured so far is saved |
| Too short | A recording under 1 KB saves nothing |
| Recordings list | The **Recordings** row at the top of the Collection tab opens a sheet with the saved files: open, share and delete (delete asks for confirmation) |

::: warning Closing the mini player does not stop a recording
Stopping playback with the **X** of the mini player does not stop an active recording. Stop the recording with the
**Record** button or the **Stop** action of its notification.
:::

Only record what you have the right to record; copyright rules for recordings of broadcasts differ by country.

## A typical session

1. Open the **Search** tab and type a name, for example the station you know from home.
2. Tap a result to play it, and tap the heart to keep it in your **Collection**.
3. Leave the app. Playback continues and you control it from the notification.
4. Set the sleep timer if you listen at night.
5. Later, open **History** to see which tracks were announced.

## Settings

Telos Radio has no settings page of its own. Everything is in the top bar of the radio screen.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Sleep timer | Top bar | off | Pauses after 15, 30, 45, 60 or 90 minutes |
| Import, export, backup | Three-dot menu | | File picker based, nothing is stored elsewhere |
| History | History tab | on | Automatic. **Clear history** empties it |
| Enable or disable the app | [Telos Store](../store/) | on | Hides the icon and disables the service |

## Privacy

| Action | Server contacted | When |
| --- | --- | --- |
| Playing a station | The station's own server (and a playlist host if the address is a playlist) | When you play |
| Searching stations | A Radio-Browser mirror (`de1`, `de2` or `all.api.radio-browser.info`, found through DNS) | Only when you type in the Search tab |
| Playing a station that came from Radio-Browser | Radio-Browser | A play is counted for that station, as Radio-Browser asks apps to do. Stations you added by address or imported from a playlist are not reported |
| Recording | The station's own server | Same stream as playing, saved on the phone only |
| Import, export, backup | None | Done locally through the file picker |

The station you play can see your IP address, as with any radio app. The user agent sent is "Telos Radio". If the
system cannot resolve a name for the station search (blocked or broken DNS), Telos asks a DNS-over-HTTPS service
(`cloudflare-dns.com`, then `dns.google`) for the address of that server name. Only the server name is sent, not what
you search for.

## Supported formats

| Type | Support |
| --- | --- |
| Streams | What ExoPlayer plays: typically MP3, AAC, Ogg and HLS (`.m3u8`) over http or https |
| Playlists | `.pls`, `.m3u` (also detected by their content type) |
| Collection files | M3U export, JSON backup |

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Station collection | Collection tab | Add by address, rename, remove |
| Radio-Browser search | Search tab | 50 most popular results |
| M3U / PLS import, M3U export | Menu | |
| JSON backup and restore | Menu | |
| Playlist resolving with fallback streams | Playback | |
| Track metadata and history | Mini player, History tab | If the station sends it |
| Next and previous station | Notification, headset | Within the collection |
| Sleep timer | Top bar | |
| Record the playing station | Top bar, mini player | mp3 / aac / ogg streams, not HLS |
| Recordings list (open, share, delete) | Collection tab | |
| Equalizer, alarms | | Not available |

## Limitations

- Needs a network connection.
- Stations that change their address stop working until you update them.
- Search lists only Radio-Browser. Other directories are not available.
- Track names exist only when the station sends them.
- HLS streams cannot be recorded. Closing the mini player with its **X** does not stop an active recording.
- No alarm clock, no equalizer, no station favorites separate from the collection.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Search failed" | No Radio-Browser mirror was reachable. Check your network and try again |
| "This station cannot be played right now" | The stream is down or the address changed. Try another station or edit the address |
| No track name | The station does not send metadata |
| No notification | Allow notifications for Telos |
| Imported nothing | The playlist had no `http` or `https` addresses |
| Playback stops by itself | The sleep timer ran out, or the network dropped and every fallback failed |

::: details Do not confuse with network radio
The cellular network mode switcher in the [phone settings](../phone/calls#cellular-network-mode) is unrelated to
internet radio.
:::
