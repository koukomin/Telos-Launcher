# Smart Freeze

Freeze or hide apps you do not need right now. A frozen app cannot run in the background until you
unfreeze it, and it is launched again (and unfrozen) the next time you open it from the launcher.

::: info Where
Settings > **Freeze Manager**. It has a Dashboard, a backend picker, auto-freeze options and the app list.
There is also a **Freeze** home screen widget with a "Freeze now" button.
:::

## Backends

Smart Freeze talks to the system through one backend. With the default setting (**System default**) it
takes the first one that is available, in this order.

| # | Backend | What it needs | Suspend / disable | Force stop, clear cache |
| --- | --- | --- | --- | --- |
| 1 | Shizuku | The [Shizuku](https://github.com/RikkaApps/Shizuku) app running and permission granted | yes | yes |
| 2 | Dhizuku | The [Dhizuku](https://github.com/iamr0s/Dhizuku) app and permission granted | yes | yes |
| 3 | Root | A rooted device | yes | yes |
| 4 | Device owner | Telos set as device owner with `adb` (guided setup screen) | yes | no |
| 5 | Island | The Island app, only for apps already in the Island profile | request only | no |

::: warning Dhizuku is automatic only
In the backend picker you can choose Shizuku, Root, Island or Device owner by hand. Dhizuku is used only
when the picker is on System default and no Shizuku is available.
:::

Two freeze methods exist per app: **Suspend** (the default) and **Disable**. Disable is only used by
Shizuku, Dhizuku, root and device owner.

::: details Device owner setup
The setup screen shows an exact `adb` command and the risks. Device owner can only be granted on a device
without Google account, work profile or another device owner, and it is hard to undo (usually a factory
reset, or a second `adb` command). As device owner Telos can suspend and hide apps, but not force stop
them or clear their cache.
:::

## What it can do

- Freeze or unfreeze single apps, or several at once, from the app list and the context menu.
- **Protected packages**: the system, system UI, settings, keyguard, telecom, Google Play services, the
  Play Store and Telos itself are never frozen, force stopped or cleared.
- **Advanced features** switch (needs Shizuku, Dhizuku or root) adds Force stop and Clear cache to context menus.
- Show or hide system apps and apps without an icon in the list. Hide frozen apps entirely, or show them
  grayscale or with a snowflake badge.
- Dashboard with frozen apps, history, counters, and per-app state (normal, suspended, disabled, hidden).
- Frozen apps can be driven by a [context profile](./launcher/desktop-and-overlays#context-profiles), which can override the freeze profile.

## Automatic freezing

Only apps you opt in (the "candidates") are ever frozen automatically. Choose a profile.

| Profile | Idle timeout | On screen off | On battery saver | Media session check |
| --- | --- | --- | --- | --- |
| Battery Saver | 30 min after screen off | no | yes | strict |
| Balanced | 15 min | no | yes | strict |
| Aggressive | 5 min | yes, immediately | yes | strict |
| Ultra Aggressive | 1 min | yes, immediately | yes | relaxed |
| Custom | your values | your choice | your choice | your choice |

Before every automatic freeze these apps are skipped:

| Rule | Always on? |
| --- | --- |
| Apps you marked **Never freeze** | yes, wins over everything |
| The app in the foreground (needs Usage access) | yes |
| Apps with an ongoing notification or a foreground service | yes |
| Android Auto / car mode is active | yes |
| Apps with an active media session | only in Strict mode |
| Apps playing music (switch), apps with network activity above a threshold (switch) | optional |

::: tip Grant usage access
Without Usage access Telos cannot know which app is in the foreground, so an app could be frozen while you
use it. Settings shows a hint until it is granted.
:::

A **"N apps ready to freeze"** notification lets you freeze on demand. With Island as backend Android does
not allow a silent freeze from the background, so the notification must be tapped.

## Work profile (Android work mode)

Under Settings > **Work profile**:

| Feature | What it does |
| --- | --- |
| Set up / manage | Opens the system user settings |
| **Work Mode** switch | Pauses or resumes the work apps (Android quiet mode). Needs the "manage profiles" permission |
| Lock work profile toggle | Asks for authentication before pausing or resuming from the app drawer |
| **Initialize Telos Sandbox** | Starts Android's managed profile setup so an isolated work profile can hold cloned apps. Shown only while no work profile exists |

::: warning Experimental
"Clone to Sandbox" in an app's menu copies the app's APK into the work profile through a small bridge
service of Telos. It is new and <Badge type="info" text="untested" /> across devices. Whether it works
depends on your device and Android version.
:::

## Limitations

- With Island Telos can only send the request. It cannot confirm that the app really ended up frozen,
  and Android blocks it when no screen is visible.
- What works depends on the device and Android version. Dhizuku, root and device owner are
  <Badge type="info" text="untested" /> on many devices.
- Freezing system apps can cause problems. Disabling or force stopping one can break core phone functions.
