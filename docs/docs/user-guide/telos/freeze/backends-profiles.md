# Backends, methods, profiles and exclusions

How Smart Freeze reaches the system, how to set up each backend, the two freeze methods, the automatic profiles and the
rules that protect running apps. Every setting is in a table with where to find it and its default. For the screens
see the [overview](./).

## Backends

Smart Freeze talks to the system through **one backend**. With the default setting (**System default**) it takes the
first one that is available, in this order.

| # | Backend | What it needs | Suspend / disable | Force stop, clear cache |
| --- | --- | --- | --- | --- |
| 1 | Shizuku | The [Shizuku](https://github.com/RikkaApps/Shizuku) app running and permission granted | yes | yes |
| 2 | Dhizuku <Badge type="warning" text="experimental" /> | The [Dhizuku](https://github.com/iamr0s/Dhizuku) app and permission granted | yes (current user) | no |
| 3 | Root | A rooted device | yes | yes |
| 4 | Device owner | Telos set as device owner with `adb` (guided setup screen) | yes | no |
| 5 | Island | The Island app, only for apps already in the Island profile | request only | no |

::: warning Dhizuku is experimental
In the backend picker (**Settings > Advanced > Freeze Manager > Backend > Freeze backend**) you can choose **System
default**, **Shizuku**, **Dhizuku**, **Root**, **Island** or **Device owner** by hand. On System default, Dhizuku is used
only when no Shizuku is available. Read the [Dhizuku note](#dhizuku) first.
:::

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Freeze backend | Freeze Manager > Backend | System default | Which backend is used. If the chosen one is not available there is no backend and freezing does nothing |
| Backend state | Freeze Manager > Backend | | Shows the active backend and "Permission granted" or "Tap to grant permission". With none: "None" |
| Device owner setup | Freeze Manager > Backend | | Opens the guided setup screen |

### How each backend works

| Backend | Suspend | Disable | Force stop and cache | Notes |
| --- | --- | --- | --- | --- |
| Shizuku | Calls the package manager's suspend function directly, as the shell or root user | Sets the app's enabled state to disabled (disabled-by-user when Shizuku runs through adb) | Calls the activity manager, and clears the app cache | Works for other users (work profile) |
| Dhizuku | The device policy manager, through Dhizuku | Hides the app (a hidden app cannot run) | Not possible, always reports failure | Current user only. <Badge type="info" text="untested" /> on a device |
| Root | `pm suspend` | `pm disable-user` | `am force-stop`, and removes the cache folder only | Works for other users |
| Device owner | The device policy manager | Hides the app (a hidden app cannot run) | Not possible, always reports failure | Current user only |
| Island | An intent to Island | An intent to Island | Not possible | Only for apps already inside Island |

Root availability is checked once per process; if you grant root later, restart the launcher so Telos asks again.

### Setting up Shizuku

1. Install Shizuku and start it (with wireless debugging, or with root).
2. Open Freeze Manager. The backend state says "Tap to grant permission". Tap it and allow the request.
3. The state turns to "Permission granted".

### Dhizuku

<Badge type="warning" text="experimental" /> The Dhizuku backend is implemented but **untested on a device**. It lets
Telos use the device policy manager through the [Dhizuku](https://github.com/iamr0s/Dhizuku) app, which must be
running as device owner, and needs the permission granted in Dhizuku. Telos suspends and hides (disables) apps for the
current user, and only reports an app as frozen if Dhizuku confirms the change. Force stop and clear cache are not
possible. It relies on internal Android classes, so it may not work on every Android version; then freezing reports
failure and nothing is changed. System default uses Dhizuku only when no Shizuku is available.

### Setting up root

Allow Telos in your root manager when asked. The backend works as soon as the superuser grant is given.

### Device owner setup

::: warning Be careful
Device owner gives Telos deep control of the phone and is hard to undo.
:::

1. Open **Freeze Manager > Backend > Device owner setup**.
2. Read the risks and requirements, and tick "I understand the risks and requirements above".
3. Remove Google accounts, any work profile and other device owners first. Android only allows device owner on a
   device without them (accounts can be added back afterwards).
4. Connect the phone to a computer with `adb`, enable USB debugging, and run the shown command, with the **Copy** button
   for convenience:
   `adb shell dpm set-device-owner` followed by Telos' admin component.
5. Tap **Check status**. It should say "Device owner is active".
6. Tap **Use as freeze backend**.

To give up device owner later, run the removal command shown on the same screen (`adb shell dpm remove-active-admin`
and the component). On some Android versions a factory reset is the fallback. As device owner Telos can suspend and
hide apps but **not** force stop them or clear their cache; use Shizuku or root for that.

### Island

Telos sends an Island freeze intent, which works only for apps already in the Island profile. Island's request is
activity based, so Telos cannot confirm that the app really ended up frozen and Android blocks it from the background.
For automatic freezing Telos shows a notification "Tap to freeze apps" that you must tap.

## Freeze methods

| Method | Effect | Which backends |
| --- | --- | --- |
| **Suspend** (default) | The app is suspended: it cannot run or start, its icon is grayed out by Android and notifications are blocked | Every backend |
| **Disable** | The app is disabled (hidden, for device owner): it disappears from the launcher and cannot run | Shizuku, root, device owner, Dhizuku |

The method is chosen **per app** in the app list ("Auto-freeze (Suspend)" or "Auto-freeze (Disable)"). Island always
uses its own freeze.

## What it can do (settings in the Apps section)

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Show system apps | Freeze Manager > Apps | off | Lists system apps. Freezing them can break the phone, a warning explains it |
| Show apps without an icon | Freeze Manager > Apps | off | Also lists background components and services |
| Advanced Features | Freeze Manager > Apps | off | Adds Freeze, Force stop and Clear cache to context menus (needs Shizuku or root) |
| Hide frozen apps | Freeze Manager > Apps | off | Removes frozen apps from the home screen, drawer and search. They stay manageable here |
| Frozen App Style | Freeze Manager > Apps | Snowflake Badge | Grayscale or Snowflake Badge for frozen apps that stay visible |
| Move frozen apps to the end | Launcher settings (drawer) | off | Puts frozen apps at the bottom of the app drawer |

**Protected packages** (`android`, System UI, Settings, keyguard, the telecom server, Google Play services, the Play
Store and Telos) are never frozen, force stopped or cleared, whatever you choose.

## Automatic freezing

Only apps you opt in (the "candidates") are frozen automatically. Choose a profile.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Enable auto-freeze | Freeze Manager > Auto-freeze | off | Master switch |
| Profile | Freeze Manager > Auto-freeze | Balanced | Battery Saver, Balanced, Aggressive, Ultra Aggressive or Custom |
| Usage access | Freeze Manager > Auto-freeze | not granted | Needed to know the foreground app |

| Profile | Idle timeout | On screen off | On battery saver | Media session check |
| --- | --- | --- | --- | --- |
| Battery Saver | 30 min after screen off | no | yes | strict |
| Balanced | 15 min | no | yes | strict |
| Aggressive | 5 min | yes, immediately | yes | strict |
| Ultra Aggressive | 1 min | yes, immediately | yes | relaxed |
| Custom | your values | your choice | your choice | your choice |

Profile summaries in settings: Battery Saver "freezes 30 minutes after the screen turns off, or when battery saver turns
on", Balanced 15 minutes, Aggressive "immediately when the screen turns off", Ultra Aggressive the same "and also freezes
apps with a paused media session".

### Custom profile values

Shown only when Custom is selected.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Freeze immediately when screen turns off | Auto-freeze | off | Freezes at screen off |
| Freeze some time after screen turns off | Auto-freeze | off | Freezes after the idle time |
| Minutes after screen off | Auto-freeze | 15 | The idle time |
| Freeze when battery saver is on | Auto-freeze | off | Freezes when Battery Saver starts |
| Exclusion strictness | Auto-freeze | Strict | **Strict** or **Relaxed**. Only affects the active media session check |

The music, network and network threshold options apply to every profile; only the exclusion strictness is Custom-only. In the app info screen of an app, the **Freeze** action uses the selected backend and switches between **Freeze** and **Unfreeze**.

The idle timeout counts from the moment the screen turns off. Turning the screen on again cancels it. A
[context profile](../launcher/desktop-and-overlays#context-profiles) can override the freeze profile while it is active, without changing
your own choice.

### Exclusion rules

Before every automatic freeze these apps are skipped:

| Rule | Always on? |
| --- | --- |
| Apps you marked **Never freeze** | yes, wins over everything |
| The app in the foreground (needs Usage access) | yes |
| Apps with an ongoing notification or a foreground service | yes |
| Android Auto / car mode is active | yes |
| Apps with an active media session | only in Strict mode |
| **Exclude apps playing music** | optional switch (all auto-freeze profiles, shown whenever auto-freeze is on), on by default. Skips when the phone is playing audio |
| **Exclude apps with network activity** | optional switch (all auto-freeze profiles), on by default. Skips when the app moves more than the threshold |
| **Network threshold** | (all auto-freeze profiles) 100 KB per second by default. Measured over half a second |

The network check samples the app's traffic for half a second and compares it to the threshold. A device without
traffic statistics skips this rule.

### "N apps ready to freeze"

A notification "Tap to freeze now, without waiting for screen off" lists how many candidates are not frozen yet, so you
can freeze on demand. With Island as backend Android does not allow a silent freeze from the background, so the
notification must be tapped. The **Freeze now** widget does the same.

## Statistics

Every freeze and unfreeze is counted per app, with the last time of each. They show in
[the dashboard](./#dashboard-history).

## Limitations

- Which backend works depends on the device and Android version. Root and device owner are
  <Badge type="info" text="untested" /> on many devices, and Dhizuku is experimental.
- Island cannot be automated from the background.
- Device owner cannot force stop or clear caches, and acts on the current user only.
- Freezing system apps can break core phone functions.
- The foreground-app check needs Usage access.
- Disabled apps vanish from the launcher until unfrozen.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "No freeze backend available" | Install and set up Shizuku, or use a rooted device |
| Freezing does nothing but no error shows | Dhizuku may be the active backend. See the Dhizuku note above |
| Shizuku permission cannot be granted | Restart Shizuku and open Freeze Manager again |
| Root says unavailable after granting | Restart the launcher so the root check runs again |
| The device owner command fails | Remove all accounts and profiles from the phone, or use the factory-reset fallback |
| Auto-freeze never runs | Enable it, pick candidates, and check the profile triggers |
| An app is never frozen automatically | An exclusion rule applies (notification, music, network, foreground) or it is set to Never freeze |
| Frozen app still appears | **Hide frozen apps** is off. Switch it on, or pick the Grayscale style |
| Force stop and clear cache are missing | Switch on **Advanced Features** and use Shizuku or root |
