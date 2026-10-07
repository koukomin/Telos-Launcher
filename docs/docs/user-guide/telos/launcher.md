# The launcher

The launcher is the foundation of Telos. It comes from [Kvaesitso](https://github.com/MM2-0/Kvaesitso) and
is built around one idea: **search is the main way to reach things**. This page walks through its pieces,
one by one. The built-in Telos apps are described in the [overview](./).

::: info Map of this page
[Search](#search) · [Home screen and widgets](#home-screen-and-widgets) · [Feed](#feed) ·
[Favorites and tags](#favorites-and-tags) · [Icons and themes](#icons-themes-and-wallpapers) ·
[Plugins](#plugins) · [Weather](#weather) · [Media](#media-control) · [Context profiles](#context-profiles) ·
[Overlays and desktop mode](#overlays-and-desktop-mode) · [App lock](#app-lock-and-settings-lock) ·
[Protection](#protection-and-crash-guard) · [Resource use](#resource-use)
:::

## Search

One search bar finds everything at once. Results are grouped by type and can be narrowed with
[filters](../search/filters). Each source can be switched on or off in Settings > Search.

| Source | What it finds | Notes |
| --- | --- | --- |
| Apps | Installed apps, app shortcuts, web app shortcuts | Also matches labels and [tags](../concepts/tags). Work and private profiles are included |
| Contacts | Android contacts | Includes the contacts used by [Telos Phone](./phone) |
| Calendar | Events from the Android system calendar | Any synced calendar works, for example DAVx5. Tasks have their own settings |
| Files | Local files, plus file plugins | Nextcloud and ownCloud have their own settings pages |
| Websites | Web pages for a link you type | [Online results](../search/online-results) |
| Wikipedia | Articles | [Online results](../search/online-results) |
| Locations | Places, with opening hours | Provided by plugins and OpenStreetMap |
| Calculator | Type an expression | [Calculator](../search/calculator) |
| Unit converter | Type a conversion, also currencies | [Unit Converter](../search/unit-converter) |
| Quick actions | Web search, calls, messages and more | [Quick Actions](../search/quickactions) |

You can also hide single results, and customize the filter bar and the gestures that open search.

## Home screen and widgets

The home screen holds widgets, optionally above a dock. Grids are configured separately for the home
screen, the search results and the dock. Folders on the home screen can have a cover and are available in
the favorites.

| Widget | What it shows | Page |
| --- | --- | --- |
| Clock | Time and date, several styles | [Clock](../widgets/clock) |
| Calendar | Upcoming events | [Calendar](../widgets/calendar-widget) |
| Weather | Forecast of your weather provider | [Weather](../widgets/weather-widget) |
| Media | Playing media with controls | [Music widget](../widgets/music-widget) |
| Note | A quick note | [Notes](../widgets/notes-widget) |
| Favorites | Your pinned items | [Favorites](../widgets/favorites-widget) |
| Apps | An app list | |
| At a Glance, Battery, Network, System, Reminders | Status at a glance | |
| Freeze | Freeze the opted-in apps with one tap | [Smart Freeze](./freeze) |
| Android widgets, plugin widgets | Anything installed on the phone or provided by a plugin | |

[Telos Phone](./phone) adds its own widgets for recents and direct call.

## Feed

A feed page to the left of the home screen, provided by a third party app. See [Feed](../integrations/feed).

## Favorites and tags

| Concept | Use |
| --- | --- |
| [Favorites](../concepts/favorites) | Pin the things you use most. They show on the favorites widget and the dock and can be sorted and grouped in folders |
| [Tags](../concepts/tags) | Label any item with your own words and find it again by typing the tag |

Both can be used to filter search results.

## Icons, themes and wallpapers

| Piece | What it does | Page |
| --- | --- | --- |
| Color schemes | Built-in schemes, custom themes and dynamic colors | [Color schemes](../customization/color-schemes) |
| Icons | Icon packs, themed icons, shapes, badges, grid size | [Themed icons](../customization/themed-icons) |
| Per-item customization | Rename an item or change its icon | [Per-item customization](../customization/per-item-customization) |
| Typography and shapes | Fonts and corner shapes of the UI | |
| Wallpaper | Static photos and **video wallpapers**, with dimming and blur | |
| Languages | Greek and German in addition to the original languages | |

## Plugins

Third party apps can add search results, weather and calendar data through the plugin SDK. Install the
plugin like any app, enable it in Settings > Plugins and allow the permission dialog. See
[Plugins](../concepts/plugins). Developers: [Plugin development](../../developer-guide/plugins/get-started).

## Weather

[Weather](../integrations/weather) data comes from plugin based providers, and shows in the weather
widget and the clock. Pick a provider and a location in the weather settings.

## Media control

[Media control](../integrations/mediacontrol) shows what is playing and offers play, pause and skip. The
Telos [music](./music), [video](./video) and [radio](./radio) players work with it like any other player.

## Context profiles

Context profiles change launcher behavior automatically. They are only checked while the launcher is open.

| Trigger | Notes |
| --- | --- |
| Manual only | Turn the profile on by hand ("Active now") |
| Time of day | From and to |
| WiFi network | By network name, needs location permission |
| Bluetooth device | By device name |
| Battery saver, charging | Also by charger type |

A profile can override the gestures, the freeze profile ([Smart Freeze](./freeze)), the home widget
page, Do Not Disturb and the screen brightness, and can launch an app when it activates.

## Overlays and desktop mode

| Feature | What it does | Needs |
| --- | --- | --- |
| Dynamic Island | A small pill near the top of the screen for media, calls, timers and charging | Display over other apps; phone state permission for calls |
| Floating launcher | Edge tabs in up to six zones (left or right, top, middle or bottom) that open favorite apps over other apps. Has a file dock to hold dragged text, images and files temporarily | Display over other apps |
| Web apps | Websites as app-like shortcuts with a custom icon, optional notifications, and an optional panel | |
| Desktop mode | A desktop shell with taskbar, start menu, system tray (volume, network, battery, notifications), workspace and clock on a connected external display | A device that can launch apps on a second display |

Desktop mode switches on by itself the first time an external display is connected. Window snapping (left or
right half, quarters, maximized) is available. Real **floating windows** are off by default and need
[Shizuku](https://github.com/RikkaApps/Shizuku): they switch the whole device into freeform windowing until
you turn them off again.

::: details Not sure a feature is on your device?
Some of these depend on Android version and permissions. Look in the launcher settings for the matching entry.
:::

## App lock and settings lock

| Feature | What it does |
| --- | --- |
| App lock | Require authentication each time a locked app (or web app) comes to the foreground. Per-app auto-lock timing. Needs Usage access, and an optional accessibility service to catch apps instantly |
| Intruder photo | Takes a front camera photo on a failed attempt. Stored only on the device, never uploaded. Retention is configurable |
| Lock work profile toggle | Authentication before pausing or resuming the work profile |
| Launcher lock | Require authentication to view the home screen after leaving it, with the device credential or a separate lock |

## Protection and crash guard

The Telos apps run inside the launcher process, so a bug in one of them could take the launcher down.
Protection keeps that contained.

| Layer | Behavior |
| --- | --- |
| Crash guard | An app that crashes or hangs twice within 24 hours is switched off, with a notification pointing to the [Store](./store). Using it for two minutes without a crash resets the counter, and so does installing it again |
| What is guarded | Radio, Music, Video and Photos. Phone and Messages are not guarded because they are default-role apps that must stay reachable |
| Native crashes and hangs | Detected on the next start through Android's exit reasons (Android 11 and later) |
| Exception containment | Background work of the Telos apps logs failures instead of ending the launcher |
| Separate video process | Optional, <Badge type="warning" text="experimental" />. A decoder or torrent crash then ends only the player |

See also [Crash reporter](../troubleshooting/crashreporter) and the developer page
[Protection and optimization](../../developer-guide/project-structure/protection-and-performance).

## Resource use

| Situation | Behavior |
| --- | --- |
| App switched off | Its components are disabled: no memory or CPU, not offered in "Open with" |
| Player services | Started only while something plays, and stop when playback ends |
| Heavy parts (decoders, torrent engine, SIP) | Created on first use, not at launcher start |
| Store updates | One periodic job, no service of its own |
| Settings > Performance | Reduce animations, animation speed, bounce physics, search debounce and icon cache size |
