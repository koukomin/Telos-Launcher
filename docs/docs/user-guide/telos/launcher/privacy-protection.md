# Privacy and protection

This page covers what Telos does to keep your data private (locks, hidden items, no network by default),
and how it keeps its own built-in apps from taking the launcher down (crash guard, containment, idle
behavior). The last section describes backup and restore. The developer view of the protection layers is
in [Protection and optimization](../../../developer-guide/project-structure/protection-and-performance).

::: warning Locks are convenience, not a vault
App Lock and the launcher lock stop casual access: a friend picking up your unlocked phone. They are not a
replacement for device encryption and a screen lock. Anyone with ADB access, or who boots the phone
another way, is not stopped by them.
:::

## Overview

| Layer | What it protects | Where |
| --- | --- | --- |
| [Settings lock](#settings-lock) | The hidden items and excluded folders settings pages | Settings > Search > Protection |
| [Launcher lock](#launcher-lock) | The home screen | Settings > Search > Protection |
| [App Lock](#app-lock) | Chosen apps and web apps | Settings > Advanced > App Lock |
| [Hidden items](#hidden-items) | Items you never want in grid or search | Long-press > Customize |
| [Online results off](#network-privacy) | Your queries | The Online results filter |
| [Crash guard](#crash-guard) | The launcher, from bugs in Telos apps | Automatic |
| [Backup](#backup-and-restore) | Your setup | Settings > Advanced > Backup and restore |

## Settings lock

**Lock sensitive settings** asks for authentication before the pages **Excluded search results** and
**Excluded folders** open. Without it, anyone who opens the settings can see what you hid.

| Setting | Default | What it does |
| --- | --- | --- |
| Lock sensitive settings | Off | Require authentication to open those two pages |
| Authentication method | Device lock | **Device lock** (biometrics, PIN or pattern) or **Biometrics only** |
| Use separate lock | Off | Use a custom PIN instead of the device credential |
| Set custom PIN | n/a | At least 4 digits, entered twice. Stored as a salted SHA-256 hash, which is light protection |

Turning the lock off again needs the same authentication, so a stranger cannot simply disable it. If no
screen lock or biometric is set up on the device, the protected pages say so and stay locked until you
set one up in Android.

## Launcher lock

**Lock launcher** requires authentication to view the home screen after you leave it. It uses the device
credential only, not the separate PIN.

::: warning If you get locked out
The launcher lock cannot be turned off from the locked home screen. Open Android Settings > Apps > Telos
and use the gear icon to open the launcher's own settings, then switch the lock off.
:::

## App Lock

<Badge type="warning" text="experimental" />

App Lock puts a gate in front of chosen apps. Every time a locked app comes to the foreground, Telos asks
you to authenticate first.

### Set up

1. Settings > Advanced > **App Lock**, switch on **Enable App Lock**.
2. Grant what the page asks for. **Display over other apps** is needed to cover the app, and **Usage
   access** to notice when a locked app opens.
3. Choose the **Unlock method** (device lock or biometrics only).
4. Under **Locked apps**, tick the apps. Web apps have their own **Web apps** list.
5. Optionally set **Auto-lock timing** and the detection method below.

| Setting | Default | What it does |
| --- | --- | --- |
| Detection method | Both | Usage access only, accessibility service only, or both (recommended) |
| Accessibility service | Off | Optional. Catches locked apps the instant they open instead of a moment later |
| Auto-lock timing | Immediately | How long after leaving a locked app you can return without authenticating: immediately, 30 seconds, 1 minute, 5 minutes or 30 minutes. Each app can override it |
| Stay unlocked until screen off | Off | Apps stay unlocked as long as the screen is on |
| Lock work profile toggle | Off | Authentication before pausing or resuming the work profile from the drawer |

When the screen turns off, all unlocked apps are locked again.

::: tip How detection works
Without the accessibility service Telos polls the foreground app about every 400 ms, so a locked app can be
visible for a split second before the gate covers it. With the service, the window change is reported
immediately. "Both" uses the service when it is on and falls back to polling otherwise.
:::

A foreground notification "App Lock is active" ("Watching for locked apps") shows while it runs. It
starts again whenever the launcher process restarts, if the overlay permission is still granted.

### Intruder photo

<Badge type="warning" text="opt-in" />

| Setting | Default | What it does |
| --- | --- | --- |
| Capture intruder photo | Off | A silent front-camera photo on a failed attempt. Needs the camera permission |
| Auto-delete after | 90 days | 7 days, 30 days, 90 days, 1 year or 2 years (the maximum, 730 days) |
| Storage location | App-private | A folder you choose instead of the default |
| Show in gallery | Off | Only meaningful for a folder outside app storage. Internal storage is never visible to the gallery |
| Notify on failed unlock | Off | A notification on every failed attempt, even without a photo, that opens the photo list |

Photos stay on the device and are never uploaded. **Captured photos** shows them. A cleanup job removes
old ones.

## Hidden items

Any app or result can be taken out of the grid or search without uninstalling it. Long-press, tap
**Customize**, and pick **Show in**:

| Choice | Effect |
| --- | --- |
| App grid and search results | Normal |
| Search results | Not in the grid, found by searching |
| Never | Not shown anywhere unless you reveal hidden results |

Settings > Search > **Excluded search results** lists them. **Show reveal button** adds a button to the
search view to reveal excluded results. Lock the page with [Settings lock](#settings-lock).

Other privacy switches: **Private keyboard** (Settings > Search) asks the keyboard not to learn from what
you type in search and web app fields, and the [web apps](./desktop-and-overlays#web-apps) browser strips
tracking parameters.

## Network privacy

- Online sources (Wikipedia, websites, places, cloud files, many plugins) run only when the **Online
  results** filter is on. It is off by default, see [Search](./search#online-results).
- Calendar, contacts, apps, local files, the calculator and unit converter never use the network.
  Currency rates download a small public file from the European Central Bank in the background.
- Weather needs a network and, for automatic location, the location permission.
- Usage data (launch counts, weights) stays in the launcher's database. The build has no analytics or
  telemetry library.
- The crash reporter lists crashes locally, see [Crash reporter](../../troubleshooting/crashreporter).

## Crash guard

Telos Radio, Music, Video and Photos are *virtual apps*. They run inside the launcher process, so a bug in
one could take the launcher down. The crash guard handles that.

| Behavior | Detail |
| --- | --- |
| What is watched | Radio, Music, Video and Photos. Phone and Messages are not guarded because they are default-role apps that must stay reachable |
| How a crash is counted | A marker is written when the app opens and removed when you leave. An uncaught exception while the marker is set counts against that app |
| Native crashes and hangs | Found on the next start through Android's exit reasons (Android 11 and newer) |
| Limit | Two crashes within 24 hours switch the app off, with a notification pointing to the [Store](../store) |
| Reset | Two minutes of use without a crash, or installing the app again from the Store |

### Exception containment

An uncaught exception in a background coroutine normally ends the whole process. Telos runs the background
work of its apps (SIP, radio service, network automation, store updates, call recording, call screening,
auto redial, SMS receiver, install receiver, network tile) in a contained scope that logs the failure and
keeps going.

### Separate video process

<Badge type="warning" text="experimental" />

Settings > Video services > **Play in a separate process** runs the video player in its own process. A crash
in a decoder or the torrent engine then ends that process, not the launcher. Videos opened from other apps
always use the normal player. It is off by default. SIP and the photo editor still run in the launcher
process.

## Idle behavior

Apps that are on cost nothing while idle:

- Player services (Radio, Music) start only when something plays and stop when playback ends.
- Heavy parts (video decoders, the torrent engine, the SIP stack) are created on first use, not at launcher
  start. A torrent is stopped and its cache deleted when the player closes.
- The Store checks updates through one periodic WorkManager job.
- Switched-off apps have their manifest components disabled, so they use no memory or CPU and do not
  appear in "Open with".
- [Smart Freeze](../freeze) idle timeouts (5, 15 or 30 minutes depending on profile) are separate from
  this and apply to other apps you opt in.

More in [Performance](./performance).

## Backup and restore

Settings > Advanced > **Backup and restore**: **Backup** writes a file you choose, **Restore** reads one.

| Part | Included |
| --- | --- |
| Launcher settings | Yes, including App Lock lists and the custom PIN hash |
| Favorites, usage weights, visibility, hidden items | Yes |
| Custom names, custom icons, tags | Yes |
| Themes (colors, shapes, typography, transparency) | Yes |
| Widgets | Yes |
| Quick actions | Yes |
| Cloud logins (Nextcloud, ownCloud) | No |
| Intruder photos | No |
| Passwords and API keys of the Telos apps | Removed from the file |
| Hidden numbers and protected call numbers of Telos Phone | Removed from the file |

The backup is a plain ZIP, **not encrypted**. Keep it somewhere private. The file records the app version,
the device model and the time, and the restore screen shows them. Compatibility is checked by format
version (currently 1.9): a different major version cannot be restored, a different minor version restores
with a warning that some data may be lost.

On restore, secrets that live on the device (passwords, keys, hidden numbers) are kept from the device,
not taken from the file, and an account without its password is switched off.

## Limitations

- App Lock reacts after an app is already open, with a short delay when only polling is used.
- Locks are not a security boundary against someone who controls the device.
- Backups are not encrypted and do not carry cloud logins.
- The video process split is experimental, and SIP does not have one yet.
