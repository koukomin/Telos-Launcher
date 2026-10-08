<div align="center">

# Telos Launcher

**A search-focused, free and open source Android launcher with a set of built-in apps.**

Telos is a personal fork of [Kvaesitso](https://github.com/MM2-0/Kvaesitso). It keeps everything the original launcher does and adds a phone app, messages, a file manager, a gallery, music, video and radio players, an app store and an app freezer.

[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE.txt)
[![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://github.com/koukomin/Telos-Launcher/releases)
[![Latest dev build](https://img.shields.io/badge/latest-dev%20build-orange.svg)](https://github.com/koukomin/Telos-Launcher/releases)

[**Documentation**](https://koukomin.github.io/Telos-Launcher/) &nbsp;|&nbsp; [**Download**](https://github.com/koukomin/Telos-Launcher/releases) &nbsp;|&nbsp; [**The launcher**](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/) &nbsp;|&nbsp; [**Built-in apps**](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/)

The documentation site is published via GitHub Pages at <https://koukomin.github.io/Telos-Launcher/>.

</div>

> **Note:** The Telos apps use flat Material icons: a white glyph on a dark background of its own
> (navy for Phone, teal for Messages, amber for Files, slate for Calculator, blue for Screenshot, orange for Screen Recorder, cyan for Voice Recorder, amber for Notes, blue for Calendar, rose for Photos, purple for Music, red for
> Video, green for Radio). Telos Store uses the launcher icon. The name and the final branding are
> still in progress.

## Contents

- [What is Telos](#what-is-telos)
- [Download](#download)
- [The launcher](#the-launcher)
- [The built-in apps](#the-built-in-apps)
- [Languages](#languages)
- [Honest status](#honest-status)
- [Planned](#planned)
- [Build](#build)
- [Contributing](#contributing)
- [Credits](#credits) and [Projects Telos is based on](#projects-telos-is-based-on)
- [License](#license)

## What is Telos

Telos has two parts. **The launcher** is search, home screen, widgets, icons and themes, inherited from
Kvaesitso and extended by Telos. **The built-in apps** are *virtual apps*: they are not separate APKs, their
code is part of the launcher and runs inside the launcher process (the video player can optionally run in
its own process). Each app has an icon in the app grid, shows up in search, and can be switched on and off
from Telos Store.

| Part | What it is | Docs |
| --- | --- | --- |
| **The launcher** | Unified search, home screen, widgets, favorites and tags, themes, plugins, desktop mode, overlays, protection | [Launcher](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/) |
| **Telos Phone** | Dialer, recents, contacts, dual SIM, call recording, call screening, SIP | [Phone](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/phone/) |
| **Telos Messages** | SMS and MMS conversations, default SMS app, scheduled messages | [Messages](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/messages/) |
| **Telos Files** | File manager with network and cloud storages, archives, Cryptomator vaults | [Files](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/files/) |
| **Telos Screenshot** | Full, partial and scrolling screenshots with an editor | [Screenshot](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/screenshot/) |
| **Telos Screen Recorder** | Screen to video with microphone, pause and countdown | [Screen Recorder](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/screen-recorder/) |
| **Telos Notes** | Notes with labels, sync with a Markdown folder or Nextcloud Notes, Keep and Evernote import | [Notes](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/notes/) |
| **Telos Calendar** | Month and agenda calendar, local calendar, Google and CalDAV through the system | [Calendar](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/calendar/) |
| **Telos Voice Recorder** | Voice recordings with search, pause and call recordings | [Voice Recorder](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/voice-recorder/) |
| **Telos Calculator** | Standard and scientific calculator, VAT, unit and currency converter, history | [Calculator](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/calculator/) |
| **Telos Photos** | Gallery, EXIF tools, editor and a document viewer | [Photos](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/photos/) |
| **Telos Music** | Local library, lyrics, scrobbling, tag editor | [Music](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/music/) |
| **Telos Video** | Library, player, web streams, torrents, subtitles, Trakt | [Video](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/video/) |
| **Telos Radio** | Internet radio with station search and sleep timer | [Radio](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/radio/) |
| **Telos Store** | Install and update apps from GitHub, F-Droid and more, manages the Telos apps | [Store](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/store/) |
| **Smart Freeze** | Freeze or hide apps through Shizuku, root, device owner or Island (a launcher feature in Settings, not a Telos app) | [Smart Freeze](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/freeze/) |

### How the built-in apps work

- **They look like normal apps.** Each has an icon in the app grid and shows up in search.
- **They can be switched on and off.** In Telos Store "install" shows an app's icon and "remove" hides it. Telos Store itself cannot be removed.
- **Switched-off apps cost nothing.** For Radio, Music, Video and Photos the services and screens are disabled, so they use no memory or CPU and are not offered in "Open with". Phone and Messages are never touched.
- **They are guarded against crashes.** Radio, Music, Video and Photos are switched off automatically when they crash or hang twice within a day, with a notification that points to the Store. Installing the app again resets the counter. Phone and Messages are never switched off, because Android needs them.
- **They appear in the share menu**, each under its own name and icon and only while installed:

| Share target | Accepts |
| --- | --- |
| Telos Messages | text, pictures, videos, `sms:` links |
| Telos Photos | pictures |
| Telos Video | videos, magnet links, torrent files |
| Telos Store | `obtainium:` links |
| Telos Phone | `tel:` links |

## Download

Every successful build of the development branch is published automatically as a debug-signed
pre-release (testing only):

- [Latest dev builds (APK)](https://github.com/koukomin/Telos-Launcher/releases)

---

## The launcher

The launcher is built around one idea: **search is the main way to reach things**. Full reference:
[launcher docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/). Everything the original Kvaesitso launcher does is kept
(see the [Kvaesitso documentation](https://kvaesitso.mm20.de)).

### What Telos adds to Kvaesitso

| Area | Added or changed |
| --- | --- |
| Home screen | Dock with custom slots and multiple docks, shutters, video wallpapers, up to four widget pages, folder covers, independent grids for home, search and dock |
| Widgets | Reminders, Freeze, Battery, Network, System and At a Glance widgets, widget stacks, linked notes |
| Search | Results for web apps, the Telos apps and frozen apps, a text-matching ranking with usage weights, optional app recommendations |
| Overlays | Floating launcher, Dynamic Island, desktop mode, web apps panel, assistant mode |
| Automation | Context profiles |
| Protection | Settings lock, launcher lock, App Lock with intruder photos, crash guard, exception containment |
| Customization | Presets, font scale, extra icon shapes, more badge options, German and Greek translations |

### Search

One text field finds apps, contacts, events, files, places, articles and more, and it can also calculate,
convert units and offer quick actions. All search settings are in **Settings > Search**. Names, titles, tags
and custom names are matched; file contents, message texts and note bodies are not.

| Source | What it finds | Min. text | Online? | Default |
| --- | --- | --- | --- | --- |
| Apps | Installed apps of every profile (personal, work, private space) | 1 | No | On |
| Telos apps | Phone, Messages, Radio, Music, Video, Photos, Files, Calculator, Screenshot, Screen Recorder, Voice Recorder, Notes, Calendar, Store, while switched on | 1 | No | On |
| Web apps | Web app shortcuts shown in the grid | 1 | No | On |
| Activity by component name | One app entry for a typed `package/class` | 1 | No | On |
| App shortcuts | Shortcuts that apps publish (needs Telos as default home app) | 3 | No | On |
| Contacts | Android contacts, plus contact plugins | 2 | No | On |
| Calendar | Events of the next 730 days, plus calendar plugins | 2 | No | On |
| Reminders | Tasks.org tasks and tasks from calendar plugins | 2 | No | Filter on, source off |
| Local files | Files from the Android media database | 2 | No | On |
| Nextcloud, ownCloud | Files on your own server | 4 | Yes | Off |
| Places | OpenStreetMap places near you, plus place plugins | 2 | Yes | On |
| Websites | A preview card for a URL you type | 1 | Yes | On |
| Wikipedia | One article | 4 | Yes | On |
| Calculator | The value of an expression | 1 | No | On |
| Unit converter | Units and currencies | 1 | Rates only | On |
| Tags and custom names | Saved items whose tag or custom name contains your text | 1 | No | Always |
| Quick actions | Chips next to the search bar | 1 | No | Nine on |

**Special queries**

| You type | You get |
| --- | --- |
| `0xFF`, `0b101`, `017`, `3,5 * 2` | Calculator: hex, binary and octal literals, comma as decimal separator, `;` as argument separator, binary/hex/octal shown for integers, scientific notation above 1e12 or below 1e-5 |
| `5 km`, `5 km >> mi`, `20 c > f` | Unit converter: `<number> <unit>` lists every unit of the dimension; `>>`, `>` or `-` picks one target |
| `package/class` | The activity with that component name |
| `example.org/path` | Website preview card (Online results) and an **Open website** chip |
| `+49 30 1234567` | **Call**, **Message**, **Add to contacts** chips |
| `name@example.org` | **Email** and **Add to contacts** chips |
| `16:39`, `2:20 am` | **Set alarm** chip |
| `30 s`, `11 min`, `2 h`, `3 d` | **Start timer** chip (24 hours or less); longer spans become a **Schedule event** |
| A date, or date and time in your phone's format | **Schedule event** chip |
| `Private space` | **Lock or unlock private space** chip (Android 15+, must be added) |
| `Work profile` | **Pause or resume work profile** chip (must be added) |
| Any text | **Web search** chip, plus **Share** if added |

Conversion units: length (m, km, dm, cm, mm, in, ft, yd, mi, nmi), mass (kg, g, t, st, lb, oz, long and short ton), area, volume (L, mL, m3, gallons, pints, fl oz, cup, tbsp, tsp and more), time (ms to years), data (B to TB, kiB to TiB, bits), speed (m/s, km/h, mph, kn), temperature (C, F, K) and ISO 4217 currencies.

<details>
<summary><b>Quick actions: twelve built-ins, custom web searches and intents</b></summary>

| Action | Shown when | Default |
| --- | --- | --- |
| Call | Phone number (opens the dialer) | On |
| Message | Phone number | On |
| Email | Email address | On |
| Add to contacts | Phone number or email | On |
| Set alarm | Time of day | On |
| Start timer | Time span up to 24 h | On |
| Schedule event | Date, date and time, or a span over a day | On |
| Open website | URL | On |
| Web search | Always | On |
| Share | Always | Off |
| Lock or unlock private space | "Private space" typed, Android 15+ | Off |
| Pause or resume work profile | "Work profile" typed | Off |

- Created in **Settings > Search > Quick actions**: add, drag to reorder, edit, delete; everything is part of the backup.
- **Search on a website:** URL template with `${1}`; the launcher tries to import name, icon and template from the site's OpenSearch description (built-in fallbacks: Google, Bing, Amazon, DuckDuckGo, Yahoo, Ecosia). **YouTube** and **Google Play** come pre-installed.
- **Search in an app** (experimental): apps that accept the standard search intent.
- **Custom intent:** action, category, type, data, package, class, extras; the query goes in as data or as a string extra.
- Quick actions are not affected by filters.

</details>

<details>
<summary><b>Ranking, groups, filters and the filter bar</b></summary>

**How a search runs.** Every enabled source that matches the active filters is queried in parallel; results appear as they arrive. Optional **Search delay** (Settings > Performance, 0 to 500 ms, off by default). Places wait 250 ms and Wikipedia 750 ms. Limits: local files 10 results (scan stops after 500 rows), contacts about 15, Nextcloud 10, five entries per group until **Show all**.

**Ranking:** `0.6 x match score + 0.4 x usage weight`.

- Match score: Jaro-Winkler plus prefix and substring bonus, capped at 1; a match on a secondary field counts 20 percent less. Apps need at least 0.8.
- Usage weight rises with each launch. **Ranking flexibility** sets the step: Stable 0.01, Balanced 0.03 (default), Variable 0.1.
- **Greek and Greeklish:** Greek matches with or without accents, in any case, and when typed with Latin letters (`kalimera`, `mpala`, `thelw`, `chartis`). Letters that sound alike are merged, so a few similar English words may match too. It covers apps, contacts, files, calendar, tags, notes and the Telos apps, but not results that a plugin searches itself.
- Text is lower-cased and accent-stripped; ae, oe and ss are expanded; a **transliterator** (Settings > Language and region) lets non-Latin names match Latin input.
- Places sort by distance when a location is cached. Quick actions keep their configured order. The empty-search drawer is alphabetical per profile (frozen apps can go last). Duplicates by item key are removed.

**Group order, top to bottom:** favorites (empty query only), pending store updates, apps, web apps, app shortcuts, unit converter, calculator, events, reminders, contacts, places, Wikipedia, websites, files, documents, images, video, music, recommended app. **Arrangement of search results** can reverse it for a bottom search bar.

**Filters** (funnel button; a dot means one is active):

| Filter | Finds |
| --- | --- |
| Online results (switch) | Wikipedia, websites, places, cloud files, online plugin results |
| Apps, Contacts, Calendar, Reminders, App shortcuts | Their own sources |
| Files | Local and cloud files as one list |
| Documents, Images, Video, Music | Files by MIME type, one group each (alternative to Files) |
| Wikipedia, Websites, Places | Still need Online results |
| Tools | Calculator and unit converter |
| Hidden items (switch) | Shows excluded results again |

- With all categories on, tapping one selects only that one; tapping the only one turns all back on. A single active category starts expanded.
- Clearing the search or closing the drawer returns to your **default filter**.
- **Filter bar:** chips above the keyboard, on by default, up to 16 entries, drag to reorder, categories first, then a divider, then the two switches.

**Online results.** Network lookups only run with Online results on:

| Source | Sends | Minimum text |
| --- | --- | --- |
| Websites | The URL you typed, to that site (3 s timeout) | Any |
| Wikipedia | Your query, to the Wikipedia API | 4 characters |
| Places | Your query and area, to an Overpass server | 2 characters |
| Nextcloud, ownCloud | Your query, to your own server | 4 characters |
| Plugins | Whatever the plugin does | Set by the plugin |

If you switch online results on in the default filter, every query is sent to external web services, and Telos warns about that.

</details>

<details>
<summary><b>Result cards, per-source settings, search bar behavior, hidden results</b></summary>

- **Result cards:** apps (notifications, shortcuts, version and package, pin, app info, share link or APK, uninstall, shutter, freeze; with advanced freeze features also Clone to Sandbox, force stop, clear cache), shortcuts, contacts (numbers with call and message, emails, addresses, rows from other apps), events and tasks, files (metadata, open, share, delete for local files), places (map, opening hours, distance, dial, website, navigation, report a bug), websites, Wikipedia articles, calculator (long-press copies the number), unit converter, web apps. **Customize** sets a custom icon, label, tags and visibility.
- **Apps:** show all apps on empty search, show app information, list instead of grid, move or hide frozen apps, own search grid. With a work profile or private space the grid gets one tab per profile and a lock button (Telos must be the default home app).
- **Files:** local file types (documents, images, videos, music, other), excluded folders, Nextcloud, ownCloud, file plugins. A query of three characters or fewer matches the start of a file name only. Needs storage permission (all files access on Android 10+).
- **Contacts:** display, alternative, phonetic name and sort key; optional **tap to call**. **Calendar:** any synced account (including DAVx5 and Fossify Calendar), pick which calendars are searched. **Tasks:** Tasks.org.
- **Places:** OpenStreetMap, radius 500 m to 10 km (default 1500 m), map preview with theming, hide uncategorized places, own Overpass and tile server URLs, deduplicates places under 100 m apart.
- **Wikipedia:** URL can point to another language edition or MediaWiki site. **Unit converter:** preferred currencies listed first; rates from the European Central Bank daily file, refreshed hourly in the background.
- **Search bar behavior:** open keyboard, **launch on enter** (best match order: apps, shortcuts, events, reminders, places, contacts, Wikipedia, websites, files, then quick actions), private keyboard, search bar style transparent, solid or hidden, top or bottom, fixed bar, remember scroll position.
- **Hidden results:** per item **Show in** is app grid and search, search only, or never. Manage under Settings > Search > Excluded search results, optional reveal button (apps only, empty query only). Wikipedia, website and calculator results cannot be hidden.
- **Permissions per source:** a missing permission never crashes search; the group shows **Grant** and **Turn off**.
- **Plugin results:** contacts, files, places and calendar plugins; off until switched on, ranked, hidden, tagged and pinned like built-in results, marked by plugin badges.
- **App recommendations** (experimental): one fixed editorial suggestion labeled "Affiliate - Supports Telos". The code states no affiliate agreement exists yet; switch it off in Settings > Search.

</details>

**Not available or not honest to promise:** there is **no search history** (queries are never stored, only launch counts), **no hashtag queries** (tags are plain keywords; `#tag` is only how cards display them), no full-text search, texts about fuzzy initials, quick settings search and learning ranking have no code behind them, there is no Google Drive source, and the stored "separate work profile in results" setting is not read.

Full catalogue: [Search: complete feature catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/search-catalogue).

### Home screen

A clock, a search bar, an optional dock and, if you want them, widgets. Settings are in **Settings > Home screen**, **Appearance**, **Gestures** and **Grid and icons**.

| Piece | What it is | Default |
| --- | --- | --- |
| Clock | Top element with a dynamic zone, never removable (style can be "No clock") | On, Bold |
| Widgets page | Scrollable widget list, up to four pages | Swipe up |
| Search / app drawer | Search view with apps and favorites | Swipe down or tap the bar |
| Dock | Row or grid of apps and widgets above the search bar | Seeded once with default apps |
| Search bar | Transparent, solid or hidden; top or bottom | Bottom, transparent |
| Web apps panel | A page for web apps | Swipe right |
| Widgets on home screen | Puts widget page 1 under the clock | Off |

<details>
<summary><b>Clock: layouts, eight styles, colors, dynamic zone</b></summary>

- **Layout:** Default (vertical) or Compact (clock and dynamic zone side by side). Long-press the clock for its sheet; tapping opens the alarm list.
- **Styles:** Bold (optional outline), Simple, Orbit, Hands (analog, optional ticks), 7-segment, Binary, No clock, Custom widget (any Android widget).
- **Options:** Auto, Dark or Light content, theme color, seconds, monospaced digits (Bold, Simple, Orbit), fill screen height, alignment top, center or bottom, 12 or 24 hour format.
- **Dynamic zone** shows one item at a time, the most relevant: Date (primary and optional secondary calendar), Weather, Media controls, Alarms in the next 8 hours, Battery (off, when low or charging, always), or **Smartspacer** targets (non-release builds).

</details>

<details>
<summary><b>Dock, search bar, wallpapers (including video), system bars</b></summary>

- **Dock:** seeded once on first start with the device's default apps. **Custom dock** slots hold an app or a widget; 1 to 4 rows, 1 to 10 columns (1 x 5 default); **multiple docks** (up to 5, swipe to switch, default dock choice); own grid (3 to 12 columns, 32 to 64 dp icons); background plate (color, opacity default 30 percent, shadow); page indicator; **shutters** (swipe up on a dock icon to launch a second app, activity or shortcut). Dock icons are always circles.
- **Search bar:** transparent, solid or hidden with live preview, light or dark content, separate colors while the drawer is open, top or bottom, fixed bar, remember scroll position. The menu button offers Wallpaper, Settings, Add widget, Help; with text it clears the field.
- **Wallpaper** (Appearance): photo or video, system wallpaper picker, dim for dark theme, blur behind the search view (Android 12+, radius 4 to 64, default 32).
- **Video wallpaper** (experimental): a playlist of videos; scaling Fit, Fill or Stretch; brightness 0 to 2; zoom 0.5 to 5; position; speed 0.25 to 3; start behavior Resume, Restart or Random frame; parallax with strength; stored option for theme colors from the video; pause in Battery Saver (on), on thermal throttling (on) and in desktop mode (off).
- **Other:** lock desktop, fixed portrait rotation, charging animation (bubbles from the navigation bar), status and navigation bar icon color auto, light or dark, hide status bar, hide navigation bar, **Create folder** (two or more apps) and folder covers, edit button.

</details>

<details>
<summary><b>Gestures: 11 gestures and every action</b></summary>

| Gesture | Default action |
| --- | --- |
| Swipe down | Search / app drawer |
| Swipe up | Widgets (page 1) |
| Swipe right | Web Apps Panel |
| Swipe left, pinch in, pinch out, two-finger swipe up, two-finger swipe down, home button | Do nothing |
| Double tap | Turn off screen |
| Long press | Home screen menu (change wallpaper, add widget) |

- **Actions:** open any built-in Telos app (Phone, Messages, Notes, Calendar, ...) directly from a gesture, or show most of them as a page inside the launcher (all except the Store), do nothing, search / app drawer, widget pages 1 to 4, Feed (non-release builds), Web Apps Panel, home screen menu, notifications, quick settings, turn off screen, power menu, recent apps, launch an app, shortcut or any searchable item, launcher settings, plugin actions.
- Turn off screen (Android 9+), power menu and recent apps need the launcher's accessibility service; a banner and a "gesture failed" sheet guide you.
- Only swipes, double tap and long press can be overridden by a context profile. Tapping the search bar always opens search.

</details>

**Honest notes:** extra home screens (the slider for up to nine pages) are **switched off** in this build by a compile-time constant, so there is exactly one home page plus four widget pages. Video wallpaper is experimental. The dock background blur value has no control. Status: [Honest status](#honest-status).

Full catalogue: [Home screen and appearance catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/home-appearance-catalogue).

### Widgets and feed

Telos has built-in widgets, hosts any Android app widget and shows small widgets that a plugin provides.

| Widget | What it shows | Options |
| --- | --- | --- |
| At a Glance | One item: weather, next calendar event or battery state | none |
| Weather | Forecast of the selected provider | Compact mode |
| Calendar | Events of the day, previous and next day buttons | Hide completed tasks, per-calendar choice |
| Reminders | Tasks from the Tasks app | Hide completed tasks |
| Media | Playing media with controls | Interactive progress bar |
| Note | Free text, optionally linked to an external text file with conflict handling | Link to file |
| Battery | Charge state | none |
| Network | Connection type | none |
| System | RAM and storage usage | none |
| Freeze | Smart Freeze candidates, **Freeze now** button | none |
| Plugin widgets | Rows of title, subtitle and value from a widget plugin | none |
| Android widgets | Any app widget | Borderless, background card, theme color, configure, replace |

- **Layout:** four widget pages, optionally page 1 on the home screen, or a dock slot (only 1x1-fitting widgets). Edit widgets: add, drag to reorder, resize by height, **stack** (swipe between them, remove from stack), replace, remove.
- **Telos Phone** adds recent calls and direct call widgets. A **context profile** can switch which widget page the Widgets gesture opens.
- **Feed** (debug and nightly builds only): a content page from a separate overlay provider app (protocol version 7); **not available in release builds**, and the Google app and Lawnfeed are blocked in release builds.
- The Favorites widget is no longer offered; widgets added earlier keep working. Calendar widget extras (all-day switch, upcoming counts) and the reminders maximum are stored without a screen.

### Favorites, tags and hidden items

| Level | How you get it | Effect |
| --- | --- | --- |
| Not pinned, frequently used | Launch an item | Shown when there is room (rows 1 to 4) |
| Pinned, automatically sorted | **Pin to favorites** | Always shown, ordered by usage |
| Pinned, manually sorted | Drag in **Edit favorites** | Always shown, in your order |
| Pinned tag | Edit favorites > Tags | Works as a folder |

- Anything saveable can be pinned: apps, contacts, files, events, shortcuts, places, websites, web apps, plugin results. Favorites show above the app grid, in the dock, in the clock's dynamic zone; pinned events show in the calendar widget.
- Settings: favorites on or off, edit button, frequently used on or off, number of rows, compact tags, **Ranking flexibility** (Stable, Balanced, Variable).
- **Tags** are plain words attached to items: search keywords and, when pinned, folders. Create, rename, change icon or emoji, duplicate, delete; renaming to an existing name merges. Tags do not nest.
- **Auto-organize** groups installed apps into tags named after their Android category. It does not run by itself, skips categories with fewer than two apps, and replaces the contents of a tag of the same name.
- **Hidden items:** **Show in** per item (grid and search, search only, never), listed in Settings > Search > Excluded search results, optional reveal button, **Hidden items** filter.
- Pinned items, usage weights, visibility, custom names, icons and tags are part of the backup.

Full catalogue: [System catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/system-catalogue#favorites).

### Customization

<details>
<summary><b>Themes, presets, fonts, blur, grid, icons, badges, motion, per-item changes, language</b></summary>

- **Theme:** system, light, dark or **time of day** (default 20:00 and 07:00 clock hours), text size 80 to 200 percent, dynamic color source, theme import and export as `.kvtheme` files (opening a theme file from another app starts the import).
- **Color schemes:** Default (Material You on Android 12+), High contrast, Black and white, or your own: six core palette colors with separate light and dark roles.
- **Fonts (typography):** Default, System, Monospace, Serif, Rounded, or custom with a brand and a plain font and a style per text role.
- **Shapes:** Default, Extra round, Cut, Rectangular, or custom. **Transparency:** Default, Semi-transparent, or custom opacity of background, surface and elevated surface.
- **Blur and surfaces:** wallpaper blur (radius 4 to 64), drawer background plate (default 90 percent), dock plate, folder background color.
- **Presets** (applied at once, no confirmation, no undo): AMOLED Black and Cyberpunk (colors), Colorful Grid (colors and layout), Classic Grid, Clean Grid, Pixel-style (layout). A layout preset overwrites grid, icon shape, dock and search bar settings.
- **Grid:** 3 to 12 columns (5 default), icon size 32 to 64 dp, labels (size 8 to 24, one or two lines, shadow, color), apps as a list, separate overrides for home and search grids, folders and folder covers.
- **Icons:** shapes (Platform default, Circle, Square, Rounded square, Squircle, Reuleaux triangle, Hexagon, Pentagon, Teardrop, Pebble), enforce shape, themed icons, enforce themed icons, one **icon pack** (appfilter and grayscale-map formats, live calendar and clock icons), icon cache size 50 to 500.
- **Badges:** notification badges (dot or count, color), cloud, suspended or frozen apps, shortcut and plugin badges; work profile, hidden item and Store update badges are always on. Frozen apps can be grayscale or carry a snowflake.
- **Motion:** charging animation, reduce animations, animation speed 0.5 to 2.0, bounce physics.
- **Per-item customization:** long-press and tap Customize for a new label (the original name still matches), icon (including icon pack, themed or text icon), tags, **Show in**, a shutter app, or web app settings.
- **Language and region:** 35 languages (see [Languages](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/languages)), per-app language on Android 13+, form of address (French, Spanish), transliteration, time format, measurement system, calendar systems, currency order.
- **Accessibility and comfort:** text size, high contrast and black and white schemes, reduce animations, label options, fixed rotation.
- **Limitation:** fallback icon packs are supported by the icon service but have **no setting**, so only a single icon pack is usable. Several other values are stored without a screen (Wikipedia images, dock background blur, legacy surface settings).

</details>

Full catalogue: [Home screen and appearance catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/home-appearance-catalogue#themes-and-colors).

### Plugins and integrations

Integrations are under **Settings > Integrations**, plugins under **Settings > Advanced > Plugins**.

| Integration | Gives you | Needs |
| --- | --- | --- |
| Weather | Forecast for the weather widget, clock and At a Glance | Network, location |
| Media control | Now playing, play, pause, skip, seek when supported; choose which music apps count. Telos Music, Video and Radio are normal sessions | Notification access |
| Nextcloud, ownCloud | File search in your own cloud, cloud badge, sign-in and sign-out | Account |
| Wikipedia | Article results, configurable Wikipedia URL | Online results filter |
| Tasks | Tasks.org tasks in search and in the reminders widget | Tasks app and permission |
| Breezy Weather | Weather via the Gadgetbridge broadcast, set up in both apps | Breezy Weather installed |
| Smartspacer | Smartspacer targets in the clock's dynamic zone (Android 10+) | A non-release build |
| Feed | A content page from another app | A non-release build, a provider app |
| Cloud and network storage | Dropbox, Google Drive, OneDrive, WebDAV, SFTP, SMB, FTP for Telos Files | Per storage |

**Severe weather alerts** (off by default, Integrations > Weather): a notification when the stored forecast for the next 6 to 72 hours expects rain (chance of rain from a threshold), heavy rain, snow or sleet, thunderstorms or hail, high temperatures, frost, strong wind or a high UV index, with the strongest value, the start time and the place; each kind at most once in 12 hours, temperatures and speeds in your measurement system, a test notification. Forecasts, not official warnings.

**Weather providers:** Open-Meteo (default) and Bright Sky always; OpenWeatherMap and MET Norway only in builds that ship their keys; Breezy Weather when installed; any weather plugin. Automatic or manual location, measurement system (system, metric, UK, US).

**Plugins** are ordinary Android apps that expose a content provider through the plugin SDK. Telos does not run their code, so a plugin cannot crash the launcher. Enabling a plugin triggers a permission dialog where **Allow** stores Telos on the plugin's own allow list; other callers get a `SecurityException`. A plugin may report "Setup required" or an error, and cannot be switched on until it is ready. A check mark marks verified authors.

| Plugin type | What it adds |
| --- | --- |
| Files | Search results from a cloud or other file source |
| Places | Places and points of interest |
| Calendar | Events and tasks, with calendar lists |
| Contacts | Contacts |
| Weather | A weather provider ("Set as weather provider") |
| Gesture action | Named actions for Settings > Gestures |
| Widget | A small read-only list widget in the widget picker |

Plugins can store references (must work offline) or copies of saved items. Developers: see `docs/docs/developer-guide/plugins/`.

**Honest notes:** feed and Smartspacer are **not in release builds**; Breezy Weather and Smartspacer need their apps; cloud logins and plugin enable state are not part of the backup.

Full catalogue: [System catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/system-catalogue#plugins).

### Backup and restore

**Settings > Advanced settings > Backup and restore** writes and reads a plain ZIP archive (not encrypted).

Backup and restore ask first **what** to include: **Launcher** (settings and the rest of the table), **Notes** and **Calendar** (the local calendars as `.ics`), each with a checkbox and an **All** button. On restore only the parts that the file contains are offered, and unticked parts are not touched.

| Included | Not included |
| --- | --- |
| Settings, favorites and saved items (usage weights, pin position, visibility), custom icons, labels and tags, widgets, quick actions, custom color, shape, transparency and typography themes, Telos Notes, local calendars of Telos Calendar (their own parts) | Cloud logins (Nextcloud, ownCloud, notes sync), calendars of accounts (the account has them), plugin enable state, wallpaper, icon packs, intruder photos, passwords and API keys of the Telos apps, hidden and protected call numbers |

- Restore wipes saved favorites first, then imports the file; secrets already on the device are kept. A SIP or phonebook account without a password after restore is switched off.
- Format `1.9`: same version restores fully, a different minor version restores with a warning, a different major version is refused. Archive entries that would unpack outside the restore folder are ignored.

### Profiles

- **Personal, Work and Private** profiles are recognized (private space on Android 15+; on older versions any other profile counts as work). The Samsung Secure Folder is hidden. Apps get tabs per profile in the empty-search grid, work apps a briefcase badge, private apps a lock badge.
- **Work profile screen** (Advanced > Work profile): status, a **Work Mode** switch to pause or resume work apps (Telos must be the default home app), optional authentication for the toggle.
- **Sandbox** (experimental, pending review): **Initialize Telos Sandbox** starts managed profile provisioning with Telos as profile owner; **Clone to Sandbox** copies an app into the work profile. There is **no "clone" profile type**; apps cloned by Android's dual apps feature live in other user handles and show a badged icon.
- **Context profiles** (experimental) are a different feature: named bundles of overrides (gestures, freeze profile, widget page, Do Not Disturb, brightness, launch an app) triggered manually, by time, WiFi name, Bluetooth device, Battery Saver or charging, checked only while the launcher is open. Details below.

### Permissions, assistant and global actions

| Permission or access | Used for |
| --- | --- |
| Calendar, Contacts, Location | Calendar search and widget, contact search, weather location, places distance, WiFi trigger |
| Tasks | Tasks.org integration |
| All files access | Local file search |
| Notification listener | Notification badges, media widget |
| Default home app role | App shortcuts, work profile pause, private space lock |
| Accessibility service | Screen off, power menu and recents gestures, App Lock detection (listens to window state only), screenshots and the swipes of the scrolling screenshot (Telos Screenshot) |
| Microphone | Telos Voice Recorder, the microphone of Telos Screen Recorder, call recording |
| Screen capture (asked every time) | Telos Screen Recorder |
| Phone, call log, phone state | Telos Phone, Dynamic Island call pill |
| Usage access | Smart Freeze idle detection, App Lock detection |
| Display over other apps | Floating launcher, Dynamic Island, App Lock overlay |
| Bluetooth connect, notification policy, write settings, camera | Context profile triggers and overrides, intruder photo |

- Permissions are asked only when a feature needs them. On Android 13+ accessibility and notification access can be blocked for apps installed outside a store; Telos shows guidance after the first attempt.
- **Default launcher:** Telos is a home app (`HOME`) and also handles pinned shortcuts, theme files and `obtainium://` links.
- **Assistant:** set Telos as the digital assistant and the assist gesture opens it in assistant mode.
- **Global actions** without root: notifications and quick settings (status bar service, accessibility fallback), screen lock, power menu and recents (accessibility). Telos Screenshot takes screenshots through the accessibility service (Android 11+ for its own pictures, the system action on Android 9 and 10); there is no gesture for it.
- Other: settings deep links, crash reporter and logs under Advanced > Debug (local only, nothing is sent), About with build information and open source libraries.

Full catalogue: [System catalogue](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/system-catalogue).

### Desktop mode and overlays

Telos can draw things in other places than the home screen. Much of this is marked experimental and needs verifying on real hardware (see [Honest status](#honest-status)).

| Feature | Where | What it does |
| --- | --- | --- |
| Desktop mode | A connected external display | Desktop shell with a workspace grid, taskbar with a start menu, running-app dots, system tray and clock. Orientation, icon size and wallpaper settings. Switches itself on at the first display |
| Freeform windows | Whole device, via Shizuku | Snap left, snap right, maximize and close from the taskbar menu. Changes windowing for every app while on |
| Floating launcher (Smart Sidebar) | Over every app | A thin handle on the left or right edge that opens a card of your tools, apps and widgets, modeled on the Smart Sidebar of OxygenOS: File Dock on top, one or two columns, an All list of tools, an Edit tile that opens an editor with search (tools, widgets, apps, drag to reorder). Tools: screenshot, partial screenshot, scrolling screenshot, screen recorder, voice recorder, recent files, flashlight, quick settings, notifications, lock screen, power menu. Widgets of the launcher can be put in it. No AI Summary, AI Speak or Private Tab. Off by default, no search field in the card by design |
| Dynamic Island | Top of the screen | A pill showing the most relevant live item by priority: call, timer, media, charging |
| Web apps | Home screen and a panel | Websites as app-like shortcuts in an embedded browser (ad and tracker blocking, tracking parameter stripping, custom CSS) or a Custom Tabs browser, with a Web Apps Panel |
| Assistant mode | The system assistant gesture | Opens Telos in a separate assistant window once Telos is set as the digital assistant |
| Context profiles | Behind the scenes | Named bundles of overrides |

<details>
<summary><b>Context profiles and overlay details</b></summary>

- **Context profile triggers:** manual, time of day, WiFi network name, Bluetooth device, Battery Saver, charging. Triggers are checked only while the launcher is open (on resume, then about once a minute), so a profile can lag behind the real situation.
- **A profile can change:** gestures, the freeze profile, which home widget page opens, Do Not Disturb, brightness, and launch an app once. Profile icons: Home, Work, Car, Gaming, Battery saver, Sleep, Custom.
- **Floating launcher settings:** side and position of the handle, handle size, width, color and transparency, hide the handle, two columns, show app names, panel transparency, icon size, rows before scrolling, File Dock, open apps in floating windows (needs freeform mode, see Desktop mode), haptic feedback, hide during a Gaming profile, Edit the sidebar. The handle opens with a tap or a drag, and the card opens next to it. If a bug ever locks the screen, it can be disabled without touch input with `adb shell am broadcast -a de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER -p <application id>`.
- **Dynamic Island:** also shows active calls (needs the phone state permission). The timer slot exists, but nothing in this build starts it.
- **Web app browsing settings:** block ads and trackers (a built-in host blocklist, not a full filter list engine), pinch to zoom, strip tracking parameters, top bar position, swipe to switch web apps. The ad blocker can also be set per web app (global, on or off, embedded browser only). Suggested category folders (Social, Email, Messaging, Video & Music, Productivity & Work) can be added with one tap; they are normal web apps and folders that can be edited or deleted, and nothing is added automatically. Web apps can be locked with App Lock and customized per item.
- **Overlay services** (floating launcher, Dynamic Island, App Lock) are foreground services that only run while switched on and need the display-over-other-apps permission.
- Desktop quarter snapping is in the code but not in the taskbar menu.

</details>

Full catalogues: [Dynamic Island, web apps and weather](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/home-appearance-catalogue#weather-media-and-web-apps) and [context profiles](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/features/system-catalogue#context-profiles).

### Privacy and protection

| Layer | What it protects | Where |
| --- | --- | --- |
| Settings lock | The hidden items and excluded folders settings pages (device lock, biometrics only or a separate PIN stored as a salted SHA-256 hash) | Settings > Search > Protection |
| Launcher lock | The home screen (device credential only) | Settings > Search > Protection |
| App Lock (experimental) | Chosen apps and web apps, with auto-lock timing and optional intruder photos | Settings > Advanced > App Lock |
| Hidden items | Items you never want in grid or search | Long-press > Customize |
| Online results off | Your queries | The Online results filter |
| Crash guard | The launcher, from bugs in Telos apps | Automatic |
| Backup | Your setup | Settings > Advanced > Backup and restore |

> Locks are convenience, not a vault. App Lock and the launcher lock stop casual access. They do not replace device encryption and a screen lock.

<details>
<summary><b>App Lock, network privacy, crash guard and backup in detail</b></summary>

- **App Lock:** asks for authentication every time a locked app comes to the foreground. Detection by usage access, by the accessibility service, or both; auto-lock timing from immediately to 30 minutes with per-app override; stay unlocked until screen off; optional lock for the work profile toggle. Detection by polling means a locked app can be visible for a split second.
- **Intruder photo (opt-in, off by default):** a silent front-camera photo on a failed attempt, auto-delete after 7 days to 2 years (90 days by default), app-private storage or a folder you choose, optional notification on every failed attempt. Photos stay on the device.
- **Network privacy:** online sources run only with the Online results filter. Calendar, contacts, apps, local files, calculator and unit converter never use the network. Currency rates download a small public file from the European Central Bank. Usage data stays in the launcher's database and the build has no analytics or telemetry library. **Private keyboard** asks the keyboard not to learn from search input.
- **Crash guard:** counts a crash when an uncaught exception happens while an app's marker is set, finds native crashes and hangs on the next start through Android's exit reasons (Android 11+), and switches an app off after two crashes in 24 hours. Two minutes of crash-free use or installing it again resets it. Background work of the Telos apps runs in a contained scope that logs failures instead of ending the launcher.
- **Separate video process (experimental):** Settings > Video services > Play in a separate process.
- **Idle behavior:** player services start only when something plays; video decoders, the torrent engine and the SIP stack are created on first use; switched-off apps have their manifest components disabled.
- **Backup and restore:** Settings > Advanced > Backup and restore writes a plain ZIP (not encrypted) with settings, favorites, usage weights, hidden items, names, icons, tags, themes, widgets and quick actions. It leaves out cloud logins, intruder photos, passwords and API keys of the Telos apps, and hidden and protected call numbers. On restore, secrets on the device are kept from the device. A different major format version cannot be restored, a different minor version restores with a warning.
- **Limitations:** App Lock reacts after an app is already open; locks are not a security boundary against someone who controls the device; backups are not encrypted.

</details>

### Performance

Telos stays light by doing little at startup and by disabling what you do not use. No measured numbers are published, and the baseline profile is hand-written, not measured on a device.

| Setting (Settings > Advanced > Performance) | Default | Range |
| --- | --- | --- |
| Reduce animations | Off | On or off |
| Animation speed | 1.0 | 0.5 to 2.0 |
| Bounce physics | No bounce | 0 to 100 percent |
| Search delay | Off (0 ms) | 0 to 500 ms |
| Icon cache size | 200 | 50 to 500 |

<details>
<summary><b>What costs something, how startup works, APK size choices, low-end checklist</b></summary>

- **Settings that cost something:** video wallpaper (pause in Battery Saver and on overheating are on by default), blur wallpaper, the three overlay services, online results as default filter, many enabled search sources, notification badges.
- **Startup:** starts the dependency injection container with all feature modules, installs the crash guard, restarts overlays you switched on, schedules the Store update check as one unique periodic job, and runs the virtual app guard off the main thread. Decoders, the torrent engine and the SIP stack are created on first use.
- **Search work:** app names are normalized once and cached, local file search stops after ten matches or 500 scanned rows, contact search at about fifteen, and slow sources are delayed so results arrive as they finish.
- **APK size choices:** only arm64-v8a and armeabi-v7a native libraries, R8 minification and resource shrinking in release and nightly builds, `Log.v` and `Log.d` stripped in release, obfuscation off (readable stack traces), one APK with two flavors (`default` and `fdroid`), minimum Android 8.0 (API 26).
- **Low-end checklist:** reduce animations, a search delay of 100 to 150 ms, a photo wallpaper without blur, switch off unused search sources and widgets, keep online results off by default, leave overlays off, switch off unused Telos apps in the Store, and freeze rarely used apps with Smart Freeze.
- **Limitations:** no benchmark numbers exist, the baseline profile is a first guess, the application class still starts all feature modules eagerly, and some motion is not covered by Reduce animations.

</details>

---

## The built-in apps

Each app below is a virtual app. Full pages with troubleshooting tables are in the
[documentation](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/).

### Telos Phone

A complete phone app inside the launcher. Its layout follows [Right Dialer](https://github.com/Goodwy/Dialer); privacy and power-user ideas come from [Secure Dialer](https://github.com/Secure-Phone-apps/Secure-Dialer) and [Ever Dialer](https://github.com/hari161008/Ever-Dialer). [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/phone/). Most call features need Telos to be the default phone app (Settings > Default phone app).

**Keypad and calls**

- Keypad with T9 predictive contact search (Latin, Greek and Cyrillic alphabets, accent-insensitive Greek matching), number formatting, paste, dialpad memory, DTMF tones and haptics, optional hidden dialpad letters
- Long-press `0` types `+`, long-press `1` calls voicemail, long-press `2` to `9` calls a speed dial, one-tap speed dial slots 1 to 9
- MMI / USSD / secret codes (`*#06#`, `*21*...#`) are passed to the network unchanged
- Tap to call and confirm before calling options
- Biometric confirmation before placing a call (every call, or listed contacts only)

**Dual SIM**

- Per-call SIM choice, per-number default SIM, last-used SIM, same SIM as last call to the number, fixed SIM 1 or SIM 2
- SIM badges in call history with configurable colors for SIM 1 and SIM 2
- Outgoing caller ID masking (CLIR) with regional prefixes and emergency-number bypass

**In-call experience**

- Incoming call screen with blurred contact photo and two answer styles (buttons or swipe), shown over the lock screen
- Quick reject with SMS, "remind me" callback reminders (5, 15, 30 or 60 minutes)
- In-call controls: mute, speaker, Bluetooth routing, hold, keypad, merge, swap, add call, record, caller notes with a floating note window
- Missed-call popup, optional popup after every call, Dynamic Island call state, secure call screen (no screenshots)

<details>
<summary><b>Recents, contacts and the contact page</b></summary>

- **Recents:** the newest 100 calls from the system call log (older entries are not listed). Day headers, grouping of repeated calls, SIM badges, swipe right to send a text, swipe left to delete an entry (no undo), long-press sheet. Filters: all, today, missed, incoming, outgoing, rejected, talk time. Call history export as plain text.
- **Contacts and favorites:** A-Z index, search by name or number, swipe right to text and left to call, long-press sheet (call, message, details, share, block). Only contacts with a phone number are listed. Telos reads and writes the Android contacts and has no contact database of its own.
- **Contact page:** photo, company, birthday, last 8 calls, one card per number with a per-contact default number, star, vCard text sharing, copy number, "remind me to call back", QR code, speed dial slot, per-contact ringtone, notes, per-number SIM, call over SIP
- **Chat app buttons:** WhatsApp, Telegram, Signal and Viber, with voice and video entries when the other app has synced them into the contact
- **Contact tools:** contact groups (listed, not editable), duplicate finder (deletes the extra Android contacts, no undo), vCard import (`.vcf`, name, numbers and emails only), scheduled fake incoming call
- Notes, per-number SIM, default numbers, hidden numbers, speed dials and block entries live in Telos settings, not in your Android contact

</details>

<details>
<summary><b>Call recording</b></summary>

- Manual or automatic (**Auto-record calls**). Needs the default dialer role and the microphone permission. Telos plays no announcement to the other party. Check your local law.
- **Backends:** Auto (Shizuku, then root, then microphone), Shizuku with microphone fallback, Root with microphone fallback, or microphone only. With Shizuku or root, Telos grants itself `CAPTURE_AUDIO_OUTPUT` and tries the call audio sources first. The microphone alone often captures only your side.
- **Quality presets:** Compact (24 kbps, 16 kHz), Balanced (48 kbps, 16 kHz, default), High (96 kbps, 44.1 kHz). AAC in an `.m4a` container.
- **Storage:** private app storage, each file encrypted with AES-256-GCM with a key in the Android Keystore. Automatic deletion after 7, 30 or 90 days (runs when the next recording starts). A recordings list opens a temporary decrypted copy in a player app.
- Recordings are not part of the encrypted settings backup and cannot be moved to another phone. SIP calls are not recorded.

</details>

<details>
<summary><b>Privacy, call screening and backup</b></summary>

- **Call screening** (offline, all rules off by default): block hidden numbers, a Telos block list (exact number match), unknown callers (not in your contacts) and international callers. Blocked calls are rejected silently and leave no log entry and no notification.
- **Hidden contacts:** hide numbers behind a 4 to 6 digit PIN typed on the dialpad as `#PIN#`. Hidden from the contacts tab and recents, name masked on incoming calls, optional stealth mode that hides the settings menu. PBKDF2 hash, guesses slowed down after five wrong ones, no PIN recovery. Hiding only changes what Telos shows.
- **Phone app lock** with biometrics or device credential, and biometric protection for chosen numbers
- **Secure storage:** call recordings AES-256-GCM with a Keystore key, block list in a SQLCipher database, SIP and FRITZ!Box passwords encrypted with a Keystore key
- **Encrypted backup** of all phone settings (AES-256-GCM, key derived from your password with PBKDF2): includes hidden and protected numbers, block list, notes and speed dials. Excludes recordings, SMS and the PIN itself. Passwords inside are bound to the phone that made the backup. The launcher-wide settings backup leaves out passwords, keys and hidden contacts.

</details>

<details>
<summary><b>Smart gestures, auto redial, network mode, widgets, SIP and FRITZ!Box</b></summary>

- **Smart gestures** (off by default, SIM calls only): raise to answer, flip to decline, rain mode shake gesture, pocket mode, proximity speaker, volume-button Do Not Disturb shortcut
- **Auto redial** for busy, missed or rejected outgoing calls, fake incoming calls (scheduled)
- **Cellular network mode switcher** (Shizuku or root) with a Quick Settings tile and screen-off / battery-saver automation
- **Home screen widgets:** recent calls and direct call (dialpad)
- **Settings page** in the Right Dialer style (accent section captions, rounded cards)
- **SIP / VoIP (experimental):** a SIP account (for example a FRITZ!Box IP telephone) on top of [baresip](https://github.com/baresip/baresip), kept registered in the background only while it is switched on. Incoming SIP calls with a call screen, notification and call log entries. Outgoing as a separate SIP button, as the default, or not at all so that it only receives calls. Opus and G.711, one SIP call at a time, no hold, merge or recording. TLS certificates are not verified, so use it only on networks you trust.
- **Remote phonebook:** caller names from an AVM FRITZ!Box telephone book (TR-064), cached locally and used for incoming calls and recents. The password is kept in the Android Keystore. Traffic to the FRITZ!Box is plain HTTP, so use it only on your home network.

</details>

**Status and limitations:** Keypad, T9, recents, contacts, screening, hidden contacts, backup and the FRITZ!Box phonebook are stable. SIP is experimental and untested on real devices and servers. The root recording backend and the cellular network mode (Shizuku or root) are untested. Call recording quality and the other party's audio depend on the device, and gestures depend on the phone's sensors. SIP calls use their own call screen (no Android Telecom integration yet) and should not be relied on for emergency calls.

### Telos Messages

Text (SMS) and picture (MMS) messages as conversations with a reply field. It is part of Telos Phone: open **Messages** from the three-dot menu. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/messages/).

**Conversations**

- Conversation list with unread conversations in bold, **New message** by number, reply field, message bubbles with pictures and videos inline
- Failed messages are marked "not sent"; opening a conversation marks it read
- Group messages as MMS (default SMS app only)

**Default SMS app**

- Telos can be the phone's default SMS app (Android asks once): it then stores and notifies about received messages, sends from the message field, sends pictures and MMS, and answers other apps' "reply with a message" requests
- Without being the default app Telos only reads messages and replies with text, and the system's messaging app still shows and stores everything

**Sending, sharing and links**

- A **+** button attaches pictures (default SMS app only); pictures are shrunk to about 1600 pixels and 600 KB before sending
- Telos appears in other apps' share menu ("Telos Messages") for a text, picture or video, and opens `sms:`, `smsto:`, `mms:` and `mmsto:` links with a prefilled text (nothing is sent until you tap Send)
- **Quick replies:** Reject + SMS on the call screen and in the missed-call popup, with a template of up to 160 characters
- **Scheduled SMS** (Phone settings > Tools): text only, one recipient, on the exact minute once "Alarms & reminders" is allowed, alarms set again after a reboot
- Notifications: one per sender while Telos is the default SMS app

**Privacy**

- Conversations with hidden contacts are only listed while the hidden contacts are unlocked, and their notifications read "New message" without sender or text
- No upload feature: messages move only between your phone, the system SMS service and your carrier

**Status and limitations:** MMS (pictures, videos, group messages) is experimental and untested across devices and carriers. A video of more than about 2.4 MB is silently left out of a message. There is no search, delete, archive, delivery report, SIM choice or draft saving, no RCS and no end-to-end encryption.

### Telos Files

A file manager for your phone, network storages and cloud storages. The layout follows [Solid Explorer](https://play.google.com/store/apps/details?id=pl.solidexplorer2) and [MiXplorer](https://forum.xda-developers.com/t/app-2-2-mixplorer-v6-x-released-fully-featured-file-manager.1523691/), taken as inspiration only, no code was copied. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/files/). Needs All files access on Android 11 and later.

**Browsing and viewing**

- Home page with storage overview (free space, SD cards and USB drives), quick access to Downloads, Camera, Pictures, Music, Movies and Documents, favorites, and saved connections
- Breadcrumb path bar, storage drawer, list and adaptive grid view with picture thumbnails and type colors, sorting by name, date, size or type, folders first, hidden files toggle
- Search in the current folder (recursive on local storage, up to 300 results)
- Files open in the right app: pictures in Telos Photos, videos in Telos Video, PDF, text, code and RTF in the Photos document viewer, everything else through Android's chooser

**File operations**

- Multi-select, copy, cut and paste with progress and cancel, rename, delete (permanent, no trash), new folder and file, share, compress to zip
- Properties with permissions and on-demand MD5, SHA-1 and SHA-256 checksums
- Copy between any two storages (local, network, cloud); nothing is ever overwritten, a free name is used

<details>
<summary><b>Network and cloud storages</b></summary>

- **Protocols:** WebDAV, Nextcloud, ownCloud, SFTP / SSHFS (password or key, host key pinned on first use), SMB / CIFS (Windows shares, NAS, SMB2 and SMB3), FTP and FTPS (explicit TLS), Dropbox, Google Drive and OneDrive, plus **Cloud app on this phone** (the document provider of an installed cloud app such as Google Drive, signed in with the phone's account, no client ID)
- **Cloud sign-in** uses OAuth with PKCE and **your own client ID** (and for Google a client secret) that you register with the provider. The redirect address is `http://localhost:53682/` (Dropbox, OneDrive) or `http://127.0.0.1:53682/` (Google). The sign-in must finish within three minutes.
- **Secrets:** passwords, private keys, client secrets and tokens are stored encrypted with a key in the Android Keystore. Saved connections are not part of the launcher backup.
- **Operations:** browse, open, download, upload, copy between any two storages, rename, move and delete. Server-side copy for WebDAV, Nextcloud, ownCloud and Dropbox, other copies stream through the phone. Opened remote files are downloaded to the cache first.
- Connections are managed in Settings > Integrations > Cloud and network storage, or from the Files drawer.
- **Not supported:** iCloud (Apple has no public API) and Mega (its own encryption protocol is not implemented). Remote search only filters the current folder, and there is no option to accept an unknown TLS certificate.

</details>

<details>
<summary><b>Archives, Cryptomator vaults and the root explorer</b></summary>

- **Archives:** zip, jar, apk, 7z and tar (also gz, bz2, xz) open like folders (read only). **Extract here** unpacks next to the archive, **Compress to zip** packs local files. Epub and Office files are zip-based and offer the same dialog. Password-protected archives are not supported, `rar`, `iso` and `cab` are not opened inside Telos, and only local archives can be opened.
- **Cryptomator vaults** (experimental, read only): vault formats 7 and 8 in a local folder can be unlocked and read. Files opened from a vault are decrypted into a temporary cache, which **Lock vault** removes. The password is not stored. gocryptfs, EncFS and VeraCrypt are not supported.
- **Root explorer** (optional, untested): a superuser mode for system folders. Off until you switch it on and tick "I understand the risks". Shows permissions, owners and links, a warning banner in system folders, extra confirmation for deleting system files, changing permissions in octal, and making `/system` writable or read-only. Writing into root paths from remote storages or archives is not supported.

</details>

**Status and limitations:** Local files, network and cloud storages and archives are stable. Cryptomator support is experimental and read only, and the root explorer is untested. iCloud, Mega, gocryptfs, EncFS and VeraCrypt are not supported. There is no trash and no undo, no writing into archives or vaults, checksums and zip creation work on local files only, and Telos Files has no settings screen of its own.

### Telos Calculator

A calculator in the launcher: standard and scientific keypad, VAT, unit and currency conversion and a history. The layout is modeled on the OxygenOS calculator, the look follows the Telos theme. No permissions, no account. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/calculator/).

- Round keys: a standard keypad (4 columns, with a `00` key and the decimal separator of your language) and a scientific keypad (5 columns: sin, cos, tan, rad, deg, log, ln, brackets, inv, factorial, power, root, π, e), switched with the top bar button or shown in landscape
- Percent that works like on a pocket calculator (`200 + 10%` is 220), live result under the expression, `inv` for inverse functions
- **VAT:** any rate (24 by default, chips for 24, 13, 6, 0), add VAT or remove VAT, shows without VAT, the VAT and with VAT at once, tap to copy; chips `+ VAT` and `− VAT` in the calculator itself
- **Unit converter** with a grid of categories: currency, length, area, volume, weight, temperature, speed, pressure, energy, numeral system (binary, octal, decimal, hexadecimal), time and data. Currency needs the currency rates of the unit converter turned on
- History with date and time (swipe down on the display or from the menu), up to 100 entries
- Quick Settings tile, texts in English and Greek

**Status and limitations:** no floating window mode, 12 significant digits, numeral systems for whole numbers only.

### Telos Screenshot

Full, partial and scrolling screenshots, taken through the accessibility service so that Telos can edit them. No cloud, nothing is read from the picture. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/screenshot/).

- **Screenshot** (Android 11+ for Telos' own picture, the system screenshot on Android 9 and 10), **partial screenshot** (rectangle, oval or free shape) and **scrolling screenshot** (experimental: swipes and joins up to 12 screens by matching rows)
- **Editor:** crop, pixelate, blur, draw (six colors, pen width), undo (8 steps); saving makes a new picture and keeps the original
- Notification with share, edit and delete, a delay of 3, 5 or 10 seconds, PNG or JPEG, a list of the screenshots Telos made (`Pictures/Screenshots`)
- Also a tool in the floating launcher

**Status and limitations:** no three-finger gestures or button combinations (they belong to the system), no text extraction, translation or AI features, protected content is black, the scrolling screenshot can fail on fixed headers, videos and animations.

### Telos Screen Recorder

Records the screen to a video with MediaProjection. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/screen-recorder/).

- Resolution (screen, 1080p, 720p, 480p), 30 or 60 fps, three qualities, the microphone as an option
- Countdown (none, 3 or 5 s), show touches (needs the permission to change system settings, the old value is restored), stop when the screen turns off
- Pause, resume and stop from the notification and from the app, recordings in `Movies/Telos` listed in the app (play, share, delete)
- Starts from the app or from the floating launcher

**Status and limitations:** Android asks for the capture permission every time, no system sound (only the microphone), protected content is black, no Live Alert or Dynamic Island entry, no editing.

### Telos Notes

A notes app with local storage and optional sync. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/notes/).

- Grid of notes with colours, pins, labels, archive, trash, search, share; checklists as Markdown lines
- Sync with a folder of Markdown files (Obsidian, Logseq, Syncthing or a cloud app's folder) and with Nextcloud Notes (app password), newest change wins; trashed notes stay on the server until deleted for good, and the folder sync never trashes notes because of an empty, failed or suspicious listing
- Import Google Keep (Takeout json or zip), Evernote (.enex), Markdown and text files and zips of them (Notion and Joplin exports)
- Importing the same file twice skips duplicates and reports how many
- Matching notes show up in the launcher search
- The **Notes** part of the Telos backup

**Status and limitations:** no direct sync with Google Keep, OneNote, Microsoft Sticky Notes, Apple Notes or Notion (no usable public API for a launcher, import only where an export exists), no background sync, no images, notes are not encrypted.

### Telos Calendar

A calendar on the calendar storage of Android. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/calendar/).

- Month view with an agenda of the chosen day, create, edit and delete events (all day, location, description, repeat daily to yearly, one reminder)
- A local calendar that needs no account, plus every calendar the system has: Google, CalDAV through DAVx5, Exchange. Telos asks the system to sync, the account does the sync
- A list of the next 30 days next to the month view
- Show or hide calendars, import and export `.ics`
- The local calendars are the **Calendar** part of the Telos backup, with all reminders and deleted occurrences of repeating events (changed single occurrences are not backed up)

**Status and limitations:** month and agenda only (no week or day grid), no guests or tasks, repeating events are edited as a whole series, Telos does not log in to Google or CalDAV itself.

### Telos Voice Recorder

A voice recorder with a list, search and a service that keeps recording with the screen off. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/voice-recorder/).

- Big red record button, pause and resume, a waveform, a notification with pause and stop
- AAC (.m4a) or Opus (.ogg, Android 10+), standard or voice mode, three qualities
- List newest first with duration and date, search, rename, share, delete, playback with a seek bar through the speaker or the earpiece
- The call recordings of Telos Phone in the same list (they stay encrypted)
- Also a tool in the floating launcher

**Status and limitations:** recordings are in the private storage of Telos and not encrypted (call recordings are), no AI assistant, transcription, markers, trimming or WAV.

### Telos Photos

A photo gallery, a metadata (EXIF) tool, a simple photo editor and a document viewer in one app. No cloud, no account and no network features of its own. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/photos/).

**Gallery and viewer**

- Photos tab by date (4 columns, newest first) and Albums tab by folder (sorted by picture count)
- Full screen viewer with swipe, pinch zoom from 1x to 8x, double tap to 2.5x, pan, and share, edit, details and delete (Android 11+, media library pictures)
- Opens images from other apps ("Open with" and share) and pages through a folder when opened from Telos Files

**EXIF tools**

- EXIF viewer (size, GPS position, date taken, camera, lens, exposure, aperture, ISO, focal length, software, artist, description, copyright) and editor
- Remove location, remove all metadata (both change the original file), and **share without metadata**, which makes a clean JPEG copy and leaves the original untouched
- Metadata removal cleans the known EXIF fields and does not guarantee that every kind of embedded data (such as XMP or embedded thumbnails) is gone

**Editor** (saves a copy as JPEG in `Pictures/Telos`, the original stays untouched)

- Rotate in 90 degree steps, horizontal flip, crop ratios (1:1, 4:3, 3:4, 16:9, 9:16, centered), brightness, contrast and saturation sliders, filters (Original, Mono, Sepia, Warm, Cool, Invert)

**Document viewer**

- PDF page by page with zoom, plain text and code (editable and saved back to the file, up to 2 MB), Word (.docx), Excel (.xlsx), PowerPoint (.pptx), OpenDocument (.odt, .ods, .odp), RTF and EPUB
- Reads documents handed to it from Telos Files or other apps' "Open with", with an **Open with** button to send them to another app

**Status and limitations:** Office, OpenDocument, RTF and EPUB files are shown as extracted text and tables only: the page layout, fonts, images and formulas are not reproduced. Old binary `.doc`, `.xls` and `.ppt` files and password-protected PDFs cannot be opened. No search in documents, no annotations, no free crop, no cloud albums, no face recognition and no slideshows. The editor always saves JPEG.

### Telos Music

A music player for the songs stored on your phone. It plays local files only: there is no streaming service and no account of its own. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/music/).

**Library and playback**

- Library by songs, albums and artists, accent-insensitive search (also for Greek)
- Play queue from the list you tapped, shuffle, repeat (off, all, one), seek, sleep timer (15 to 90 minutes)
- Mini player and full player; notification, lock screen and headset button controls; pauses when headphones are unplugged; shows up in the launcher's media control

**Lyrics**

- Synchronized lyrics (and plain text) from [LRCLIB](https://lrclib.net), with the current line highlighted, cached on the device

**Scrobbling**

- [Last.fm](https://www.last.fm), [Libre.fm](https://libre.fm) and [ListenBrainz](https://listenbrainz.org), each switched on separately, with an offline queue of up to 500 scrobbles for when you are offline (the queue is sent with the next successful scrobble)
- A "now playing" message, and a scrobble after half the song or 4 minutes. Secrets are stored encrypted with a Keystore key.

**Tag editor**

- Edit title, artist, album, album artist, genre, year, track number and cover art for MP3, FLAC, M4A and OGG files. It writes to the audio file itself, so keep a backup of music you care about.

**Status and limitations:** Local files only. No user playlists, no queue editing, no equalizer, no crossfade, no sorting options. Only music that Android has indexed is listed. Lyrics depend on LRCLIB and need a network connection the first time. Music is guarded by the crash guard.

### Telos Video

A video library and player. It plays the videos on your phone, web streams, and torrents while they download. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/video/).

**Library**

- Tabs Library, Movies, Series and Folders; continue watching (up to 10), resume between 2 and 95 percent watched, progress and watched marks
- Titles recognized from file names (`Show.S01E02.mkv`, `Show 1x02`, `Movie Title 2019`)
- Movie and series posters with descriptions from [Wikipedia](https://www.wikipedia.org), or from [TMDB](https://www.themoviedb.org) with your own free key (ratings too)

**Player**

- Full screen player: swipe to seek, brightness (left) and volume (right), double tap to jump 10 seconds, press and hold for double speed
- Speed presets (0.5x to 2x), picture size (fit, fill, zoom), audio and subtitle track choice, repeat, sleep timer, picture in picture
- FFmpeg software decoders for AC3, E-AC3, DTS, TrueHD and more
- Also used when another app opens a video; playlists for series and folders

<details>
<summary><b>Web streams, torrents, subtitles, Trakt and the separate process</b></summary>

- **Web streams:** HLS, DASH, RTSP and plain video links via "Play from the web". A magnet link or address on the clipboard is filled in automatically.
- **Torrents:** magnet links, `.torrent` addresses and files open in Telos Video from any app or browser. The video downloads in order while it plays, through a local-only address, on Wi-Fi only by default, and everything is deleted when the player closes. Only play content you are allowed to watch: torrent networks show your IP address to other peers.
- **Subtitles:** external files (SRT, VTT, ASS / SSA, TTML), embedded tracks, and online search or automatic download from [OpenSubtitles](https://www.opensubtitles.com) (your own API key and account), also for torrents.
- **Trakt.tv:** sign in with a device code, scrobbling of movies and episodes, watched marks in the library, add titles to the watchlist (your own Trakt application).
- **Separate player process (experimental):** Video services > Play in a separate process, so a crash of the player does not close the launcher.
- **Video services dialog:** TMDB key, OpenSubtitles key and account, languages, automatic subtitles, torrents Wi-Fi only, separate process, Trakt. Keys and passwords are encrypted with the Android Keystore.

</details>

**Status and limitations:** Decoding depends on the phone, and some codecs may not play. TMDB and OpenSubtitles need your own keys. Title detection relies on file names. Torrent streaming needs healthy peers and has no catalog. No Chromecast, DLNA, offline downloads, equalizer or subtitle styling. The separate process is experimental. Video is guarded by the crash guard.

### Telos Radio

Internet radio with your own station collection. Behavior and logic follow [Transistor](https://codeberg.org/y20k/transistor) (compared with 4.3.9). [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/radio/).

**Stations**

- Station collection with add by address, rename and remove
- Station discovery through [Radio-Browser](https://www.radio-browser.info/) with mirror fallback (the 50 most popular matches, broken streams hidden)
- Tabs Collection, Search and History

**Import, export and backup**

- M3U / PLS import, M3U export and JSON backup and restore

**Playback**

- Playlist links resolved to the real stream with fallback streams, HLS (`.m3u8`) played directly
- Current track from the stream metadata with a track history (newest 500)
- Next / previous buttons switch stations (notification, lock screen, headset)
- Sleep timer (15 to 90 minutes), pauses when headphones are unplugged, background playback with a notification

**Status and limitations:** Needs a network connection, track names exist only when the station sends them, and search lists only Radio-Browser. No stream recording, no alarm clock and no equalizer. Radio is guarded by the crash guard.

### Telos Store

An app installer and updater that works like [Obtainium](https://github.com/ImranR98/Obtainium) (the behavior is re-implemented, no code was copied). There is no central Telos catalog and no account. It also manages the Telos apps. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/store/).

**Sources**

- GitHub, GitLab (also self-hosted), Codeberg / Forgejo / Gitea, F-Droid, IzzyOnDroid, SourceForge, direct APK links and any web page that links to APKs
- The APK that fits the phone's CPU is chosen, older releases are used when the newest has no APK

**Per-app settings**

- Track only, stay on this version, skip a version, exclude from background updates, file name filter, pre-releases, category, note, rename

**Updates and installing**

- Update checks in the background (interval and Wi-Fi only settings, a notification), "update all", "check now"
- Install and uninstall with Shizuku or root backends (silent) or the system installer; an APK for another package is refused
- Optional GitHub token to raise the request limit

**Organizing**

- Search, filters (updates, installed, not installed, track only) and categories
- Add the apps that are already installed (found through F-Droid and IzzyOnDroid)
- Import and export in the Obtainium export format, and `obtainium://` links open in the Store

**Managing the Telos apps**

- The Telos apps (Phone, Messages, Radio, Music, Video, Photos, Files, Calculator, Screenshot, Screen Recorder, Voice Recorder) are listed with what each one does; "installing" one shows its icon in the app grid and in search, "removing" hides it. Telos Store itself cannot be removed.
- Crash guard: an app that crashes (or hangs) twice within a day is switched off automatically, with a notification pointing to the Store; "installing" it again resets the counter.

**Status and limitations:** Only the listed sources: APKMirror, Uptodown, Aptoide, APKPure, the Play Store, Huawei, Tencent, RuStore and Telegram entries are skipped when importing. Split APKs and apps that need a login are not supported, direct APK links have no version check, and silent installs need Shizuku or root. Installing APKs from outside an app store means you trust the source.

### Smart Freeze

Freeze or hide apps you do not need right now. It is a launcher feature in Settings (Freeze Manager and Smart Freeze Dashboard), not a Telos app, so it does not appear in the Store. [Docs](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/freeze/).

**Backends**

| Backend | Needs | Status |
| --- | --- | --- |
| Shizuku | The Shizuku app running and permission granted | Suspend, disable, force stop, clear cache |
| Root | A rooted device | Untested on many devices |
| Device owner | Telos set as device owner with `adb` (guided setup screen) | Untested on many devices; no force stop or cache clearing |
| Island | The Island app, only for apps already in the Island profile | Request only, cannot be automated from the background |
| Dhizuku | The Dhizuku app | **Not available:** a placeholder that does not freeze anything yet |

**Freezing**

- **Suspend** (default) or **Disable** per app; per-app states: Not managed, Auto-freeze (Suspend), Auto-freeze (Disable), Never freeze
- Frozen apps are unfrozen automatically when launched from the launcher; hide frozen apps, or show them grayscale or with a snowflake badge
- Protected packages (the Android system, System UI, Settings, keyguard, telecom server, Google Play services, Play Store and Telos) are never frozen
- Advanced features: Force stop and Clear cache (Shizuku or root)
- **Freeze** home screen widget with a "Freeze now" button; "N apps ready to freeze" notification

<details>
<summary><b>Automatic freezing, profiles, exclusions, dashboard and work profile</b></summary>

- **Profiles:** Battery Saver (30 min after screen off, or on battery saver), Balanced (15 min, default), Aggressive (5 min, and immediately at screen off), Ultra Aggressive (1 min, immediately, also freezes apps with a paused media session), Custom. A context profile can override the freeze profile.
- **Exclusion rules** skip: apps marked Never freeze, the foreground app (needs Usage access), apps with an ongoing notification or foreground service, Android Auto / car mode, apps with an active media session (strict mode), optionally apps playing music and apps with network activity above a threshold (100 KB/s by default).
- **Dashboards:** currently frozen apps, history with freeze and unfreeze counters, per-app status (Normal, Suspended, Disabled, Hidden, time used today), backend status, counters and a quick Auto-Freeze toggle for each app.
- **Work profile:** status, a Work Mode switch to pause or resume work apps, optional authentication before pausing, and **Initialize Telos Sandbox** to start Android's managed profile setup. Shizuku and root can target apps in a work profile.
- **Clone to Sandbox** (experimental): copies an app's APK into the work profile. New and untested across devices, and it needs a work profile.

</details>

**Status and limitations:** Which backend works depends on the device and Android version. Root and device owner are untested on many devices. **Dhizuku is not available.** Island cannot confirm that an app was frozen. Work Mode and Clone to Sandbox are experimental. Freezing system apps can break core phone functions. The foreground-app check needs Usage access.

---

## Languages

Telos is translated into Greek (first), German, French, Italian, Spanish, Portuguese (Portugal and Brazil), Dutch, Polish, Czech, Slovak,
Slovenian, Croatian, Bulgarian, Romanian, Hungarian, Danish, Swedish, Finnish, Estonian, Latvian, Lithuanian, Irish, Maltese
(all official languages of the European Union), Russian, Ukrainian, Turkish, Norwegian, Chinese (Simplified and Traditional), Japanese, Korean,
Arabic, Hindi and Bengali, plus the languages that came with the original launcher. The texts that Telos added were translated by machine and are
not checked by native speakers, a missing string falls back to English, and the documentation is in English only.
New strings are translated into all of these languages in the same change. Details: [Languages](https://koukomin.github.io/Telos-Launcher/docs/user-guide/telos/launcher/languages).

## Honest status

Some features are new and not tested on every device. This is what the project's docs say today.

| State | Features |
| --- | --- |
| **Untested on real devices** | MMS (Messages), SIP / VoIP, root features (the root call recording backend, root explorer, cellular network mode with Shizuku or root, root and device owner freeze backends), desktop window snapping |
| **Experimental** | Cryptomator vaults (read only), Clone to Sandbox, overlays (floating launcher, Dynamic Island, desktop mode), App Lock, video wallpapers, app recommendations, the separate video process, SIP |
| **Not supported** | iCloud, Mega, gocryptfs, EncFS, VeraCrypt, Dhizuku (not available) |
| **Limited by design** | Office documents are shown as extracted text only (no layout, formulas or images; old `.doc`, `.xls`, `.ppt` not opened) |
| **Debug and nightly builds only** | Feed and the Smartspacer integration |
| **Disabled or hidden in this build** | Extra home screens, the Favorites widget in the picker, fallback icon pack setting, Dynamic Island timer (no source) |

## Planned

- SIP: text messages, video and Bluetooth routing, and Android Telecom integration (SIP calls are
  handled by their own call screen for now)

## Build

Telos is a Kotlin/Gradle multi-module Android project (package `de.mm20.launcher2`). It needs a JDK 21
toolchain and Gradle 9.4.1 via the wrapper. Build variants combine a build type (`debug`, `release`,
`nightly`) with a product flavor (`default`, `fdroid`).

```sh
./gradlew assembleDefaultDebug      # build a debug APK
./gradlew installDefaultDebug       # install to a connected device or emulator
./gradlew test                      # run all unit tests
./gradlew :core:base:test           # run the tests of a single module
```

Developer documentation (module structure, plugin contracts) lives in `docs/docs/developer-guide/`.

## Contributing

- Bug fixes and smaller enhancements: send a pull request directly.
- Bigger new features: open an issue first to discuss the design before implementing it.
- All contributed code must be GPL-3.0-or-later licensed (except contributions to `plugins/sdk` and `core/shared`, which are Apache-2.0).

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
| [Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | Store behaviour and features: sources, per-app settings, update flow, export format, links, update broadcasts (the behaviour is re-implemented, no code was copied) |
| [Transistor](https://codeberg.org/y20k/transistor) | MIT | Radio player behaviour |
| [Radio-Browser](https://www.radio-browser.info/) | public API | Radio station directory |
| [Shizuku](https://github.com/RikkaApps/Shizuku) and [Dhizuku](https://github.com/iamr0s/Dhizuku) | see project | Privileged operations (freeze, recording, install, network mode) |
| [baresip](https://github.com/baresip/baresip) and [baresip-studio](https://github.com/juha-h/baresip-studio) | BSD-3-Clause | SIP engine and its JNI bridge |
| [LRCLIB](https://lrclib.net) | open API | Song lyrics for Telos Music |
| [Next Player](https://github.com/anilbeesetti/nextplayer) and [NextLib](https://github.com/anilbeesetti/nextlib) | GPL-3.0 | Player gestures and features (design), FFmpeg decoders for Media3 (library, uses FFmpeg under LGPL-2.1) |
| [mpv-android](https://github.com/mpv-android/mpv-android) and [mpvKt](https://github.com/abdallahmehiz/mpvKt) | MIT / Apache-2.0 | Ideas for gestures, speed presets and sleep timer (no code) |
| [Trakt.tv](https://trakt.tv) | API terms | Scrobbling and watched marks in Telos Video |
| [Last.fm](https://www.last.fm/api), [Libre.fm](https://libre.fm) and [ListenBrainz](https://listenbrainz.org) | open APIs | Scrobbling in Telos Music |
| [libtorrent4j](https://github.com/aldenml/libtorrent4j) and [libtorrent](https://www.libtorrent.org) | MIT / BSD-3-Clause | Torrent streaming in Telos Video |
| [TMDB](https://www.themoviedb.org) | API terms | Posters and descriptions in Telos Video (not endorsed or certified by TMDB) |
| [OpenSubtitles](https://www.opensubtitles.com) | API terms | Subtitle search and download in Telos Video |
| [TagLib wrapper (Kyant0/taglib)](https://github.com/Kyant0/taglib) | Apache-2.0 | Reading and writing audio tags; it bundles [TagLib](https://taglib.org/) (LGPL-2.1 / MPL-1.1 upstream) |
| [Solid Explorer](https://play.google.com/store/apps/details?id=pl.solidexplorer2) and [MiXplorer](https://forum.xda-developers.com/t/app-2-2-mixplorer-v6-x-released-fully-featured-file-manager.1523691/) | proprietary / freeware | Layout and feature ideas for Telos Files (no code) |
| [sshj](https://github.com/hierynomus/sshj), [smbj](https://github.com/hierynomus/smbj), [Apache Commons Net](https://commons.apache.org/proper/commons-net/), [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/), [OkHttp](https://square.github.io/okhttp/), [Bouncy Castle](https://www.bouncycastle.org/) | Apache-2.0 / MIT | SFTP, SMB, FTP, archives, WebDAV and cloud HTTP, cryptography (libraries) |
| [Cryptomator](https://cryptomator.org) vault format | specification (GPL-3.0 reference code, none used) | Reading Cryptomator vaults, implemented from the published format description |
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
