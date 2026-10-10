# Telos Media

Telos Media puts [Telos Music](../music/index.md), [Telos Radio](../radio/index.md), TV and [Telos Video](../video/index.md) in one app, as four spaces.

## Switching spaces

A pill at the top has four segments: **Music**, **Radio**, **TV** and **Video** (on narrow phones only the selected one shows its label). Tap one to switch; the last space you used is remembered
(it is part of the launcher settings backup). The three older apps still exist and open Telos Media on their space, so existing
shortcuts and search results keep working. A space whose app is hidden in the Store disappears from the pill.

## Shared mini player

Music and Radio share one mini player at the bottom. It shows the title and artist (music) or the station (radio) with the artwork,
play/pause, and next (music) or stop (radio). Tapping it opens the Music Now Playing screen, or the Radio space. It is hidden on the
Video space, because video plays in its own player.

## What is kept when you switch

Search text, the open tab and the library state of each space are kept; an open album or series page is not.

## Music, radio and video at the same time

Starting a video pauses music and radio; the paused music keeps its track and position and radio reconnects to the same station.
In the video player options, **Resume music or radio after the video** (off by default) resumes them automatically when you leave
the video. The paused state expires after 30 minutes. Music and radio also pause each other.

## Share

Long-press an item to **Share** it: songs (the audio file), albums and artists (a title list), radio stations (see below), videos
(a copy of the file, with a confirmation above 50 MB, or the link or magnet of a web video). Contacts (vCard), notes (text or a
Markdown file), files from Telos Files (network and cloud files are downloaded first, with a warning above 50 MB; vault files cannot be
shared), documents in the viewer, and installed apps (an APK, or an `.apks` archive for split apps) have Share in their long-press or
overflow menus too.

**Radio stations:** the shared text has the name, the address and a `telos-radio://add` link. If the receiver has Telos, opening it
asks "Add station X to Telos Radio?" with the name and address editable. Nothing is played or fetched before the receiver confirms,
only http and https addresses without credentials are accepted. Telos Radio also accepts shared stream and playlist links
(`.m3u`, `.pls`, `.mp3`, `.aac`, `icy://`). Telos Video accepts web video links, magnets and `.torrent` files shared to it.

## TV

The TV space plays live channels from the open [iptv-org](https://github.com/iptv-org/api) list (public domain data; the streams belong to third parties, and availability and legality depend on your country).

- **First use:** a disclaimer card. Nothing is downloaded until you accept it, and no network access happens before you open TV.
- **Catalog:** channel list, logos and stream addresses are downloaded from `iptv-org.github.io` when you open TV (cached on the device, checked for changes, refreshed in the background when older than 24 hours; **Refresh** forces it). Adult channels, closed channels and blocklisted ones are left out.
- **Country and language:** your home country and the app language are preselected; **Countries** lets you pick several (flags, channel counts, search) and filter by language. Category chips filter the list; search is Greek and Greeklish aware.
- **Favorites, recents, your channels:** the star on a card adds a favorite (shelf at the top, **Move up/down** in the long-press menu), recents are kept, and **Add channel** creates your own (name, http or https stream address, optional logo and group). **Import M3U playlist** (up to 2 MB and 5000 entries) adds many at once. **Export / Import TV channels** saves a JSON backup file.
- **Player:** tap a card for the full player; tap the picture for the controls, swipe up or down to change channel, back or minimize keeps it playing in the mini player.
- **Self-healing streams:** if a stream fails or is not ready within 8 seconds, Telos tries the next stream of the same channel; if all fail it checks online whether the stream list was updated and retries with the new addresses before showing an error ("Looking for another stream…" is shown meanwhile).
- **Search:** Settings > Search > **TV channels** (off by default) shows favorite, custom and recent channels; TV and Radio results carry a type chip.
- **Extra Greek sources (optional):** when Greece is among the selected countries, two public playlists ([Free-TV/IPTV](https://github.com/Free-TV/IPTV) Greece and [greektvm3u](https://github.com/filipposfilippides/greektvm3u)) add more streams to the same channels (tried after the iptv-org streams when one fails) and a few extra channels, marked **Extra**. They and the **Programme guide** have switches in the Countries sheet (on by default, only shown with Greece selected). Everything is loaded on your device only while TV is open, from `raw.githubusercontent.com`, `ext.greektv.app` and `epgshare01.online`; nothing is bundled (the playlists have no license file), and if a source does not work nothing is shown for it.
- **What is on:** the programme guide (XMLTV, refreshed at most every 6 hours) adds a **Now:** line on the channel cards and, in the player, now and next with the times and a progress bar; tap the now line for the description. Without guide data nothing is shown.
- **Offline channels:** when a channel cannot play, even after trying all its streams and checking for an updated list, the player shows animated TV static (a frozen frame with reduced animations) with "This channel is temporarily offline", **Retry** and **Back**; channels whose streams all fail get a small frozen static on their tile.
- TV pauses music and radio when it starts. It contacts only `iptv-org.github.io` and the stream hosts.
