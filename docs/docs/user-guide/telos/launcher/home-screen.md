# Home screen

The Telos home screen is deliberately small: a clock, a search bar, an optional dock and, if you want
them, widgets. Everything else is one gesture away. This page describes each piece and where to
configure it. The settings live in **Settings > Home screen**, **Settings > Gestures** and **Settings >
Grid and icons**.

## Telos apps on a gesture

In Settings > Gestures, every gesture (swipe up, down, left, right, double tap, long press, pinch and so on) can open a built-in
Telos app directly: choose the gesture, then **Telos apps** and pick Phone, Messages, Notes, Calendar, Calculator and the others.
The app opens with the same zoom or push animation as the other launch gestures. This works even though the Telos apps are
virtual apps that cannot be saved as a normal shortcut. Switching an app off in Telos Store makes its gesture do nothing.

For every app except the Store you get a second choice: **"&lt;App&gt; as a page in the launcher"**. Then the app is not started in a
separate screen but shown as a page of the home screen, which slides in with the gesture (push for swipes, zoom for double tap and
long press) and goes away with the back gesture or by swiping back. Phone, Messages, Notes, Calendar, Calculator, Voice Recorder,
Screen Recorder, Screenshot, Radio, Music, Video, Photos, Files, Downloads and Network can be used this way, including their own sub screens
(for example contact details or the app's settings). The page always starts on the app's first screen and is closed again when you
leave the launcher. The keyboard moves the page up while you type. Lists inside the page scroll first; the page only closes when you
drag further than the list can scroll. Like "open", the page does nothing if the app is switched off in Telos Store. This is not
tested on every device yet; if an app misbehaves as a page, choose "Open" for it instead.

## How the pieces fit

| Piece | What it is | Default |
| --- | --- | --- |
| Clock | The top element of the home screen, with an optional "dynamic zone" | On, bold digital style |
| Widgets page | A scrollable list of widgets, see [Widgets and feed](./widgets-feed) | A separate page, reached by swiping up |
| Search / app drawer | The [search](./search) view with your apps and favorites | Reached by swiping down or tapping the search bar |
| Dock | A row of apps or widgets above the search bar | On with default apps on first start |
| Search bar | Always visible, at the bottom | Bottom, transparent |
| Web apps panel | A page for [web apps](./desktop-and-overlays#web-apps) | Swipe right |

If you switch on **Widgets on home screen**, the widgets move onto the home screen below the clock and
the separate widgets page is no longer needed.

::: warning Extra home screens are switched off
The code has an "extra home screens" feature (up to nine swipeable pages). It is disabled in this
build: the setting is hidden and any stored page count is ignored. Do not expect more than one home
page.
:::

## Gestures

Settings > Gestures assigns an action to each gesture. A gesture can also be overridden temporarily by
a [context profile](./desktop-and-overlays#context-profiles) (swipe down, left, right, up, double tap
and long press only).

| Gesture | Default action |
| --- | --- |
| Swipe down | Search / app drawer |
| Swipe up | Widgets |
| Swipe left | Do nothing |
| Swipe right | Web Apps Panel |
| Double tap | Turn off screen |
| Long press | Home screen menu |
| Home button or gesture (while on the home screen) | Do nothing |
| Pinch in, pinch out | Do nothing |
| Two-finger swipe up, two-finger swipe down | Do nothing |

Tapping the search bar always opens search.

### Available actions

| Group | Actions |
| --- | --- |
| Launcher pages | Search / app drawer, one of up to four **widget pages** (each lists the widgets it holds, or "Empty"), Web Apps Panel, Home screen menu, Feed |
| System actions | Notifications, Quick settings, Turn off screen, Power menu, Recent apps |
| Apps and shortcuts | Launch an app or a shortcut that you choose |
| Plugin actions | Actions offered by a [gesture plugin](./plugins-integrations#gesture-and-widget-plugins) |
| Nothing | Do nothing |

- **Turn off screen**, **Power menu** and **Recent apps** need the launcher's **accessibility
  service**. Settings shows a lock banner on the gesture until you enable it. Turn off screen also needs
  Android 9 or newer.
- **Feed** is only offered in debug and nightly builds, see [Widgets and feed](./widgets-feed#feed).
- If a gesture cannot run, a dialog explains why instead of failing silently.

::: tip Home screen menu
The default long press opens a small menu with **Change wallpaper** and **Add widget**, as on most
launchers. Add widget only appears when the home screen shows widgets (see "Widgets on home screen").
:::

## Search bar

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Style | Settings > Home screen > Search bar | Transparent | Transparent, Solid or Hidden |
| Color | Same | Auto | Light, dark or automatic icon and text color |
| Different colors in drawer | Same | Off | A separate color for the bar while the drawer is open |
| Search bar position | Same | Bottom | Top or bottom |
| Fixed search bar | Same | Off | Do not scroll the bar out of view |
| Remember scroll position | Same | Off | Keep the drawer scroll position when it is reopened |

Behavior of the search itself (keyboard, launch on enter, filters) is described in [Search](./search).

## Clock

The clock is always the first element. Its settings open from the clock itself.

| Setting | Default | What it does |
| --- | --- | --- |
| Layout | Default | Default (large clock, dynamic zone below) or Compact (one row) |
| Style | Bold | Bold, Simple, Orbit, Binary, Hands (analog, optional ticks), 7-segment, No clock, Custom widget |
| Show seconds | Off | Seconds on the digital styles |
| Monospaced digits | Off | Fixed-width numbers |
| Use theme color | Off | Color the clock with the theme instead of the wallpaper |
| Fill screen height | Off | Let the clock fill the screen; alignment top, center or bottom |
| Time format | System | 12-hour or 24-hour (Settings > Language and region) |

**Dynamic zone** (one component at a time): Date (on), Weather (off), Media controls (on, only while a
session is active), Alarms (on, shown when an alarm rings within 8 hours), Battery (show when low or
charging, always, or off). With the Smartspacer integration the zone is handed to Smartspacer, see
[Plugins and integrations](./plugins-integrations#smartspacer). The existing
[Clock](../../widgets/clock) page describes the original behavior.

## Dock

Settings > Home screen > Dock.

| Setting | Default | What it does |
| --- | --- | --- |
| Dock | Seeded with your default apps on first start | Shown when enabled or when custom pages exist; icons are always round |
| Enable custom dock | n/a | Place apps and widgets in slots yourself instead of showing pinned favorites |
| Dock rows and columns | 1 x 5 | Size of one dock page. The column count comes from the **Dock columns** slider |
| Multiple docks | Off | Up to several docks; swipe on the dock to switch. New docks start empty |
| Override dock grid | Off | Own icon size for the dock. It does not change the number of columns |
| Clear a slot | n/a | Tap a filled dock slot to open the slot dialog and choose **Remove from dock** |
| Dock background | Off | A plate behind the dock with color, opacity and shadow |
| Page indicator | On | Dots when there is more than one dock |
| Shutters | On | Swipe up on a dock app to launch the app assigned to it |

**Shutters** let one icon hide two actions: a tap launches the app, a swipe up launches another app,
activity or shortcut that you pick (asked on first use, or from the app info menu).

## Wallpaper

Settings > Appearance > Wallpaper (also the home screen menu entry **Change wallpaper**).

| Setting | Default | What it does |
| --- | --- | --- |
| Change wallpaper | n/a | Pick a photo or a video. For photos you choose home screen, lock screen or both |
| System wallpaper picker | n/a | Open the Android wallpaper chooser |
| Dim wallpaper | Off | Darken the wallpaper in dark themes |
| Blur wallpaper | On | Blur behind the drawer and other full pages, using the blur radius slider (default 32). Not on every device |

**Video wallpaper** <Badge type="warning" text="experimental" />: add one or more videos to a playlist and
activate it. It runs as a live wallpaper service.

| Video setting | Default |
| --- | --- |
| Scaling mode | Fill (also Fit, Stretch) |
| Zoom, horizontal and vertical position | 1.0, centered |
| Brightness, playback speed | 1.0 |
| Start behavior | Resume (also Restart, Random frame) |
| Parallax scrolling and strength | Off, 0.2 |
| Use video colors for the theme | On |
| Pause in Battery Saver | On |
| Pause when the device overheats | On |
| Pause in desktop mode | Off |

A video wallpaper pauses on the current frame instead of stopping, so it costs little while paused.
Pausing on battery saver and thermal throttling are on by default for that reason. The wallpaper
cannot render on an external display, see [Desktop and overlays](./desktop-and-overlays#desktop-mode).

## Other home screen options

| Setting | Default | What it does |
| --- | --- | --- |
| Lock desktop | Off | Prevent moving or removing items on the home screen |
| Fixed screen rotation | Off | Keep the launcher in portrait |
| Charging animation | On | A bubble animation while charging |
| Hide status bar, hide navigation bar | Off | Immersive home screen |
| Status and navigation bar icons | Auto | Light or dark icons |
| Edit button (widgets) | On | A button to add, remove and rearrange widgets |
| Create folder | n/a | Group two or more apps into a folder in the dock, in one step |
| Folder covers | On | The first icon fades like a lid when a folder opens |

Folders are also created from [tags](./favorites-tags#folders).

## Limitations

- One home page only (see the warning above).
- Gesture actions that need the accessibility service cannot run before you enable it.
- Video wallpapers are experimental and device dependent.
