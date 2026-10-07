# Telos Radio

Internet radio with your own station collection. Its behavior and logic follow the
[Transistor](https://codeberg.org/y20k/transistor) project (compared with version 4.3.9). Transistor is
MIT licensed and credited in the project's third-party notices.

::: tip At a glance
Collection, search and history tabs. Add stations by address, import M3U or PLS playlists, find stations
in the Radio-Browser directory, see the current track, and use a sleep timer.
:::

## What it is

Telos Radio streams live radio over the internet with a media session service. Playback continues in the
background with a notification, and a small player with the station name and current track is shown
while a station is loaded. It pauses when headphones are unplugged.

## Getting started and permissions

| Permission | Why |
| --- | --- |
| Internet | Streaming and station search |
| Notifications | Playback notification (Android 13 and later) |

There is no storage permission. Import and export use Android's file picker, so you choose each file.

To start: open **Telos Radio**, go to **Search**, type a station name and tap a result, or tap **+** to
add a stream address yourself.

## Stations

The screen has three tabs: **Collection**, **Search** and **History**.

| Action | How |
| --- | --- |
| Add by address | **+** button, paste a stream or playlist address, optional name |
| Rename or remove | Open the station options from the collection |
| Favorite | Heart on a station in the search results saves it to your collection |
| Discover | **Search** tab: search by station name through Radio-Browser. Results are the 50 most popular matches, broken streams hidden |
| Play | Tap a station |

## Import and export

Use the three-dot menu in the top bar.

| Format | Import | Export |
| --- | --- | --- |
| M3U | Yes | Yes |
| PLS | Yes | No |
| JSON | Restore backup | Back up collection |

- Only entries with `http` or `https` addresses are imported from a playlist.
- Use the JSON backup to move your collection to another device. Restoring reports how many stations it read.

## Playback

- **Playlist links are resolved.** If the address is a `.pls` or `.m3u` playlist, Telos opens it, finds the real
  stream, and keeps further streams of the playlist as fallbacks. If a stream fails it tries the next one,
  and only then says "This station cannot be played right now".
- `.m3u8` (HLS) streams are played directly.
- **Current track.** Many stations announce the track in the stream metadata. Telos shows it and writes each
  new track to the **History** tab. "Clear history" empties it.
- **Next and previous** (notification, lock screen, headset, Android Auto) switch between the stations of
  your collection.
- **Sleep timer.** The moon or timer icon offers 15, 30, 45, 60 or 90 minutes. When it ends, playback is paused.

::: tip
Which track information you see depends entirely on what the station sends. Stations without metadata
only show the station name or "Streaming live...".
:::

## Settings

Telos Radio has no settings page of its own. The sleep timer, import and export, and backup are in the top bar of
the radio screen.

## Privacy

| Action | Server contacted | When |
| --- | --- | --- |
| Playing a station | The station's own server (and a playlist host if the address is a playlist) | When you play |
| Searching stations | A Radio-Browser mirror (`de1`, `de2` or `all.api.radio-browser.info`, found through DNS) | Only when you type in the Search tab |
| Playing a station that came from Radio-Browser | Radio-Browser | A play is counted for that station, as Radio-Browser asks apps to do. Stations you added by address or imported from a playlist are not reported |
| Import, export, backup | None | Done locally through the file picker |

The station you play can see your IP address, as with any radio app. The user agent sent is "Telos Radio".

## Supported formats

| Type | Support |
| --- | --- |
| Streams | What ExoPlayer plays: typically MP3, AAC, Ogg and HLS (`.m3u8`) over http or https |
| Playlists | `.pls`, `.m3u` (also detected by their content type) |
| Collection files | M3U export, JSON backup |

## Limitations

- Needs a network connection.
- Stations that change their address stop working until you update them.
- Search lists only Radio-Browser. Other directories are not available.
- Track names exist only when the station sends them.
- No recording of streams.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Search failed" | No Radio-Browser mirror was reachable. Check your network and try again |
| "This station cannot be played right now" | The stream is down or the address changed. Try another station or edit the address |
| No track name | The station does not send metadata |
| No notification | Allow notifications for Telos |
| Imported nothing | The playlist had no `http` or `https` addresses |

::: details Do not confuse with network radio
The cellular network mode switcher in the phone settings is unrelated to internet radio.
:::
