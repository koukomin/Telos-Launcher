# The launcher

The launcher is the foundation of Telos. It comes from [Kvaesitso](https://github.com/MM2-0/Kvaesitso) and
is built around one idea: **search is the main way to reach things**. Telos keeps all of it and adds its
own pieces on top (overlays, desktop mode, app lock, context profiles, video wallpapers and more).

This section is the detailed description of the launcher's own features. It was written from the code, the
settings screens and the string resources, and it says so when something is switched off, hidden or
untested in the current build. The built-in Telos apps (Phone, Messages, Files and so on) have their own
pages, see [Telos at a glance](../).

## Map of this section

| Page | What it covers | Read it when you want to |
| --- | --- | --- |
| [Search](./search) | Every search source, filters, ranking, result groups, quick actions, search settings | Find things faster, or turn sources on and off |
| [Home screen](./home-screen) | Home and widget pages, clock, dock, gestures, wallpaper, search bar | Set up the main screen and its gestures |
| [Widgets and feed](./widgets-feed) | The widget list, how to add and stack them, the feed | Add widgets or understand why a widget is missing |
| [Favorites and tags](./favorites-tags) | Pinning, frequently used items, tags, folders, hiding items | Organize apps and results your own way |
| [Customization](./customization) | Themes, colors, shapes, fonts, grid, icons, badges, animations, per-item changes, language | Change how Telos looks |
| [Plugins and integrations](./plugins-integrations) | Plugin types, weather, media control, Nextcloud, ownCloud, Wikipedia, Tasks, Breezy, Smartspacer | Connect other apps and services |
| [Desktop mode and overlays](./desktop-and-overlays) | External display shell, floating launcher, Dynamic Island, web apps, context profiles | Use Telos on a monitor or over other apps |
| [Privacy and protection](./privacy-protection) | Settings lock, launcher lock, App Lock, hidden items, crash guard, idle behavior, backup | Protect your data and the launcher itself |
| [Performance](./performance) | Tuning settings, startup, baseline profile, APK size choices | Make Telos faster or lighter |

## Where to find a setting

| Looking for | Path in Settings |
| --- | --- |
| Search sources, filters, favorites, hidden items, tags, locks | Search |
| Clock, widgets, dock, wallpaper, search bar, desktop mode | Home screen |
| Grid, icons, badges, folders | Grid and icons |
| Colors, fonts, shapes, presets, import and export themes | Appearance |
| Gestures | Gestures |
| Web apps and the web apps panel | Web app shortcuts |
| Dynamic Island | Dynamic Island |
| Weather, media, Nextcloud, Tasks and similar | Integrations |
| Language, units, time format | Language and region |
| Performance, context profiles, plugins, App Lock, work profile, backup, debug | Advanced settings |
| Smart Freeze | Freeze Manager, see [Smart Freeze](../freeze) |

## What Telos adds to Kvaesitso

| Area | Added or changed |
| --- | --- |
| Home screen | Dock with custom slots and multiple docks, shutters, video wallpapers, up to four widget pages, folder covers, independent grids for home, search and dock |
| Widgets | Reminders, Freeze, Battery, Network, System and At a Glance widgets, widget stacks, linked notes |
| Search | Search results for web apps, the Telos apps and frozen apps, a text-matching ranking with usage weights, optional app recommendations |
| Overlays | Floating launcher, Dynamic Island, desktop mode, web apps panel, assistant mode |
| Automation | Context profiles |
| Protection | Settings lock, launcher lock, App Lock with intruder photos, crash guard, exception containment |
| Customization | Presets, font scale, extra icon shapes, more badge options, German and Greek |

## Status of features

Some features are present in the code but limited or switched off in this build. The pages explain each
in detail. In short:

| Feature | State |
| --- | --- |
| Extra home screens (up to nine pages) | Disabled by a kill switch. Use the four widget pages instead |
| Feed and Smartspacer integration | Only in debug and nightly builds |
| Favorites widget | No longer in the widget picker |
| Fallback icon packs | Supported by the icon service, but no setting in the UI |
| Floating launcher settings | Page exists, but the settings list does not link to it |
| Dynamic Island timer | Slot exists, nothing starts it |
| Desktop quarter snapping | In the code, not in the taskbar menu |
| Overlays, desktop mode, App Lock, context profiles | Marked experimental, source comments say they need real-hardware verification |

::: tip Reading the badges
<Badge type="warning" text="experimental" /> means the feature works in principle but is new or unverified
on many devices. <Badge type="warning" text="untested" /> means the code says it has not been tried on
real hardware. <Badge type="warning" text="debug and nightly only" /> means a release build hides it.
:::

## Original Kvaesitso pages

These pages from the original launcher are still valid and are linked from the sections above:
[Filters](../../search/filters), [Online results](../../search/online-results),
[Quick actions](../../search/quickactions), [Calculator](../../search/calculator),
[Unit converter](../../search/unit-converter), [Favorites](../../concepts/favorites),
[Tags](../../concepts/tags), [Plugins](../../concepts/plugins),
[Color schemes](../../customization/color-schemes), [Themed icons](../../customization/themed-icons),
[Per-item customization](../../customization/per-item-customization),
[Weather](../../integrations/weather), [Media control](../../integrations/mediacontrol),
[Feed](../../integrations/feed), and the widget pages for the
[clock](../../widgets/clock), [calendar](../../widgets/calendar-widget),
[weather](../../widgets/weather-widget), [media](../../widgets/music-widget),
[notes](../../widgets/notes-widget) and [favorites](../../widgets/favorites-widget).

For developers: [project structure](../../../developer-guide/project-structure/modules) and the
[plugin guide](../../../developer-guide/plugins/get-started).
