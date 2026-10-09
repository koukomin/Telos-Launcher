# Desktop mode and overlays

Besides the home screen, Telos can draw things in other places: on a connected monitor (desktop mode),
over other apps (floating launcher, Dynamic Island), inside a built-in browser (web apps), and
automatically by situation (context profiles). This page covers all of them.

::: warning Mostly untested on real hardware
Much of this code carries a "pending review" marker in the source, and the source comments say the
overlay behavior "needs verifying on real hardware". Treat these features as
<Badge type="warning" text="experimental" /> and report problems. Each section says what is solid and what is not.
:::

## Overview

| Feature | Where | Needs | Default |
| --- | --- | --- | --- |
| [Desktop mode](#desktop-mode) | A connected external display | A device that can launch apps on a second display | On, switches itself on at the first display |
| [Floating launcher](#floating-launcher) | Over every app | Display over other apps | Off |
| [Dynamic Island](#dynamic-island) | Top of the screen | Display over other apps | Off |
| [Web apps](#web-apps) | Home screen and a panel | Nothing | Panel on swipe right |
| [Assistant mode](#assistant-mode) | The system assistant gesture | Telos set as assistant | Off until you set it |
| [Context profiles](#context-profiles) | Behind the scenes | Depends on the trigger | Off |

The floating launcher, the Dynamic Island and App Lock run as foreground services. They only run while
switched on, and Telos starts them again whenever the launcher process restarts, if the
display-over-other-apps permission is still granted.

## Desktop mode

Desktop mode shows a desktop-style shell on an external display, while the phone keeps its normal home
screen. Settings > Home screen > **Desktop mode**.

| Setting | Default | What it does |
| --- | --- | --- |
| Enable desktop mode | On after first connect | Show the shell whenever an external display is connected |
| External display status | n/a | "Connected" or "Not connected" |
| Orientation | Automatic | Automatic, portrait or landscape |
| Icon size | 48 | Independent of the phone icon size, because monitors are bigger |
| Desktop wallpaper | Solid color | Solid color or a static image you choose |
| Enable floating windows (via Shizuku) | Off | See below |

**How it switches on.** The first time you connect a display, Telos turns desktop mode on by itself and
remembers it. If you turn it off, it stays off. When the display goes away, the shell goes away. If the
device does not support launching on a second display, the page says "Not supported on this device" and
the options are disabled.

### What the shell contains

- **Workspace.** A grid of app icons on the wallpaper. Long-press the empty area for **Change
  wallpaper**, **Desktop settings** and **Add shortcut**.
- **Taskbar.** A start button, a dot under apps that are running, and a system tray.
- **Start menu.** Lists your apps ("No apps found" if none match).
- **System tray.** Volume, network, battery and notifications (with the count), plus a clock.

Telos does not draw other apps' windows itself. It launches them on the external display and the Android
window manager shows them, fullscreen or freeform.

### Window management

Long-press a running app in the taskbar to open its menu: **Snap Left**, **Snap Right**, **Maximize** and
**Close Window** <Badge type="warning" text="untested" />. Snapping needs windows that can be resized,
which means freeform mode.

::: details Quarter snapping
The snap calculator also knows the four quarters, but the taskbar menu only offers left half, right half
and maximize. Quarter positions are not reachable from the UI in this build.
:::

### Floating windows (freeform)

<Badge type="warning" text="experimental" />

**Enable floating windows (via Shizuku)** switches the whole device into Android's freeform windowing
mode. It needs [Shizuku](https://github.com/RikkaApps/Shizuku) running and granted. The page shows
"Waiting for Shizuku permission" and then "Freeform windowing is active system-wide".

::: warning This changes every app
Freeform mode affects how every app on the device is windowed, not only the launcher, for as long as it
stays on. Turn the toggle off again to restore normal fullscreen windows.
:::

The video wallpaper cannot render on the external display, which is why desktop mode has its own
wallpaper. The phone screen keeps its video wallpaper unless you switch on **Pause video in desktop
mode**, see [Home screen](./home-screen#wallpaper).

## Floating launcher (Smart Sidebar)

<Badge type="warning" text="experimental" />

A thin handle on one screen edge, even while you use other apps. Tap it, or drag it toward the middle of the screen,
to open a card with your tools, apps and widgets next to it. The layout is modeled on the Smart Sidebar of OxygenOS,
the colors, shapes and fonts follow the Telos theme. It is off by default.

1. Open Settings > **Floating launcher**. Allow **Display over other apps** when asked.
2. Switch on **Smart sidebar**.
3. Choose the **side** and the **position** of the handle, then open **Edit the sidebar** to put things into the card.

### The card

| Part | What it does |
| --- | --- |
| **File Dock** (top button) | The text, pictures and files you dragged onto the handle or the card, held until you drop them into another app. The shelf is not saved: it is cleared when the service restarts. Can be switched off |
| Tiles | Your tools, apps and widgets in the order you chose, in one or two columns. Tap an app to open it, tap a tool to use it |
| Widgets | Shown full width. The card gets wider when it has one |
| **All** | The list of every tool |
| **Edit** | Opens the editor |

Tapping outside the card closes it. The card opens next to the handle it came from.

### Tools

| Tool | What it does |
| --- | --- |
| Screenshot, Partial screenshot, Scrolling screenshot | The three modes of [Telos Screenshot](../screenshot/). The card closes first. They need the Telos accessibility service (Settings > Gestures) |
| Screen recorder | Starts a recording of [Telos Screen Recorder](../screen-recorder/) (Android asks for the capture permission). Runs it again to stop it |
| Voice recorder | Starts or stops a recording of [Telos Voice Recorder](../voice-recorder/). The microphone permission must have been given in the app once |
| Recent files | The 30 newest files on the phone with their date, tap one to open it. It reads the system's media index, so it needs the access to files that Telos Files asks for |
| Flashlight | Turns the flashlight on or off |
| Quick settings, Notifications | Pull down the quick settings or the notifications |
| Lock screen | Locks the screen (accessibility service, Android 9+) |
| Power menu | Opens the power menu (accessibility service) |

### The editor

**Edit** opens the editor, which dims the screen. The left side lists what can be added: **Tools**, **Widgets** and
**Apps** (every installed app, including the Telos apps), with a search field at the top. Tap an item with a plus to
add it. The right side shows the sidebar as it is: tap the minus on an item to remove it, drag an item to move it.
**Done** closes the editor, and the gear opens the settings. The window of the editor takes the keyboard focus (it has
a search field), which the card itself never does.

**Widgets** are the widgets of the launcher: weather, calendar, music, notes, battery, at a glance, reminders, network,
system, freeze and favorites. A widget that you add here lives only in the sidebar, not on your home screen. Widgets
use the same settings as on the home screen, but cannot be configured from the sidebar yet, and widgets of other
apps (Android app widgets) cannot be added.

### Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Smart sidebar | Off | Starts and stops the overlay |
| Edit the sidebar | | Opens the editor over the current screen |
| Side of the screen | Right | Left or right edge |
| Position of the handle | 50 percent | Height on the screen |
| Size of the handle | 72 | Height of the handle |
| Tab thickness | 24 | Width of the touch area around the handle (the visible handle is a thin pill) |
| Tab color and transparency | Gray, 80 percent | Look of the handle |
| Hide tabs | Off | Make the handle invisible but still tappable |
| Two columns | On | Two tiles per row (one column when off) |
| Show app names | On | Names under the icons |
| Panel transparency | 85 percent | How see-through the card is |
| Icon size | 48 | Size of the icons |
| Rows before scrolling | 10 | Card height before it scrolls |
| File Dock | On | The temporary storage button |
| Open apps in floating windows | On | Tapped apps open as a window on top of the app in front, see below |
| Haptic feedback | On | A short vibration on tap |
| Hide during Gaming profile | Off | Hide the handle while a context profile with the Gaming icon is active |

**Floating windows.** With *Open apps in floating windows* on, a tapped app opens as a window on top of the app in
front. This only works while Android's freeform mode is on (see [Floating windows (freeform)](#floating-windows-freeform));
without it Android ignores the window size and the app opens normally.

**What is not in it.** The Smart Sidebar of OxygenOS also has **AI Summary** and **AI Speak** (OnePlus's own system
services), a partial **screen recording** and a **Private Tab** shortcut. Telos has none of these. The handle can be on the left or the right edge at
any height, and there is one sidebar, not one per screen zone as in earlier builds.

::: warning Known risk and the emergency switch
An early version of this overlay blocked touch on the whole screen. It was fixed by using a small window for the handle
and a full screen window only while the card is open. If a bug ever locks the screen, disable the floating launcher from
a computer with this command, which needs no touch input:

`adb shell am broadcast -a de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER -p <application id>`

Drag and drop between windows, widgets inside the overlay and the editor have not been verified on a real device.
:::

The card deliberately has no search field. A text field would have to steal focus from the app in front. The editor has
one, so its window can take the focus while it is open.

## Dynamic Island

<Badge type="warning" text="experimental" />

A small pill near the top of the screen that shows the most relevant live thing. Settings > Dynamic Island.

| Setting | Default | What it does |
| --- | --- | --- |
| Enable Dynamic Island | Off | Needs **Display over other apps** |
| Show active calls | On | Needs the **phone state** permission, used only to know whether a call is active |

The pill shows one source at a time, by priority: **call** (100), **timer** (80), **media** (50),
**charging** (20). It only appears while something is live. The pill can expand to show media controls for
playback or a cancel button for a timer.

::: warning The timer slot is empty
The pill has a timer slot with a countdown, but nothing in this build starts that timer. Quick actions
like "Start timer" use the system clock app. Expect calls, media and charging only.
:::

## Web apps

Web apps turn a website into an app-like shortcut. Settings > Web app shortcuts.

1. Tap **Add web app shortcut**.
2. Enter a **Name** and **URL**.
3. Choose an **Icon source**: website icon (auto-detected), a system icon, or a custom photo or icon-pack
   icon.
4. Choose **Open with**: the **embedded** (in-app) browser, or an installed browser that supports Custom
   Tabs.
5. Optional: **Custom CSS** (embedded only), **Enable notifications**, **Ad blocker** (embedded only:
   use the global setting, always on, or always off for this web app), a folder, and whether it shows in
   the grid and in the panel. Save.

### Block lists

Besides the built-in host list, Settings > Web app shortcuts > **Block lists** can load more domain lists. Nothing is
downloaded until you switch a list on, and the built-in list always stays active.

| Offered list | Licence |
| --- | --- |
| StevenBlack unified hosts | MIT |
| AdGuard DNS filter | GPL-3.0 |
| Peter Lowe's ad and tracking server list | free to use and redistribute (see its site) |
| OISD small | GPL-3.0 |
| EasyList (domain rules only) | GPL-3.0 / CC BY-SA 3.0 |
| URLhaus malware domains | abuse.ch terms of use |

- Each enabled list is fetched directly from its own address (shown under the list). No other server is contacted.
- Supported formats: hosts files (`0.0.0.0 domain`), plain domain lists, wildcard lists (`*.domain`) and, from adblock
  lists, only simple domain rules (`||domain^`, optionally with `$third-party`). Cosmetic filters, path rules and
  exceptions are ignored, so this is not a full EasyList engine.
- A blocked domain also blocks its subdomains. The lists apply to the embedded browser only and follow the global
  switch and each web app's own ad blocker setting.
- **Add list by address** (https only) and **Import from a file** add your own lists; custom lists can be removed.
- **Automatic update:** Off, Daily or Weekly (default weekly once a list is on), by default only on Wi-Fi and not when
  the battery is low; **Update now** ignores the Wi-Fi setting. Unchanged lists are not downloaded again (ETag /
  Last-Modified). If an update fails the previous list stays in use and the error is shown. Limit: 160 MB of text per list.
- Switching a downloaded list off deletes its data.

Web apps appear in the app grid and in search, can be locked with [App Lock](./privacy-protection#app-lock)
and can carry per-item customization.

| Browsing setting | Default | Applies to |
| --- | --- | --- |
| Block ads and trackers | On | Embedded browser only. A built-in host blocklist plus the block lists you switch on (domain rules only), not a full filter-list engine. Each web app can override it (global, on, off) |
| Pinch to zoom, zoom controls | On | Embedded browser. A change applies to open web apps immediately |
| Strip tracking parameters from links | On | Embedded browser (utm_, fbclid, gclid and similar) |
| Top bar position | Top | Navigation bar at the top or bottom |
| Swipe to switch web apps | On | Swipe the top bar to move between your web apps |

**Deleting.** Tap the bin next to a web app in Settings > Web app shortcuts, or choose **Delete** in the
long-press card of a web app, and confirm. The web app disappears from search, the grid, the panel and
its folder. Deleting a folder (with confirmation) keeps the web apps inside and leaves them ungrouped.
Folders can also be renamed.

**Suggested categories.** Settings > Web app shortcuts > **Suggested categories** offers ready-made
folders: Social, Email, Messaging, Video & Music and Productivity & Work. They are only suggestions:
nothing is added until you tap **Add** on a category and choose which websites to include. The chosen
websites are saved as normal web apps (their icon is looked up from the website, with a category icon
as fallback) in a folder named after the category, and turning the folder feature on if it was off. A website
you already added with the same address is reused, not duplicated. Afterwards the folder and every web app
can be edited or deleted like anything else. You sign in on the website itself. The embedded browser keeps
the logins, so this only applies to web apps that open in the embedded browser; a web app opened in an
external browser uses that browser's own sign-in.

**Web Apps Panel.** A page that holds the web apps you choose. It is reached by the gesture that has the
**Web Apps Panel** action (swipe right by default). The settings page, with its **Web Apps Panel** checkbox, lets you
pick the swipe direction (left or right) and the web apps in it, with drag to reorder.

## Assistant mode

Telos registers an assistant activity. If you set Telos as the **digital assistant** in Android, the
assistant gesture or button opens Telos in a separate assistant window instead of the home screen.
Android controls that setting, not Telos.

## Context profiles

<Badge type="warning" text="experimental" />

A context profile is a named bundle of overrides that switches on automatically (or by hand). Settings >
Advanced > Context profiles. Turn on **Enable context profiles** first.

| Trigger | Notes |
| --- | --- |
| Manual only | Use **Active now** on the profile |
| Time of day | Editable start and end time |
| WiFi network | By network name (SSID). Reading the name needs the location permission |
| Bluetooth device | By exact device name. Needs the Bluetooth permission |
| Battery Saver is on | While Battery Saver is active |
| Charging | Any charger, USB, power adapter or wireless |

| A profile can change | Details |
| --- | --- |
| Gestures | Swipe down, left, right, up, double tap and long press |
| Freeze profile | Battery Saver, Balanced, Aggressive or Ultra Aggressive, see [Smart Freeze](../freeze/) |
| Home widgets | Which widget page opens |
| Do Not Disturb | Turn on or off. Needs notification policy access |
| Brightness | A percentage. Needs the modify system settings permission |
| Launch app | Starts an app once when the profile becomes active |

Icons for profiles: Home, Work, Car, Gaming, Battery saver, Sleep and Custom.

::: warning Triggers are checked only while the launcher is open
There is no background service watching for triggers. Telos checks when the launcher resumes and then
about once a minute while it is visible. A profile can lag behind the real situation, and Bluetooth and
time triggers only take effect while the launcher process is alive.
:::

## Limitations

- Desktop mode needs hardware support for a second display.
- Freeform windows change the whole device and need Shizuku.
- Overlays depend on the display-over-other-apps permission, which some manufacturers restrict.
- The Dynamic Island timer is not fed by anything yet.
- Context profile triggers do not run in the background.
