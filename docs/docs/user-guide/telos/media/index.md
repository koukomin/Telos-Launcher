# Telos Media

Telos Media puts [Telos Music](../music/index.md), [Telos Radio](../radio/index.md), TV and [Telos Video](../video/index.md) in one app, as four spaces, plus a fifth one for Stremio add-ons.

## Switching spaces

A pill at the top has five segments: **Music**, **Radio**, **Video**, **TV** and **Stremio Fork** (only the selected one shows its label, so all five icons always fit). Tap one to switch; long press a segment and drag it sideways to change the order (it is remembered); the last space you used is remembered
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

## TV: favorites, background playback, reconnecting

- **Favorites first:** once you have favorites, the TV home shows them (a large **Continue watching** card, the favorites grid, then recently watched and your own channels). **Browse** has everything else (search, categories, countries, extra sources); the last view you used is remembered. Category names are shown in the app language.
- **Keep playing in the background:** in the TV overflow menu, **Playback** has the switch (off by default). Off: leaving the app or turning the screen off pauses the channel and you resume it yourself (the notification stays with a play button). On: the channel keeps playing with the screen off or after leaving the app, with a notification (logo, name, "Live TV", play/pause, stop), lock-screen and Bluetooth controls, and the video switched off in the background to save data; it stops after 5 minutes without playback. Android 13 and newer asks for the notification permission. The switch is part of the TV backup.
- **Reconnecting:** when the connection to a stream is lost, Telos Radio, web videos and TV keep trying until it is back (offline they just wait, online they retry after 1, 2, 4, 8 and then every 15 seconds), unless you press stop or pause. For TV, nothing is marked as broken and the offline static is not shown while the device is offline; the failover to other streams applies only while online.

## Telos Media pill

All five spaces are offered as icons in the pill, with the selected space named in a large heading directly below it; hiding the older Telos Music or Radio icons in the Store only removes those icons.

## Notification controls

Music, radio, TV and video each show a media notification while they play (also on the lock screen and in the system media controls, e.g. the media output on newer Android versions) with play/pause and previous/next (songs, stations, channels, episodes). Leaving the video screen pauses the video; its notification then lets you continue with sound only. Android 13 and newer asks for the notification permission.

## Stremio Fork

The **Stremio Fork** space is Telos' own client for [Stremio add-ons](https://github.com/Stremio/stremio-addon-sdk). It was written
from the public description of the add-on protocol (MIT licensed); no code of the Stremio apps or of other Stremio clients
is used. **Telos is not affiliated with Stremio and this is not the Stremio app.**

::: warning What Telos does and does not do
Telos ships **no add-on** and hosts **no content**. An add-on is a small web service run by someone else; it decides which
catalogs and streams you see, and Telos does not check them. You are responsible for what you add and watch, and for having the
right to watch it where you live. Some add-ons use BitTorrent, which shows your IP address to the other users of the torrent
(the add-on list and the stream list say so). The first time you sign in, it shows a notice that you must accept.
Streams of the torrent kind go through the same torrent player as Telos Video, so its Wi-Fi only setting and its VPN route apply.
:::

- **Add-ons:** the extension icon at the top opens the add-ons. Paste an add-on address (`https://…/manifest.json`) or a
  `stremio://` link and tap **Add add-on**. A one-tap suggestion adds Cinemeta, Stremio's own metadata add-on; it is never
  added without your tap, and we have not verified that its terms allow other clients, so check them if that matters to you.
  Add-ons can be removed and refreshed (a new version may have new catalogs). Only addresses on the public internet are used.
- **Home:** one row of posters for every catalog of every add-on (catalogs that need extra input are left out), with a search
  field that asks all searchable catalogs. **Continue watching** shows what you played last.
- **Detail page:** artwork, year, runtime, rating, genres, description and cast. A movie has **Play**; a series has seasons
  and episodes. **Save** puts the title in your **Library** tab (stored on this phone only).
- **Streams:** tapping Play or an episode asks all stream add-ons and lists what they offer, with the stream you used last for
  that title first. Web streams play in Telos Video's player (with its resume, picture-in-picture and notification controls),
  torrents open as magnet links in the same player, YouTube and web page links open in the matching app. Other kinds (Usenet,
  archives) are shown as not supported.

- **Subtitles:** subtitles that a stream carries, and those of subtitle add-ons (asked for a few seconds, with the file name of the
  stream), are handed to the player; pick them in the player's subtitle menu next to the ones of Telos Video's own subtitle search.
- **File inside a torrent (`fileIdx`):** a torrent can hold many files, for example a whole season. When an add-on names the
  file of the episode (its index in the torrent), that file plays; without it, the biggest video of the torrent plays.
- **Directory of add-ons:** **Browse add-ons** (in the add-ons sheet) lists add-ons offered by the directories of your installed
  add-ons (the `addon_catalog` part of the protocol) and by Stremio's public list of community add-ons (an address that
  Stremio can change or remove; then the list is shorter). They are made by others and are not checked by Telos or Stremio.
- **Stremio account:** opening this space while signed out shows an explanation of Stremio Fork and the sign-in form immediately. Sign in with your Stremio account to keep your **add-ons, library and continue watching** in
  step with it, the way the Stremio apps back them up. Telos uses the same account service as the Stremio apps
  (`api.strem.io`), documented in Stremio's open-source core (MIT). Your password is sent once to Stremio and never stored; only
  the session key is kept, encrypted with the Android Keystore. Add-ons and library items are merged (nothing of the account is
  deleted by signing in), and changes made here are sent to the account; **Sync now** repeats the merge. This is **unofficial**:
  Stremio can change or block the service, and we have not been able to check its terms of use. If you want to stay clear of that,
  you can use the other Telos Media spaces without signing in.

Use **Stremio account** in the toolbar to sync or **Sign out**. Signing out returns to the sign-in form and clears Stremio watched indicators. **Trakt** in the toolbar opens the same account settings used by Telos Video.

Movies display **Watched · Stremio**, **Watched · Trakt**, or both sources on posters and detail pages. Episode rows show the corresponding marks. Stremio episode marks follow its [watched-bitfield format](https://github.com/Stremio/stremio-watched-bitfield); merely starting playback is not counted as watched. Trakt marks match IMDb IDs and episode season/number, and work even when scrobbling is disabled. History refreshes on returning to this space and through **Sync now**. Trakt marks require a connected account and a catalog that supplies IMDb IDs.

What is not there: Stremio's own streaming server (torrents use the torrent engine of Telos Video), the MPV player, add-on
settings pages (add-ons that need configuration first must be configured in a browser, then added here with their address), the
watch position of an item (Stremio shows a title in continue watching when its offset is above zero; Telos writes the smallest
value and does not know the real position of a stream), and Android TV controls. The local add-on list, library and history are not part of the Telos backup.
