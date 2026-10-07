# Telos Screen Recorder

Records the screen to a video, with the microphone if you want, and keeps the recordings in `Movies/Telos`.

::: tip At a glance
Resolution up to the size of the screen, 30 or 60 frames per second, three qualities, microphone, a countdown,
touch dots, pause and resume from the notification, and a stop when the screen turns off.
:::

## What it is

Telos Screen Recorder is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. It uses Android's
screen capture (MediaProjection), so Android asks for permission **every time** a recording starts and shows its own
recording indicator. Nothing is uploaded.

## Recording

1. Open **Telos Screen Recorder** and tap the red button, or use the **Screen recorder** tool of the
   [floating launcher](../launcher/desktop-and-overlays#floating-launcher) to start it over any app.
2. Allow the screen capture in Android's dialog. If you turned on the microphone, Android also asks for the
   microphone permission once.
3. After the countdown the recording starts. A notification with a timer shows it, with **Pause** and **Stop**. The
   same buttons are on the screen of the app.
4. The video is saved to `Movies/Telos` (Android 10 and newer, in the phone's own movies folder of the app on older
   versions) and listed in the app. Tap it to play it, for example in [Telos Video](../video/). The menu of a recording
   shares or deletes it.

Starting the recording from the floating launcher while a recording runs stops it.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Resolution | 1080p | Screen resolution, 1080p, 720p or 480p. The number is the short side of a picture in the 16:9 format of a TV, so 1080p is 1920 pixels on the long side. A smaller screen is never enlarged |
| Frame rate | 30 fps | 30 or 60 frames per second (60 doubles the data rate) |
| Quality | Balanced | Compact (4 Mbit/s), Balanced (8 Mbit/s), High (16 Mbit/s) for 30 fps |
| Record the microphone | Off | Adds your voice. Sound that the phone plays cannot be recorded yet |
| Show touches | Off | Shows a dot where the screen is touched while recording. It changes a system setting, so Android asks for the permission to change system settings, and the old value is restored afterwards |
| Countdown | 3 s | None, 3 or 5 seconds before the recording starts |
| Stop when the screen turns off | On | Keeps the recording from running with the screen off |

## Limitations

- **No system sound.** Only the microphone can be recorded. Recording what the phone plays would need its own
  encoder pipeline, which is not built.
- Content that apps protect (some video apps, banking apps) is recorded black. That is Android's rule.
- There is no Live Alert or Dynamic Island entry for a running recording.
- No editing, trimming or GIF export.
