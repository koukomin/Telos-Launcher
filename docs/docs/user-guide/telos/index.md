# Telos at a glance

Telos is a search-focused, free and open source launcher for Android. It is a fork of
[Kvaesitso](https://github.com/MM2-0/Kvaesitso) and keeps everything the original launcher does, then adds a
set of **built-in apps** that run inside the launcher: a phone app, messages, a file manager, a
gallery, music, video and radio players, an app store and an app freezer.

::: tip Two parts
- [**The launcher**](./launcher/) is search, home screen, widgets, icons and themes (inherited from Kvaesitso).
- **The Telos apps** are added on top. Each one has its own page below.
:::

## ✨ Built-in apps

<div class="telos-grid">

<a href="./phone/"><b>📞 Phone</b><span>Dialer, recents, contacts, dual SIM, call recording, screening, SIP</span></a>
<a href="./messages/"><b>💬 Messages</b><span>SMS and MMS conversations, default SMS app, scheduled messages</span></a>
<a href="./files/"><b>📁 Files</b><span>File manager with network and cloud storages, archives, Cryptomator</span></a>
<a href="./calculator/"><b>🧮 Calculator</b><span>Scientific calculator, VAT, unit and currency converter, history</span></a>
<a href="./photos/"><b>🖼️ Photos</b><span>Gallery, EXIF tools, editor and a document viewer</span></a>
<a href="./music/"><b>🎵 Music</b><span>Local library, lyrics, scrobbling, tag editor</span></a>
<a href="./video/"><b>🎬 Video</b><span>Library, player, web streams, torrents, subtitles, Trakt</span></a>
<a href="./radio/"><b>📻 Radio</b><span>Internet radio with station search and sleep timer</span></a>
<a href="./store/"><b>🛍️ Store</b><span>Install and update apps from GitHub, F-Droid and more</span></a>
<a href="./freeze/"><b>❄️ Smart Freeze</b><span>Freeze or hide apps through Shizuku, Dhizuku, root, Island or device owner</span></a>

</div>

<style>
.telos-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin: 16px 0; }
.telos-grid a { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border: 1px solid var(--vp-c-divider); border-radius: 12px; background: var(--vp-c-bg-soft); text-decoration: none; transition: border-color .2s; }
.telos-grid a:hover { border-color: var(--vp-c-brand-1); }
.telos-grid a b { color: var(--vp-c-text-1); font-size: 1.05em; }
.telos-grid a span { color: var(--vp-c-text-2); font-size: .9em; line-height: 1.4; }
</style>

| App | Purpose | Details |
| --- | --- | --- |
| Telos Phone | Calls, recents, contacts, call recording, call screening, SIP | [Phone](./phone/) |
| Telos Messages | Text and picture messages | [Messages](./messages/) |
| Telos Files | Browse and manage files, also on network and cloud storages | [Files](./files/) |
| Telos Calculator | Standard and scientific calculator, VAT, converters | [Calculator](./calculator/) |
| Telos Photos | Gallery, photo editor, document viewer | [Photos](./photos/) |
| Telos Music | Player for the music on the phone | [Music](./music/) |
| Telos Video | Video library and player, streams and torrents | [Video](./video/) |
| Telos Radio | Internet radio | [Radio](./radio/) |
| Telos Store | Obtainium-like app installer and updater, manages the Telos apps | [Store](./store/) |
| Smart Freeze | Freeze or hide installed apps | [Smart Freeze](./freeze/) |

## Launcher and apps compared

| | The launcher | The Telos apps |
| --- | --- | --- |
| Origin | Kvaesitso | Added by Telos |
| Where it runs | Launcher process | Launcher process (Video can use its own process) |
| Can be switched off | No | Yes, from the [Store](./store/) (except the Store itself) |
| Guarded by the crash guard | n/a | Radio, Music, Video, Photos |
| Docs | [The launcher](./launcher/) | One page per app |

## How the built-in apps work

The Telos apps are **virtual apps**. They are not separate APKs: their code is part of the launcher and
they run inside the launcher process (the video player can optionally run in a separate process, see
[Video](./video/)).

- **They look like normal apps.** Each one has an icon in the app grid and shows up in search.
- **They can be switched on and off.** Open [Telos Store](./store/) and "install" or "remove" an app.
  "Installing" shows its icon, "removing" hides it. Telos Store itself cannot be removed, otherwise
  there would be no way back.
- **Switched-off apps cost nothing.** For Radio, Music, Video and Photos the services and screens are
  disabled, so they use no memory or CPU and are not offered in "Open with". Phone and Messages are
  never touched by this.
- **They appear in the share menu.** Other apps can hand text, pictures, videos, `sms:`, `tel:`,
  `magnet:` and `obtainium:` links to them. Each entry appears under its own name and icon, and only
  while that app is installed.
- **They are guarded against crashes.** Radio, Music, Video and Photos are switched off automatically
  when they crash or hang twice within a day, with a notification that points to the Store. Installing
  the app again resets the counter. Phone and Messages are never switched off, because Android needs them.

| Share target | Accepts |
| --- | --- |
| Telos Messages | text, pictures, videos, `sms:` links |
| Telos Photos | pictures |
| Telos Video | videos, magnet links, torrent files |
| Telos Store | `obtainium:` links |
| Telos Phone | `tel:` links |

## Where to find the settings

- Each app has a settings page that you reach from its own screen. The Phone settings use rounded
  cards with accent colored section captions.
- Launcher-wide settings (search, grids, icons, plugins, integrations) are in the launcher settings.
- Cloud and network storages are managed in **Settings > Integrations > Cloud and network storage**.

::: warning Honest status
Some features are new and not tested on every device. They are marked <Badge type="warning" text="experimental" /> or
<Badge type="info" text="untested" /> on the app pages. Please read the "Limitations" section of an app before relying on it.
:::

## Credits

Telos builds on Kvaesitso and on ideas and code from several open source projects. The full list with
licenses is in the project [readme](https://github.com/koukomin/Telos-Launcher#projects-telos-is-based-on).
