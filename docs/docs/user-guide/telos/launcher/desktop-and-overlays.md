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

## Floating launcher

<Badge type="warning" text="experimental" />

A thin tab on the screen edge, even while you use other apps. Drag it toward the middle of the screen to
open a panel of your favorite apps. It is off by default.

1. Open the Floating launcher settings (see the warning below, there is no entry for it in the main
   settings list). Grant **Display over other apps** when asked.
2. Switch on **Enable floating launcher**.
3. Under **Trigger zones**, turn on the edge zones you want. There are six: left or right, each in the
   top, middle or bottom third. Only **Right, top** is on at first.
4. Open a zone and pick its apps. Each zone has its own list.
5. Use **Preview** to see the result.

| Setting | Default | What it does |
| --- | --- | --- |
| Tab thickness | 24 | Width of the tab |
| Tab color and transparency | Purple, 60 percent | Look of the tab |
| Two columns | On | Two apps per row in the panel |
| Rows before scrolling | 10 | Panel height before it scrolls |
| Hide tabs | Off | Make tabs invisible but still tappable |
| Haptic feedback | On | A short vibration on tap |
| Hide during Gaming profile | Off | Hide tabs while a context profile with the Gaming icon is active |

**File dock.** Dragging text, images or files from another app onto a tab holds them temporarily, so you
can drop them into another app. Media is copied into the launcher's cache when dropped. The shelf is not
saved: it is cleared whenever the service restarts. Dragging an app icon from the home screen onto a tab
adds the app to that zone.

::: warning No settings entry in this build
The Floating launcher settings page exists, but the main settings list does not link to it. The only
link is the gear icon inside the floating panel, which needs the launcher to be on already. To reach the
page the first time, start the settings activity with the route extra, for example:

`adb shell am start -n <application id>/de.mm20.launcher2.ui.settings.SettingsActivity --es de.mm20.launcher2.settings.ROUTE settings/floatinglauncher`

The application id is `com.dimitris.telos`, plus `.debug`, `.release` or `.nightly` depending on the
build. This looks like an oversight and is listed under limitations.
:::

::: warning Known risk and the emergency switch
An early version of this overlay blocked touch on the whole screen. It was fixed by using one small
window per tab. If a future bug ever locks the screen, disable the floating launcher from a computer with
this command, which needs no touch input:

`adb shell am broadcast -a de.mm20.launcher2.action.DISABLE_FLOATING_LAUNCHER -p <application id>`

Drag and drop between windows is described in the source as not specifically tested.
:::

The floating launcher deliberately has no search field. A text field would have to steal focus from the
app in front.

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
5. Optional: **Custom CSS** (embedded only), **Enable notifications**, and whether it shows in the grid
   and in the panel. Save.

Web apps appear in the app grid and in search, can be locked with [App Lock](./privacy-protection#app-lock)
and can carry per-item customization.

| Browsing setting | Default | Applies to |
| --- | --- | --- |
| Block ads and trackers | On | Embedded browser only. A built-in host blocklist, not a full filter-list engine |
| Pinch to zoom | On | Embedded browser |
| Strip tracking parameters from links | On | Embedded browser (utm_, fbclid, gclid and similar) |
| Top bar position | Top | Navigation bar at the top or bottom |
| Swipe to switch web apps | On | Swipe the top bar to move between your web apps |

**Web Apps Panel.** A page that holds the web apps you choose. It is reached by the gesture that has the
**Web Apps Panel** action (swipe right by default). The settings page **Enable Web Apps Panel** lets you
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
| Time of day | From and to |
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
- The Floating launcher settings page is not linked from the settings list.
- The Dynamic Island timer is not fed by anything yet.
- Context profile triggers do not run in the background.
