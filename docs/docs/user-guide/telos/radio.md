# Telos Radio

Internet radio with your own station collection. Behavior and logic follow the
[Transistor](https://codeberg.org/y20k/transistor) project (compared with version 4.3.9).

## Stations

| Action | Details |
| --- | --- |
| Add by address | Paste a stream or playlist address |
| Rename, remove | Manage your collection |
| Discover | Search stations through [Radio-Browser](https://www.radio-browser.info/), with mirror fallback |

## Import and export

| Format | Import | Export |
| --- | --- | --- |
| M3U | yes | yes |
| PLS | yes | no |
| JSON | backup restore | backup |

## Playback

- Playlist links are resolved to the real stream, with fallback streams.
- The current track comes from the stream metadata, and a **track history** is kept.
- **Next / previous** buttons switch stations.
- **Sleep timer** stops the radio after a set time.

::: tip
What track information you see depends on what the station sends in its stream metadata.
:::

## Privacy

Station search contacts the public Radio-Browser directory. Streams connect directly to the station.

## Limitations

- Needs a network connection.
- Stations that change their stream address may need updating.
