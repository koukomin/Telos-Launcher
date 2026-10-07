# Widgets and feed

Widgets show information at a glance. Telos has its own built-in widgets, can host any Android app
widget, and can show small widgets that a [plugin](./plugins-integrations) provides. A feed is a page of
content from a separate app. This page covers both, and the controls that arrange them.

## Where widgets live

| Place | How to reach it | Notes |
| --- | --- | --- |
| Widgets page | Swipe up (default gesture) | Up to four separate widget pages exist. Each can be bound to its own gesture in Settings > Gestures |
| Home screen | Settings > Home screen > **Widgets on home screen** | Moves page 1 of the widgets under the clock |
| Dock | Settings > Home screen > Dock > custom dock | An Android widget can fill a dock slot |
| Clock | The clock widget at the top | Not removable, see [Home screen](./home-screen#clock) |

::: tip Four widget pages
Open Settings > Gestures, pick a gesture and choose **Widgets (2)**, **Widgets (3)** or **Widgets (4)**.
Each page lists what it contains (or "Empty") so you can tell them apart. When widgets are on the home
screen, page 1 is no longer offered as a gesture target, because it is the home screen.
:::

## Add, move, resize and stack

1. Open the widgets page (swipe up) and tap **Edit widgets**. The button can be hidden in Settings >
   Home screen > Edit button. A short tutorial runs the first time and can be shown again with **Reset
   tutorial**.
2. Tap **Add widget**. The picker lists the built-in widgets, plugin widgets and the Android widgets of
   your apps. You can search in it.
3. Drag the handle on a widget header to reorder it. Drag its bottom edge to resize it.
4. Tap the **+** icon on a widget to **stack** it with another one in the same slot. Swipe left or right on
   a stack to switch between its widgets. Use **Remove from stack** to split them.
5. **Replace** swaps a widget for another one, **Remove** deletes it.

Android widgets have extra options: **Borderless**, **Background card** and **Use theme color**, and
**Configure widget** when the app offers a configuration screen.

## Built-in widgets

| Widget | What it shows | Needs | Settings |
| --- | --- | --- | --- |
| At a Glance | One item: weather, the next calendar event, or the battery state | Calendar permission for events | None |
| Weather | Forecast of the selected [weather provider](./plugins-integrations#weather) | Location or a fixed location | Compact mode |
| Calendar | Events of the day, with previous and next day buttons | Calendar permission | Calendars, hide completed tasks, Tasks |
| Reminders | Tasks from the Tasks app | Tasks permission | None |
| Media | Playing media with controls | Notification access | Interactive progress bar |
| Note | A free text note | Nothing | Link to a file |
| Battery | Charge level and charging state | Nothing | None |
| Network | Wi-Fi, mobile data, Ethernet, or no connection | Nothing | None |
| System | RAM and storage usage | Nothing | None |
| Freeze | The [Smart Freeze](../freeze) candidates, with a **Freeze now** button | Freeze set up | None |
| Android widgets | Any app widget installed on the phone | The app | Per widget |
| Plugin widgets | Rows of title, subtitle and value from a plugin | A widget plugin | None |

The [Telos Phone](../phone) app adds widgets of its own (recents and direct call).

::: warning The Favorites widget is not offered
The picker no longer lists the **Favorites (Apps)** widget. Favorites are meant to stay in the app grid
and drawer. Widgets of that type that were added earlier keep working. See the existing
[Favorites widget](../../widgets/favorites-widget) page for how it behaved.
:::

### At a Glance

At a Glance shows only one thing. It scores three candidates and shows the highest:

- the current **weather**,
- the **battery**, when it is charging or low,
- the next **calendar event** that starts soon (events in the next day are considered).

If nothing qualifies it reads "Nothing to show right now". It is a quiet, single-slot summary, not a
replacement for the weather or calendar widget.

### Note widget

The note widget keeps its text in the launcher. **Link to file** keeps the note in sync with an external
text file (for example one that Syncthing syncs). If the file and the last saved note differ, Telos asks
which version to keep (**Conflict**). If the file cannot be written, a copy stays in the launcher's own
storage. Other actions: new note, share, save, dismiss with undo. See also the existing
[Notes](../../widgets/notes-widget) page.

### Calendar and reminders

The calendar widget uses the same calendars as search. Pinned events appear in it rather than in the
favorites. Pick which calendars it shows in the widget settings. The reminders widget reads the Tasks app
and shows "No reminders" when empty. Tasks setup is in
[Plugins and integrations](./plugins-integrations#tasks).

### Media widget

The media widget lists sessions of music apps. By default only music apps are recognized. Choose more apps
in Settings > Integrations > Media control. The Telos [Music](../music), [Video](../video) and
[Radio](../radio) players show up like any other player. See
[Plugins and integrations](./plugins-integrations#media-control).

## Feed

<Badge type="warning" text="debug and nightly only" />

A feed is a content page (for example Google Discover through a bridge, or an RSS reader) that a
separate app provides. Telos only hosts it.

| Item | Detail |
| --- | --- |
| Availability | Gated by a feature flag that is on only for debug and nightly builds. In a release build the Feed settings page and the **Feed** gesture action are hidden |
| Setup | Settings > Integrations > **Feed**: switch it on and pick a feed provider app |
| Opening it | Assign **Feed** to a gesture (the settings summary suggests swiping right, but the default swipe right opens the Web Apps Panel, so change one of them) |
| Without a provider | "No feed providers installed" |
| Providers | See the original [Feed](../../integrations/feed) page for tested providers |

::: warning Not in release builds
If you installed a release build and cannot find Feed anywhere, that is expected. Use a nightly build
if you want it.
:::

## Context profiles and widget pages

A [context profile](./desktop-and-overlays#context-profiles) can switch which widget page the "Widgets"
gesture opens, for example showing a work page during office hours.

## Limitations

- The Favorites widget cannot be added anymore.
- Plugin widgets are read-only lists. They cannot draw custom layouts.
- Android widgets depend on the app that provides them.
- The feed needs a non-release build and a third party provider.
