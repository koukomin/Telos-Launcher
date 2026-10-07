# Telos Voice Recorder

A voice recorder with a list of recordings, search, pause and a service that keeps recording with the screen off.
The layout is modeled on the Sound Recorder of OxygenOS, the colors, shapes and fonts follow the Telos theme.

::: tip At a glance
One big red button to record, pause and resume, AAC or Opus files, a standard and a voice mode, three qualities,
playback through the speaker or the earpiece, and the call recordings of Telos Phone in the same list.
:::

## What it is

Telos Voice Recorder is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. It records the
microphone to files in the private storage of Telos on your phone. It has no cloud, no account and no network
features. Switching it off in [Telos Store](../store/) hides its icon.

## Recording

1. Open **Telos Voice Recorder** and tap the red button. The first time, Android asks for the **microphone**
   permission (and on Android 13 and newer for notifications, so that the recording notification is shown).
2. The screen shows the time and a waveform. The buttons are **discard** (left), **stop and save** (middle) and
   **pause or resume** (right).
3. Leave the app, lock the screen: the recording goes on. A notification shows that it is running and has **Pause**
   and **Stop** buttons.

## The list

| Part | What it does |
| --- | --- |
| Search (top right) | Filters the list by name |
| Menu (three dots) | **Earpiece mode** or **Speaker mode** for playback, and **Settings** |
| Chips | **All recordings** or **Call recordings** (the recordings of [Telos Phone](../phone/recents-recording), which stay encrypted) |
| A recording | Tap to play, tap again to pause. The row opens a seek bar. The three dots offer **Rename**, **Share** and **Delete** |

Recordings are named `Recording_<date>_<time>` and listed newest first with their length and date.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Recording file format | AAC (.m4a) | AAC, or Opus (.ogg, Android 10 and newer) |
| Recording mode | Standard | **Standard** uses the normal microphone input, **Voice** uses the input that is tuned for speech |
| Quality | Balanced | Compact (24 kbit/s, 16 kHz), Balanced (48 kbit/s, 16 kHz), High (96 kbit/s, 44.1 kHz). Opus always uses 48 kHz |
| Play through the speaker | On | Off plays through the earpiece, like on a phone call |

## Privacy

Recordings stay in the app's own storage (`files/VoiceRecordings`), nothing is uploaded. Other apps cannot read them
unless you share a recording. Recordings of Telos Voice Recorder are not encrypted at rest, the call recordings of
Telos Phone are.

## Limitations

- No AI assistant, no transcription, no summary and no noise removal.
- No markers, no trimming and no cloud sync.
- Only AAC and Opus, no WAV.
- A recording that was started from the [floating launcher](../launcher/desktop-and-overlays#floating-launcher)
  needs the microphone permission to be given in the app first.
