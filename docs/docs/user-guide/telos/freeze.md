# Smart Freeze

Freeze or hide apps you do not need right now. A frozen (suspended) app cannot run in the background
until you unfreeze it.

## Backends

Smart Freeze talks to the system through one of several backends. Which one is used depends on what is
available on your device.

| Backend | What it needs |
| --- | --- |
| Shizuku | The [Shizuku](https://github.com/RikkaApps/Shizuku) app running |
| Dhizuku | The [Dhizuku](https://github.com/iamr0s/Dhizuku) app (device owner level access) |
| Root | A rooted device |
| Island | The Island app, using its public intent API, only for apps already in the Island profile |
| Device owner | Telos set as device owner |

Backend ideas and OEM suspend fallbacks come from the Thor project.

## Features

- Freeze or unfreeze single apps, or several at once.
- Protection for critical apps, so you cannot freeze something the system needs.
- Multi-user targeting.
- Work Profile sandbox and a Work Profile quiet mode toggle.
- Automatic freezing (screen off, idle, battery saver) for apps you opted in. Apps in the foreground,
  with an active call or navigation, and similar cases are left alone, and a per-app "never freeze"
  override always wins.
- Force stop and clear cache with a privileged backend.

::: warning
Freezing system apps can cause problems. Keep the protection for critical apps switched on.
:::

## Limitations

- With Island the launcher can only send the request. It cannot always confirm that the app really
  ended up frozen, and Android may block it when no UI is visible.
- What works depends on the device and Android version. Dhizuku, root and device owner modes are
  <Badge type="info" text="untested" /> on many devices.
