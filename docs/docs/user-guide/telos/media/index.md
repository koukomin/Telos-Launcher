# Telos Media

Telos Media puts [Telos Music](../music/index.md), [Telos Radio](../radio/index.md) and [Telos Video](../video/index.md) in one app, as three spaces.

## Switching spaces

A pill at the top has three segments: **Music**, **Radio** and **Video**. Tap one to switch; the last space you used is remembered
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
