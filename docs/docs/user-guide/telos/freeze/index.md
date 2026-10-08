# Smart Freeze

Freeze or hide apps you do not need right now. A frozen app cannot run in the background until you unfreeze it, and it
is launched again (and unfrozen) the next time you open it from the launcher.

::: info This section
- This page: where to find it, the screens, what you can do, work profile and Sandbox, widget and feature matrix.
- [Backends and profiles](./backends-profiles): the five backends and their setup, freeze methods, auto-freeze profiles,
  exclusion rules and the settings tables.
:::

::: info Where
Smart Freeze is a feature of the launcher settings, not a separate Telos app, so it does not appear in
[Telos Store](../store/). You find it in two places:

- **Settings > Advanced > Freeze Manager**: the backend, auto-freeze options and the app list.
- **Settings > Smart Freeze Dashboard**: backend status, counters, filters and a quick toggle for each app.

There is also a **Freeze** home screen widget with a "Freeze now" button, and Freeze / Unfreeze entries in the context
menu of an app.
:::

## What it is

Smart Freeze talks to the system through one **backend** (Shizuku, root, device owner or Island, and a Dhizuku
backend that is not finished) to
**suspend** or **disable** apps. It can freeze apps by hand, or automatically when the screen is off, the phone idles
or Battery Saver turns on, while it protects apps that you are using.

## Requirements and permissions

| Needed for | Requirement | Notes |
| --- | --- | --- |
| Any freezing | One working backend | See [Backends](./backends-profiles#backends) |
| Not freezing the app in use | **Usage access** (`PACKAGE_USAGE_STATS`) | Settings shows a hint until granted. Without it an app could be frozen while you use it |
| The "ready to freeze" notification and Island notification | Notifications | Android 13 and later |
| Work Mode switch | "Manage profiles" access (`INTERACT_ACROSS_PROFILES`) | See [Work profile](#work-profile-android-work-mode) |
| Force stop and clear cache | Shizuku or root | Device owner, Dhizuku and Island cannot do these |

::: tip Grant usage access
Without Usage access Telos cannot know which app is in the foreground, so an app could be frozen while you use it.
Settings shows a hint until it is granted.
:::

## A tour of the screens

### Freeze Manager (Settings > Advanced)

| Section | Contents |
| --- | --- |
| Dashboard | A row "Currently frozen apps and freeze history" that opens the history screen |
| Backend | The backend picker, the active backend, its permission state, and the device owner setup |
| Auto-freeze | Enable switch, Usage access, Profile, triggers, exclusion strictness and the optional exclusions |
| Apps | Switches for system apps, apps without an icon, advanced features, hiding frozen apps and the frozen style, then every app with its freeze state |

### Dashboard (history)

- **Currently frozen**: each frozen app with a "Frozen" badge, or "Nothing is frozen right now".
- **History**: apps that were frozen or unfrozen, with counters ("Frozen N times, unfrozen N times"), "Last frozen" and
  "Last unfrozen" times, or "No apps have been frozen or unfrozen yet".
- **App status**: every app, whether it is Normal, Suspended, Disabled or Hidden, how long it ran today ("1h 5m today",
  "Under 1m", "Not used today") and "Running now". It needs Usage access and has a **Refresh** action.

### Smart Freeze Dashboard

| Element | What it does |
| --- | --- |
| Backend Status | "Connected via (backend)" or "Disconnected", with **Grant Permission** when the permission is missing |
| Counters | Frozen Apps, Auto-Freeze List, Last Execution ("Monitoring") |
| Search field "Search Apps" | Filters the list |
| Filter chips | All, User, System, Frozen |
| App rows | Name, "Frozen" or "Active", an **Auto-Freeze** switch that adds or removes the app as a candidate |
| **Toggle Freeze (N)** button | Freezes or unfreezes the selected apps |

## What it can do

- Freeze or unfreeze single apps, or several at once, from the app list and the context menu.
- **Protected packages**: the Android system (`android`), System UI, Settings, the keyguard, the telecom server, Google
  Play services, the Play Store and Telos itself are never frozen, force stopped or cleared.
- **Advanced features** switch (needs Shizuku or root) adds **Force stop** and **Clear cache** to context menus.
- Show or hide system apps and apps without an icon in the list. Hide frozen apps entirely, or show them grayscale or
  with a snowflake badge.
- A frozen app is **unfrozen automatically** when you launch it from the launcher; the launcher waits a moment so the
  system sees the app is enabled again.
- Frozen apps can be driven by a [context profile](../launcher/desktop-and-overlays#context-profiles), which can override the freeze profile.

### Per-app states

Each app in the Freeze Manager list has a state, chosen from its menu:

| State | Meaning |
| --- | --- |
| Not managed | Never frozen automatically. You can still freeze it by hand |
| Auto-freeze (Suspend) | A candidate, frozen by suspending it. The default method |
| Auto-freeze (Disable) | A candidate, frozen by disabling it. Only for Shizuku, root and device owner |
| Never freeze | Never frozen by the automatic triggers. Wins over every other rule |

## Work profile (Android work mode)

Under **Settings > Advanced > Work profile**:

| Feature | What it does |
| --- | --- |
| Status | Not set up, Active or Paused |
| Set up / manage | Opens the system user settings. Telos cannot create a work profile itself. Look under System > Multiple users |
| **Work Mode** switch | Pauses or resumes the work apps (Android quiet mode). Needs the "manage profiles" permission |
| Lock work profile toggle | Asks for authentication before pausing or resuming from the app drawer |
| **Initialize Telos Sandbox** | Starts Android's managed profile setup so an isolated work profile can hold cloned apps. Shown only while no work profile exists |

Shizuku and root apply freezing to the user (profile) of the target app, so apps in a work profile can be targeted.
Dhizuku and device owner act on the current user only.

::: warning Experimental
"Clone to Sandbox" in an app's menu copies the app's APK into the work profile through a small bridge service of Telos.
It is new and <Badge type="warning" text="experimental" /> and <Badge type="info" text="untested" /> across devices.
Whether it works depends on your device and Android version, and it needs a work profile.
:::

## The Freeze widget

A home screen widget with a **Freeze now** button. It shows how many apps are selected ("N apps selected") and the result
("Frozen N of M"). With no candidates it says "No apps selected. Choose apps to freeze in settings."

## Feature matrix

| Feature | Backends | Status |
| --- | --- | --- |
| Suspend an app | Shizuku, Dhizuku, root, device owner, Island (request only) | stable |
| Disable an app | Shizuku, Dhizuku, root, device owner | stable |
| Force stop, clear cache | Shizuku, root | stable |
| Auto-freeze on screen off, idle, Battery Saver | all | stable |
| Profiles and exclusion rules | all | stable |
| "Ready to freeze" notification | all | stable |
| Freeze history and app status | all | stable |
| Frozen apps hidden, grayscale or badged | all | stable |
| Work Mode and Sandbox | needs a work profile | <Badge type="warning" text="experimental" /> |
| Root, device owner | | <Badge type="info" text="untested" /> on many devices |
| Dhizuku | | <Badge type="warning" text="experimental" />: implemented, untested on a device |

## Limitations

- With Island Telos can only send the request. It cannot confirm that the app really ended up frozen, and Android blocks
  it when no screen is visible.
- What works depends on the device and Android version. Root and device owner are
  <Badge type="info" text="untested" /> on many devices. The Dhizuku backend is experimental and untested on a device,
  see [Backends](./backends-profiles#dhizuku).
- Freezing system apps can cause problems. Disabling or force stopping one can break core phone functions and, in the
  worst case, leave the device unable to boot.
- Frozen apps do not run in the background, so they do not deliver notifications until unfrozen.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "No freeze backend available. Install and set up Shizuku, or use a rooted device." | Start Shizuku and grant it, or set up another backend |
| "Tap to grant permission" | Tap it and allow the backend (Shizuku or root) |
| An app froze while I was using it | Grant Usage access |
| Auto-freeze does nothing | The app is not a candidate, an exclusion rule applies, or auto-freeze is off |
| Force stop is missing | Turn on **Advanced Features** and use Shizuku or root |
