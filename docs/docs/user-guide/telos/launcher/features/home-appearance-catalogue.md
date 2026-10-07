# Home screen and appearance: complete feature catalogue

This page lists every home screen and appearance setting of the Telos launcher that exists in the
code, with where to find it, its default value and what it does. It is a reference, not a tutorial. For
walkthroughs see [Home screen](../home-screen), [Customization](../customization) and
[Widgets and feed](../widgets-feed).

Every row was checked against the settings screens and the settings data class. Things that are stored
but have no screen, or that are switched off in this build, are marked and collected in
[Known gaps](#known-gaps).

::: tip How to read the tables
"Where" is the path in the launcher settings, opened from the search bar menu. Defaults are fresh-install values.
:::

## Where things live in Settings

| Settings entry | What is inside |
| --- | --- |
| Appearance | Wallpaper, theme (light, dark, system, time), presets, color scheme, typography, shapes, transparency, text size, theme import and export |
| Home screen | Desktop mode, dock, lock desktop, fixed rotation, clock, widgets on home screen, edit button, search bar, charging animation, system bars |
| Grid and icons | Grid, home and search overrides, drawer background, folders, icon shape, themed icons, icon pack, badges |
| Web app shortcuts | Web Apps Panel, groups, browsing options, shortcut list |
| Gestures | One action for each of 11 gestures |
| Dynamic Island | Pill over other apps |
| Integrations | Weather, media control, plus the other integrations |
| Language and region | Language, form of address, transliteration, time format, units, calendars, currencies |
| Advanced settings | Performance, context profiles, plugins and more |

## Home screen layout

The home screen is a column: the **clock**, with an optional **dock** at its bottom, optional
**widgets** below, and the **search bar**. Everything else is a gesture away.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Widgets on home screen | Home screen > Widgets | Off | Puts the widgets of widget page 1 under the clock. With it off, only the clock is on the home screen |
| Edit button | Home screen > Widgets | On | Shows **Edit widgets** at the bottom of a widget list. It is hidden anyway while **Lock desktop** is on |
| Reset widgets tutorial | Home screen > Widgets | n/a | Shows the first-run widget tutorial again |
| Lock desktop | Home screen | Off | Prevents moving and removing items on the home screen and hides the edit button |
| Fixed screen rotation | Home screen | Off | Locks the launcher to portrait. Off follows the sensor |
| Desktop mode | Home screen | see [Desktop and overlays](../desktop-and-overlays#desktop-mode) | A desktop shell for an external display |
| Number of home screens | not shown | 1 | <Badge type="danger" text="disabled" /> See below |

::: warning Extra home screens are disabled
The code has a slider for 1 to 9 swipeable home pages and a stored page count. A compile-time constant
(`EXTRA_HOME_SCREENS_ENABLED = false` in `HomeScreenPager.kt`) switches the feature off, so the slider
is hidden and the stored value is ignored. There is exactly one home page. The warning text in the
slider says the extra pages are independent widget areas, but you cannot reach them in this build.
:::

### Long press and the search bar menu

| Menu | How to open | Entries |
| --- | --- | --- |
| Home screen menu | The default long press gesture | **Change wallpaper** (opens the wallpaper settings), **Add widget** (only when the visible page shows widgets) |
| Search bar menu | The three-dot button in the search bar while it is empty. With text in the bar the same button clears it | **Wallpaper** (Android wallpaper chooser), **Settings**, **Add widget**, **Help** |

## Clock

The clock is always the first element and cannot be removed, but its style can be **No clock**. Long
press the clock to open its settings sheet (also reachable from Home screen > Widgets > Clock, and from
the clock row in widget edit mode). A tap on the clock opens the system alarm list (the clock app).

### Layout and style

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Layout | Clock sheet | Default (vertical) | **Default**: big clock with the dynamic zone below. **Compact**: one row, clock and dynamic zone side by side |
| Style | Clock sheet, swipeable style picker | Bold | One of 8 styles, see below |
| Colors | Clock sheet, three-way toggle | Auto | **Auto** picks dark or light content from the wallpaper brightness, **Dark** and **Light** force it |
| Use theme color | Clock sheet | Off | Color the clock from the color scheme instead of white or black |
| Show seconds | Clock sheet | Off | Seconds. Only offered in the default layout and not for the custom widget style |
| Monospaced digits | Clock sheet | Off | Fixed width digits. Only offered for Bold, Simple and Orbit |
| Fill screen height | Clock sheet | Off | Lets the clock block fill the free screen height. Needs **Widgets on home screen**. Without it the clock always fills the height |
| Alignment | Clock sheet | Bottom | Top, Center or Bottom inside the free height |
| Time format | Language and region | System | System, 12-hour or 24-hour. The same setting also applies elsewhere |

### The eight clock styles

| Style | Name in the picker | Variant option | Notes |
| --- | --- | --- | --- |
| Digital 1 | Bold | **Outlined** (default off) | Large bold digits, outline instead of fill when the variant is on |
| Digital 2 | Simple | none | Plain digital clock |
| Orbit | Orbit | none | Digits around an orbit |
| Analog | Hands | **Ticks** (default off) | Analog dial, optional minute ticks |
| Segment | 7-segment | none | Seven segment display |
| Binary | Binary | none | Binary clock |
| Empty | No clock | none | Hides the clock. The dynamic zone and dock still show. Tapping does nothing |
| Custom | Custom widget | **Pick widget**, **Resize**, **Configure** | Any Android app widget as the clock. Stored size defaults to 200 dp high and the widget width |

The style picker remembers a separate variant for Bold and Hands, so switching styles does not reset them.

### Dynamic zone

The dynamic zone is the line or row under the clock. It shows **one** component at a time, the one with
the highest current relevance, so with several parts on you still see only one.

| Part | Where | Default | Effect |
| --- | --- | --- | --- |
| Date | Clock sheet > Dynamic zone | On | The current date. Uses the primary (and optional secondary) calendar system |
| Weather | Clock sheet > Dynamic zone | Off | Current weather of the selected weather provider. Asks for the needed permission when switched on |
| Media | Clock sheet > Dynamic zone | On | Media controls while a media session is active |
| Alarms | Clock sheet > Dynamic zone | On | Alarms that ring within the next 8 hours |
| Battery | Clock sheet > Dynamic zone | Show when low or charging | Choices: Off, Show when low or charging, Always show |
| Managed by Smartspacer | Clock sheet > Dynamic zone | Off | If the Smartspacer integration is on (Android 10 or newer), the zone shows Smartspacer targets and the parts above are hidden. A **Turn off** button returns to the normal parts |

## Dock

The dock is a row (or grid) of apps and widgets inside the clock block, above the search bar. Settings >
Home screen > Dock.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Dock on first start | automatic | seeded once | On the first start Telos fills a dock with the device's default apps (dialer, messages, browser and so on) and never does it again, even if you empty the dock |
| Enable custom dock | Dock > Pages | n/a | Switches from automatic favorites to a dock whose slots you fill. Tap a slot to pick **App** or **Widget** |
| Rows | Dock | 1 | 1 to 4 rows per dock page |
| Columns | Dock | 5 | 1 to 10 columns per dock page |
| Multiple docks | Dock > Multiple docks | Off | Up to 5 docks, switched by swiping on the dock. Extra docks start empty |
| Default dock | Dock | First | Radio button under a dock page, shown with more than one dock |
| Override dock grid | Dock > Grid | Off | Own column count (3 to 12) and icon size (32 to 64 dp) for the dock |
| Dock background | Dock | Off | A plate behind the dock. Color (theme if unset), opacity 0 to 100 percent (default 30), shadow 0 to 16 |
| Page indicator | Dock | On | Dots when there is more than one dock. Own color option |
| Shutters | Dock > Shutters | On | Swipe up on an icon to launch the app assigned to it (a second app, activity or shortcut) |

Dock icons are always drawn as circles, whatever the icon shape setting says. A dock without custom
pages shows your most used favorites in pages instead (this "automatic" dock is also what layout
presets switch on).

## Search bar

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Style | Home screen > Search bar | Transparent | **Transparent**, **Solid** or **Hidden**. The picker shows a live preview of each |
| Colors | Same picker, for Transparent only | Auto | Auto, Light or Dark content over the wallpaper |
| Different colors in the drawer | Home screen > Search bar | Off | A separate Auto, Light or Dark choice while the search view is open |
| Search bar position | Home screen > Search bar | Bottom | Top or bottom of the screen |
| Fixed search bar | Home screen > Search bar | Off | Keeps the bar in place instead of scrolling it away with the content |
| Remember scroll position | Home screen > Search bar | Off | The search view keeps its scroll position when reopened |

Keyboard on open, launch on enter, result order and filters are search settings, see [Search](../search).

## Wallpaper

Reached from the first entry of **Settings > Appearance**, from the home screen menu or from the search
bar menu. (Older docs say Home screen > Wallpaper; the entry moved to Appearance.)

| Setting | Default | Effect |
| --- | --- | --- |
| Change wallpaper | n/a | Pick a photo or a video. For photos a dialog asks: home screen, lock screen or both |
| System wallpaper picker | n/a | Opens the Android chooser |
| Dim wallpaper | Off | Darkens the wallpaper while a dark theme is active |
| Blur wallpaper | On | Blurs the wallpaper behind the search view. Needs Android 12 or newer, else the switch is disabled |
| Blur radius | 32 | 4 to 64 in steps of 4, shown while blur is on |

### Video wallpaper <Badge type="warning" text="experimental" />

The video section appears once a video wallpaper exists. Videos form a playlist; **Clear playlist**
empties it.

| Setting | Default | Range or choices |
| --- | --- | --- |
| Scaling mode | Fill | Fit (letterbox), Fill (crop), Stretch |
| Brightness | 1.0 | 0 to 2 |
| Zoom | 1.0 | 0.5 to 5 |
| Horizontal and vertical position | 0 and 0 | -1 to 1 |
| Playback speed | 1.0 | 0.25 to 3 |
| Start behavior | Resume | Resume, Restart, Random frame |
| Parallax and strength | Off, 0.2 | Strength 0.1 to 1, shown with parallax on |
| Use video colors for the theme | On | Stored setting for theme colors from the video |
| Pause in Battery Saver | On | Freezes on the current frame |
| Pause on thermal throttling | On | Freezes on the current frame |
| Pause in desktop mode | Off | The phone wallpaper cannot render on the external display |

## Gestures

Settings > Gestures. Eleven gestures each get one action. Only the swipe, double tap and long press
gestures can be overridden by a [context profile](../desktop-and-overlays#context-profiles).

| Gesture | Default action |
| --- | --- |
| Swipe down | Search / app drawer |
| Swipe up | Widgets (page 1) |
| Swipe left | Do nothing |
| Swipe right | Web Apps Panel |
| Double tap | Turn off screen |
| Long press | Home screen menu |
| Home button (pressed on the home screen) | Do nothing |
| Pinch in, pinch out | Do nothing |
| Two-finger swipe up, two-finger swipe down | Do nothing |

Tapping the search bar always opens search. Opening animations depend on gesture and target:
**rubberband** for a swipe down to search, **zoom in** for taps, double tap and long press, and
**push** for the other directional swipes and pinches.

### Every action

| Action | Group | Needs | Notes |
| --- | --- | --- | --- |
| Do nothing | Top of the list | n/a | |
| Search / app drawer | Launcher | n/a | |
| Widgets, widget pages 1 to 4 | Launcher | n/a | Each entry shows what the page holds or "Empty". Page 1 is not offered as a target when widgets are on the home screen |
| Feed | Launcher | Debug or nightly build | <Badge type="warning" text="debug and nightly only" /> Hidden by a feature flag in release builds |
| Web Apps Panel | System group | n/a | The assignment itself turns the panel on and picks its direction |
| Home screen menu | System group | n/a | Change wallpaper and add widget |
| Notifications | System | none | Opens the notification shade |
| Quick settings | System | none | |
| Turn off screen | System | Accessibility service, Android 9 or newer | Missing on older Android |
| Power menu | System | Accessibility service | |
| Recent apps | System | Accessibility service | |
| App or shortcut | Apps | n/a | Pick an app, shortcut or any searchable item. Shortcut suggestions appear in the list |
| Settings | Apps | n/a | Opens the launcher settings |
| Plugin actions | Plugins | A gesture plugin | Actions offered by [gesture plugins](../plugins-integrations#gesture-and-widget-plugins), listed per plugin |

Only **Turn off screen**, **Recent apps** and **Power menu** need the accessibility service. A gesture
row with one of these shows a lock banner until the service is enabled. If an action cannot run, a sheet
explains why.

## Widgets

Widget pages and where they live are described in [Widgets and feed](../widgets-feed#where-widgets-live).
Four widget pages exist (page 1 is the home screen when **Widgets on home screen** is on). The widget
list of a page is edited with **Edit widgets**: add, drag to reorder, resize by height, stack, replace,
remove.

### Built-in widget types

The picker offers these built-in types, and lists plugin widgets and Android app widgets after them.

| Widget | Needs | Options in the widget settings | Stored but without a screen |
| --- | --- | --- | --- |
| At a Glance | Calendar permission for events | none | none |
| Weather | Weather provider | **Compact mode** (hides the forecast), link to weather integration settings | none |
| Media (Music) | Notification access | **Interactive progress bar** (default off, seeks when the player allows it), link to media integration settings | none |
| Calendar | Calendar permission | **Hide completed tasks**, per-calendar choice | All-day events (stored default on), 3 upcoming events, 3 upcoming tasks |
| Reminders | Tasks app | **Hide completed tasks** | Maximum items (stored default 5) |
| Note | none | **Link to file** (sync with a text file) | none |
| Battery | none | none | none |
| Network | none | none | none |
| System (RAM and storage) | none | none | none |
| Freeze | Freeze set up | none | none |
| Plugin widgets | A widget plugin | none | Label |
| Android widgets | The app providing it | **Borderless**, **Background card**, **Use theme color**, **Configure widget**, **Replace** | Width |
| Favorites (Apps) | n/a | n/a | <Badge type="danger" text="not offered" /> Removed from the picker, older ones keep working |

Every widget also has a stored height. Widgets can be **stacked**: **+** adds a widget to the same slot,
swipe sideways to switch, **Remove from stack** splits it. The widget picker opened from a dock slot only lists widgets that fit a 1x1 slot.

::: tip Favorites widget settings
An existing Favorites widget still stores custom tags, an edit button, multiline tags, compact tags,
a tag list and "skip rows" (0 to 8). They are edited in its configure sheet. Nobody can add a new one.
:::

## Themes and colors

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Theme | Appearance | System | System, Light, Dark or **Time of day** |
| Dark theme starts, light theme starts | Appearance, with Time of day | 20:00 and 07:00 | Hour sliders 0 to 23. Clock hours, not sunrise |
| Text size | Appearance | 100 percent | 80 to 200 percent in 5 percent steps, a launcher-only multiplier with a live preview |
| Source for dynamic colors | Appearance > Advanced (Android 12+) | System | System colors, or colors from the wallpaper (compat mode) |
| Presets | Appearance > Presets | n/a | See below |
| Color scheme | Appearance | Default | List of schemes. Pick, create, duplicate, edit, delete |
| Typography | Appearance | Default | List of typographies |
| Shapes | Appearance | Default | List of shape schemes |
| Transparency | Appearance | Default | List of transparency schemes |
| Import theme | Appearance | n/a | Pick a `.kvtheme` file. A preview shows what it contains and whether it replaces an existing one |
| Export theme | Appearance | n/a | Choose parts, add a name and author, then share or save the file |

Opening a theme file from another app (MIME type `application/vnd.de.mm20.launcher2.theme`) starts the
import dialog directly.

### Built-in schemes and what you can edit

| Area | Built-in | What a custom one controls |
| --- | --- | --- |
| Color scheme | Default (Material You on Android 12+), High contrast, Black and white | A core palette of six colors (Primary, Secondary, Tertiary, Neutral, Neutral variant, Error) and separate light and dark color roles, each from the palette, a custom color or generated from the primary color. See [Color schemes](../../../customization/color-schemes) |
| Typography | Default, System, Monospace, Serif, Rounded | A "brand" and a "plain" font, plus a style (family, weight, size) per text role and an emphasized variant. Families: launcher default, device headline or body, sans serif, serif, monospace, or a named system font |
| Shapes | Default, Extra round, Cut, Rectangular | A base shape (rounded or cut corners, radii) and optional overrides for extra small up to extra extra large, including the "increased" sizes |
| Transparency | Default, Semi-transparent | Opacity of Background, Surface and Elevated surface |

### Presets

Settings > Appearance > Presets. A tap installs the preset immediately, with no confirmation and no undo.
Each preset is a bundle of optional colors and an optional layout.

| Preset | Colors | Layout (grid, icon size, labels, shape, dock, search bar, animation speed) |
| --- | --- | --- |
| AMOLED Black | yes | none |
| Cyberpunk | yes | none |
| Colorful Grid | yes | 5 columns, 48 dp, labels, Pebble, dock, solid fixed search bar at the bottom, speed 1.15 |
| Classic Grid | none | 4 columns, 52 dp, labels, Rounded square, dock, solid fixed bar at the bottom, speed 1.0 |
| Clean Grid | none | 4 columns, 56 dp, no labels, Squircle, dock, transparent fixed bar at the bottom, speed 1.1 |
| Pixel-style | none | 4 columns, 48 dp, labels, Circle, dock, solid fixed bar at the **top**, speed 1.0 |

A layout preset only writes the fields it sets: grid columns, icon size, label visibility, icon shape,
dock on, dock rows, search bar position, style and fixed state, reduce animations and animation speed.
A bundle can also carry shapes, transparency and typography; none of the shipped presets do.

### Blur, transparency and surfaces

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Wallpaper blur and radius | Appearance > Wallpaper | On, 32 | See [Wallpaper](#wallpaper) |
| Transparency scheme | Appearance > Transparency | Default | Opacity of the three surface layers |
| Dock background opacity and shadow | Dock | 30 percent, 0 | A plate behind the dock |
| Drawer background | Grid and icons > Search drawer grid | Off | A plate behind the search view. Color and opacity (default 90 percent) |
| Folder background color | Grid and icons > Folders | Theme | Folder popups |

## Grid, labels and folders

Settings > Grid and icons.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Icon size | Grid | 48 dp | 32 to 64 dp in steps of 8 |
| Number of columns | Grid | 5 (from a device resource) | 3 to 12 |
| Show labels | Grid | On | Names under icons |
| Label size | Grid | 12 | 8 to 24 |
| Label max lines | Grid | 1 | 1 or 2 |
| Label shadow | Grid | Off | A shadow for readability |
| Label color | Grid | Auto | A fixed color |
| Show apps in a list | Grid | Off | A list instead of a grid |
| Show icons in the list | Grid | On | Icons in the list rows |
| Override home grid | Grid > Home screen grid | Off | Own columns and icon size for the home screen |
| Override search grid | Grid > Search drawer grid | Off | Own columns and icon size for the search view |
| Create folder | Grid > Folders | n/a | Starts the folder creation sheet |
| Folder covers | Grid > Folders | On | The first icon fades like a lid while a folder opens |

## Icons

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Icon shape | Grid and icons > Icons | System default | System default, Circle, Square, Rounded square, Squircle, Reuleaux triangle, Hexagon, Pentagon, Teardrop, Pebble. A hidden eleventh shape is not listed |
| Enforce shape | Icons | Off | Also reshapes legacy icons that are not adaptive |
| Themed icons | Icons | Off | Colors icons from the color scheme, where an app ships a themed icon |
| Enforce themed icons | Icons | Off | Themes all icons. Only enabled while Themed icons is on. Not recommended |
| Icon pack | Icons | System | Pick one installed icon pack. The row is disabled when no pack is installed. Packs that support theming are marked |
| Icon cache size | Advanced settings > Performance | 200 | 50 to 500 in steps of 50 |

The icon service understands two kinds of installed packs (the appfilter format and the grayscale map
format), calendar and clock icons that update live, dynamic themed calendar icons, folder icons,
custom text icons and placeholder icons. Per-item customization (rename, other icon, icon from a pack,
themed or text icon, shutter, visibility) is on [Customization](../customization#per-item-customization).
See [Themed icons](../../../customization/themed-icons) and the
[icon pack developer guide](../../../../developer-guide/integrations/icon-packs).

### Badges

Settings > Grid and icons > Badges.

| Badge | Default | Notes |
| --- | --- | --- |
| Notification badges | On | Needs notification access. Style **Dot** (default) or **Count** (number of active, non-summary notifications) |
| Badge color | Theme tertiary | One color for notification badges. Reset to follow the theme |
| Cloud badges | On | A file lives in the cloud |
| Suspended apps | On | App is suspended or frozen |
| Shortcut badges | On | The app a shortcut belongs to |
| Plugin badges | On | The plugin that produced a result |
| Work profile badge | always on | No setting. Marks work profile apps |
| Hidden item badge | always on | No setting. Marks hidden items where they are shown |
| App update badge | always on | No setting. Marks installed apps that have a pending update in the Telos Store |

Frozen apps can show a snowflake badge or turn gray (a style choice in the Freeze settings), see
[Freeze](../../freeze/).

## Animations and motion

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Charging animation | Home screen > Animations | On | Bubbles rising from the navigation bar while charging. The intensity grows with the charging current |
| Reduce animations | Advanced settings > Performance | Off | Instant transitions |
| Animation speed | Performance | 1.0 | 0.5 to 2.0 in steps of 0.25. Hidden while Reduce animations is on |
| Bounce physics | Performance | no bounce (100 percent damping) | Slider 0 to 100 in steps of 10. Lower values overshoot after a gesture |
| Search debounce | Performance | Off (0 ms) | 0 to 500 ms in steps of 50 before search runs |

The scaffold animates page changes with the three styles listed under [Gestures](#gestures). The speed
setting scales fade and effect motion, not the spring physics.

## System bars

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Status bar icons | Home screen > System bars | Auto | Auto, Light or Dark icons |
| Navigation bar icons | Home screen > System bars | Auto | Auto, Light or Dark icons |
| Hide status bar | Home screen > System bars | Off | Immersive home screen |
| Hide navigation bar | Home screen > System bars | Off | Hides the bar. Gesture navigation still works |

## Weather, media and web apps

### Weather providers

Settings > Integrations > Weather. Weather feeds the clock, the weather widget and At a Glance.

| Provider | Availability |
| --- | --- |
| Open-Meteo | Always. This is the default |
| Bright Sky (German weather service) | Always |
| OpenWeatherMap | Only in builds that include an API key |
| MET Norway | Only in builds that include a contact string |
| Breezy Weather | When the Breezy Weather app is installed. Location is managed there |
| Weather plugins | When the plugin is enabled. A plugin can manage the location itself |

| Setting | Default | Effect |
| --- | --- | --- |
| Provider | Open-Meteo | Which provider is used |
| Automatic location | On | Uses the device location. Asks for permission |
| Location | none | Pick a place when automatic location is off. Hidden for managed providers |
| Measurement system | System | System, Metric, UK or US. Same setting as Language and region |

Setup details: [Plugins and integrations](../plugins-integrations#weather).

### Media control

Settings > Integrations > Media control. The media widget, the clock's media part and the Dynamic Island
use the same player list.

| Setting | Default | Effect |
| --- | --- | --- |
| Media apps | Installed music apps | A checkbox per app. Add apps that are not detected as music apps, or remove apps. Needs notification access |
| Controls | automatic | Previous, play and pause, next, a progress bar and the player's own custom actions. Seek, stop, fast forward and rewind appear only if the player supports them |
| Open player | n/a | Tapping the track opens the player, or a chooser when none is running |

### Dynamic Island <Badge type="warning" text="experimental" />

Settings > Dynamic Island. A pill over other apps for the most relevant live item.

| Setting | Default | Effect |
| --- | --- | --- |
| Enable Dynamic Island | Off | Needs the permission to display over other apps |
| Show active calls | On | Needs the phone state permission, only used to know whether a call is active |

Priority when several things are live: call (100), timer (80), media (50), charging (20). No code
starts the timer slot. More in [Desktop and overlays](../desktop-and-overlays#dynamic-island).

### Web apps and the Web Apps Panel

Settings > Web app shortcuts. The panel is a home screen page for web app shortcuts.

| Setting | Default | Effect |
| --- | --- | --- |
| Enable Web Apps Panel | On (through the swipe right gesture) | Switching on assigns the panel to a swipe gesture, switching off clears it |
| Swipe direction | Right | Left or right. Moves the gesture assignment |
| Enable web app groups | Off | Lets you create named groups of shortcuts. Each group can switch notifications on or off |
| Block ads and trackers | On | Embedded browser only |
| Pinch to zoom | On | Embedded browser |
| Strip tracking parameters | On | Embedded browser |
| Top bar position | Top | Top or bottom |
| Swipe to switch web apps | On | Swipe sideways on the top bar to switch shortcuts |
| Panel items | empty | Per shortcut: show it in the app grid, in the panel, or both |

Shortcut options (name, URL, icon source, browser, custom CSS, notifications) are in
[Desktop and overlays](../desktop-and-overlays#web-apps).

## Language, region and units

Settings > Language and region.

| Setting | Default | Effect |
| --- | --- | --- |
| Language | System default | Opens the Android per-app language screen. Android 13 or newer, disabled below that. About 45 translation folders ship, including Greek and German |
| Form of address | not set | Only shown while the language is French or Spanish. Neutral, feminine or masculine |
| Transliteration | Automatic | Which transliterator normalizes text for search. Needs Android 10 and more than two choices; also can be turned off |
| Time format | System | System, 12-hour or 24-hour |
| Measurement system | System | System, Metric, UK or US |
| Calendar system | System | Primary and optional secondary calendar, shown in the clock date |
| Currencies | System order | Currencies listed first in the unit converter |

## Accessibility and comfort

There is no single accessibility page. These settings serve that purpose.

| Setting | Where | Effect |
| --- | --- | --- |
| Text size | Appearance | 80 to 200 percent on top of the system font size |
| High contrast color scheme | Appearance > Color scheme | Built-in scheme |
| Black and white color scheme | Appearance > Color scheme | Built-in scheme |
| Reduce animations | Performance | Instant transitions |
| Label size, label lines, label color | Grid and icons | Larger or more readable labels |
| Accessibility service | Gestures (banner) | Lets the screen off, recents and power menu gestures work. Also used by App Lock |
| Fixed rotation | Home screen | Prevents rotating while the phone lies at an angle |

## Known gaps

::: warning Stored in the settings file but not reachable from the UI
- **Extra home screens** (1 to 9 pages): disabled by a build constant, see above.
- **Fallback icon packs**: the icon service and the settings model support a list of fallback packs and a
  setter exists, but no screen calls it. Only the single icon pack is usable.
- **Dock background blur**: a stored value (default 0) with no control and no code that applies it.
- **Calendar widget extras**: all-day events switch and the counts of upcoming events and tasks.
- **Reminders widget**: maximum items.
- **Legacy surface settings**: surface opacity, border width, radius and shape were replaced by the
  transparency and shape schemes.
:::

::: tip Differences from older pages
The Home screen page lists the dock as **Off** by default and the wallpaper under Home screen. In the
code the dock is seeded with default apps on first start and the wallpaper entry now sits in
Appearance. This page follows the code.
:::
