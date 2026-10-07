# The launcher

The launcher is the foundation of Telos. It comes from [Kvaesitso](https://github.com/MM2-0/Kvaesitso) and
is built around one idea: **search is the main way to reach things**. This page walks through its pieces.
The Telos apps are described separately in the [overview](./).

## Search

One search bar finds everything at once. Results are grouped and can be filtered.

| Searches | Notes |
| --- | --- |
| Apps | Also web apps, per-item labels and tags |
| Contacts | Including the Telos Phone contacts |
| Calendar | Reads the Android system calendar, so any synced calendar works (for example DAVx5) |
| Files | Local files, plus file plugins |
| Websites and Wikipedia | Online results |
| Locations | Places and opening hours, via plugins |
| Calculator and unit converter | Type an expression or a conversion |
| Quick actions | Search actions such as web search or calls |

See [Calculator](../search/calculator), [Unit Converter](../search/unit-converter),
[Quick Actions](../search/quickactions), [Online Results](../search/online-results) and
[Filters](../search/filters).

## Home screen and widgets

The home screen holds widgets. Telos has independent grids for home, search and dock, home screen
folders with covers, and advanced icon label and dock styling.

| Widget | Page |
| --- | --- |
| Clock | [Clock](../widgets/clock) |
| Calendar | [Calendar](../widgets/calendar-widget) |
| Weather | [Weather](../widgets/weather-widget) |
| Music | [Music widget](../widgets/music-widget) |
| Notes | [Notes](../widgets/notes-widget) |
| Favorites | [Favorites](../widgets/favorites-widget) |

Telos Phone adds home screen widgets for recents and direct call.

## Feed

A feed page to the left of the home screen, provided by a third party app. See [Feed](../integrations/feed).

## Favorites and tags

Pin the things you use most as [favorites](../concepts/favorites) and group any item with
[tags](../concepts/tags). Both can be used to filter search results.

## Icons, themes and customization

- [Color schemes](../customization/color-schemes) and dynamic colors
- [Themed icons](../customization/themed-icons) and icon packs
- [Per-item customization](../customization/per-item-customization) for names and icons
- Video live wallpapers with dynamic color extraction
- Greek and German translations in addition to the original locales

## Plugins

Third party apps can add search results, weather and calendar data through the plugin SDK. See
[Plugins](../concepts/plugins). Developers: [Plugin development](../../developer-guide/plugins/get-started).

## Weather and media control

- [Weather](../integrations/weather) with plugin based providers
- [Media control](../integrations/mediacontrol) shows what is playing and offers play/pause and skip.
  The Telos music, video and radio players work with it like any other player.

## Desktop mode and overlays

| Feature | What it does |
| --- | --- |
| Desktop mode | Freeform windows, snapping and tiling presets, a taskbar with running tasks and a workspace grid |
| Dynamic Island | An overlay that shows call state and more |
| Floating launcher | The launcher as a floating window |
| Context profiles | Routines that change behavior depending on context |
| Web apps panel | Quick access to web apps |
| App lock | Lock apps, with intruder photos |

::: details Not sure a feature is on your device?
Some of these depend on Android version and permissions. Look in the launcher settings for the matching entry.
:::

## Resource protection and crash guard

The Telos media apps are protected so that a failure in one of them does not take the launcher down:

- An app that crashes or hangs twice within a day is switched off, with a notification pointing to the
  [Store](./store).
- Background tasks of these apps log failures instead of ending the launcher.
- Switched-off apps have their services and screens disabled. Services of Radio, Music, Video and Photos
  stop by themselves when nothing is playing.
- The video player can run in a separate process (see [Video](./video)).

See also [Crash reporter](../troubleshooting/crashreporter) and the developer page
[Protection and optimization](../../developer-guide/project-structure/protection-and-performance).
