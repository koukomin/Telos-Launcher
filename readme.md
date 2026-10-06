# Telos Launcher

Telos is a personal fork of [Kvaesitso](https://github.com/MM2-0/Kvaesitso), a search focused, free
and open source launcher for Android. On top of the Kvaesitso launcher it adds a full phone app
(dialer, contacts, SMS, call recording), an app store and freezer, a desktop mode and more.

> **Note:** This fork currently uses a placeholder app icon and name. Final branding assets are
> still in progress.

## Download

Every successful build of the development branch is published automatically as a debug-signed
pre-release (testing only):

- [Latest dev builds (APK)](https://github.com/koukomin/Telos-Launcher/releases)

## Features

### Launcher (inherited from Kvaesitso)

Unified search over apps, contacts, calendar, files, websites, Wikipedia, locations, unit
conversion, calculator and more; widgets; weather; icon packs and themes; tags and favorites;
plugin SDK for third-party search/weather/calendar providers. See the
[Kvaesitso documentation](https://kvaesitso.mm20.de) for the full list.

### Added in Telos

**Phone app (Telos Phone, `:services:comms`, `:data:comms`)**

- Dialer with T9 predictive search (Latin, Greek and Cyrillic alphabets, accent-insensitive Greek
  matching), dialpad memory, voicemail key, one-tap speed dial, DTMF tones and haptics
- MMI / USSD / secret codes (`*#06#`, `*21*...#`) are passed to the network unchanged
- Recents with swipe to call or message, filters (missed, incoming, outgoing, rejected, talk time),
  grouping of repeated calls, call log export
- Contact page with call history, per-app chat, voice and video buttons (WhatsApp, Telegram, Signal,
  Viber), SIM choice, notes, copy number, call reminder, QR code, speed dial and ringtone per contact
- Contacts and favorites with A-Z index, contact groups, duplicate finder, vCard import and export,
  QR code sharing, per-contact default number and default SIM
- Dual-SIM routing: per-call SIM choice, per-number default SIM, last-used SIM
- SIM badges in call history with configurable colors for SIM 1 and SIM 2, number type labels
  (Mobile, Home, Work), favorites toggle on the contact page, filters shown on demand
- Outgoing caller ID masking (CLIR) with emergency-number bypass
- Offline call screening: block hidden, unknown or international numbers and a personal block list
- Call recording with Shizuku, root or microphone backends, quality presets, auto-record and
  automatic deletion of old recordings
- In-call controls: mute, speaker, Bluetooth routing, hold, keypad, merge, swap, add call,
  floating notes, caller notes
- Incoming call screen with blurred contact photo and two answer styles (buttons or swipe),
  quick reject with SMS, "remind me" callback reminders
- Privacy: hidden contacts behind a dialpad passcode, stealth settings menu, biometric phone-app
  lock, biometric protection for chosen numbers, secure call screen (no screenshots)
- Smart gestures: raise to answer, flip to decline, rain mode shake gesture, pocket mode,
  proximity speaker, volume-button Do Not Disturb shortcut
- Auto redial, fake incoming calls (scheduled), missed-call and post-call popups, Dynamic Island
  call state
- SMS engine: quick replies, scheduled SMS, routing of hidden-contact messages to the vault
- Radio (behaviour and logic taken from [Transistor](https://codeberg.org/y20k/transistor), compared with 4.3.9): station
  collection with add by address, rename and remove; M3U / PLS import, M3U export and JSON backup;
  playlist links resolved to the real stream with fallback streams; current track from the stream
  metadata with a track history; next / previous buttons switch stations; sleep timer; station discovery through
  [Radio-Browser](https://www.radio-browser.info/) with mirror fallback
- Settings page in the Right Dialer style (accent section captions, rounded cards)
- **SIP / VoIP (experimental):** a SIP account (for example a FRITZ!Box IP telephone) on top of
  [baresip](https://github.com/baresip/baresip), kept registered in the background only while it is
  switched on; incoming SIP calls with a call screen, notification and call log entries; the account
  can be used for outgoing calls as a separate SIP button, as the default, or not at all so that it
  only receives calls
- Cellular network mode switcher with Quick Settings tile and screen-off / battery-saver automation
- Home screen widgets (recents, direct call) and an encrypted backup of all phone settings
- **Remote phonebook:** caller names from an AVM FRITZ!Box telephone book (TR-064), cached locally
  and used for incoming calls and recents; the password is kept in the Android Keystore

**Media (`:services:comms`, `:app:ui`)**

- Telos Music: local music library by songs, albums and artists, accent-insensitive search (also for
  Greek), play queue with shuffle and repeat, notification and lock screen controls, synchronized
  lyrics from [LRCLIB](https://lrclib.net), tag editor for MP3, FLAC, M4A and OGG files (title, artist,
  album, genre, year, track number, cover art), sleep timer
- Telos Video: video library with movies, series (recognised from file names such as
  `Show.S01E02.mkv`) and folders, continue watching, full screen player with subtitle selection,
  external subtitle files, picture in picture, also used when another app opens a video
- Telos Video also plays web streams (HLS, DASH, RTSP and plain video links) and torrents (magnet
  links, .torrent addresses and files): the video downloads in order while it plays, through a
  local-only address, on Wi-Fi only by default, and everything is deleted when the player closes.
  Movie and series posters with descriptions come from [TMDB](https://www.themoviedb.org), subtitles
  can be searched, downloaded or fetched automatically from [OpenSubtitles](https://www.opensubtitles.com),
  also for torrents (both with your own API key). Only play content you are allowed to watch.
- Telos Photos: photo gallery by folder, full screen viewer with pinch zoom, EXIF viewer and editor,
  removal of the location or of all metadata, sharing without metadata, and an editor (rotate, flip,
  crop ratios, brightness, contrast, saturation, filters) that saves a copy; also opens images from
  other apps
- Telos Radio: see the radio entry above

**Apps and system (`:services:freeze`, `:services:app-management`, `:services:store`)**

- Smart Freeze: freeze or hide apps through Shizuku, Dhizuku or device owner, with protection for
  critical apps, multi-user targeting and a Work Profile sandbox
- Telos Store: install and update apps from F-Droid and GitHub releases, root and Shizuku install
  backends, update notifications from Obtainium
- App lock with intruder photos, Work Profile quiet mode toggle

**Desktop and appearance**

- Desktop mode with freeform windows, snapping and tiling presets, a taskbar with running tasks and a
  desktop workspace grid
- Video live wallpapers with dynamic color extraction (`:services:wallpapers`)
- Dynamic Island overlay, floating launcher, context profiles (routines), web apps panel
- Independent grids for home, search and dock, home screen folders with covers, advanced icon label
  and dock styling
- Greek and German translations in addition to the Kvaesitso locales

### Planned

- SIP: text messages, video and Bluetooth routing, and Android Telecom integration (SIP calls are
  handled by their own call screen for now)

## Calendar search

Calendar search reads from Android's system calendar storage (`CalendarContract`), so it works
with any calendar account synced to the device - not just Google Calendar. This includes FOSS
CalDAV calendars synced via apps like [DAVx5](https://www.davx5.com/) or
[Fossify Calendar](https://github.com/FossifyOrg/Calendar), as well as Exchange and other
sync-adapter-backed accounts. There is no Google-specific dependency anywhere in calendar search.

## Credits

Telos is built on top of the excellent work of the Kvaesitso project. All credit for the original
design, architecture and functionality of this launcher goes to its creator and contributors:

- Original project: [MM2-0/Kvaesitso](https://github.com/MM2-0/Kvaesitso)
- Original website and documentation: https://kvaesitso.mm20.de
- Original app icon: [@EliotAku](https://github.com/EliotAku)
- All Kvaesitso [translators and code contributors](https://github.com/MM2-0/Kvaesitso/graphs/contributors)

### Projects Telos is based on

The Telos features below take their design, behaviour or code from these projects. Where code was
adapted, the original license is respected.

| Project | License | What Telos uses it for |
| --- | --- | --- |
| [Kvaesitso](https://github.com/MM2-0/Kvaesitso) | GPL-3.0 | The whole launcher, plugin SDK and architecture |
| [Right Dialer (Goodwy/Dialer)](https://github.com/Goodwy/Dialer) | GPL-3.0 | Phone app layout and look: recents, contacts, dialpad, call screens |
| [Secure Dialer](https://github.com/Secure-Phone-apps/Secure-Dialer) | GPL-3.0 | Privacy features: biometric lock, secure call screen, call screening, callback reminders |
| [Ever Dialer](https://github.com/hari161008/Ever-Dialer) | GPL-3.0 | Power-user features: recording backends and retention, gestures, auto redial, fake calls, network switcher, notes |
| [Thor](https://github.com/trinadhthatakula/Thor) | GPL-3.0 | Freeze backends (Shizuku, Dhizuku) and OEM suspend fallbacks |
| [Undead Wallpaper](https://github.com/maocide/UndeadWallpaper) | GPL-3.0 | Video live wallpaper engine |
| [Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | Store behaviour: source URL parsing, update flow, update broadcasts |
| [Transistor](https://codeberg.org/y20k/transistor) | MIT | Radio player behaviour |
| [Radio-Browser](https://www.radio-browser.info/) | public API | Radio station directory |
| [Shizuku](https://github.com/RikkaApps/Shizuku) and [Dhizuku](https://github.com/iamr0s/Dhizuku) | see project | Privileged operations (freeze, recording, install, network mode) |
| [baresip](https://github.com/baresip/baresip) and [baresip-studio](https://github.com/juha-h/baresip-studio) | BSD-3-Clause | SIP engine and its JNI bridge |
| [LRCLIB](https://lrclib.net) | open API | Song lyrics for Telos Music |
| [libtorrent4j](https://github.com/aldenml/libtorrent4j) and [libtorrent](https://www.libtorrent.org) | MIT / BSD-3-Clause | Torrent streaming in Telos Video |
| [TMDB](https://www.themoviedb.org) | API terms | Posters and descriptions in Telos Video (not endorsed or certified by TMDB) |
| [OpenSubtitles](https://www.opensubtitles.com) | API terms | Subtitle search and download in Telos Video |
| [TagLib wrapper (Kyant0/taglib)](https://github.com/Kyant0/taglib) | Apache-2.0 | Reading and writing audio tags; it bundles [TagLib](https://taglib.org/) (LGPL-2.1 / MPL-1.1 upstream) |
| AVM FRITZ!Box [TR-064](https://avm.de/service/schnittstellen/) | specification | Remote phonebook |

The copyright notices of these projects are kept in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Other libraries are listed in `gradle/libs.versions.toml`, for example Jetpack Compose, Koin, Room,
SQLCipher, Ktor, Coil, Media3 and ZXing.

## License

This software is free software licensed under the GNU General Public License 3.0.

```
Copyright (C) 2021–2026 MM2-0 and the Kvaesitso contributors
Copyright (C) 2026 koukos (Telos Launcher fork)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

The plugin SDK modules (`plugins/sdk` and `core/shared`) are licensed under the Apache License 2.0.
