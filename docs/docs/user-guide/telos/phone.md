# Telos Phone

A complete phone app inside the launcher: dialer, recents, contacts and favorites, call screens and a
large set of call settings. Its layout and look follow the *Right Dialer* project, and several privacy
and power-user features come from *Secure Dialer* and *Ever Dialer* (see the [credits](https://github.com/koukomin/Telos-Launcher#projects-telos-is-based-on)).

## Setup and permissions

| Needed for | Permission or access |
| --- | --- |
| Calling, call log | Phone and call log permissions |
| Contacts | Contacts permission |
| Call recording | Shizuku, root, or the microphone (see [Recording](#call-recording)) |
| Dual SIM routing | Phone permission |
| Scheduled reminders and exact timers | "Alarms & reminders" |
| Biometric lock | A fingerprint or face set up in Android |

::: tip
You can use Telos Phone next to your system dialer. Switch it on or off in [Telos Store](./store).
:::

## Dialer

| Feature | Details |
| --- | --- |
| T9 predictive search | Latin, Greek and Cyrillic alphabets, accent-insensitive Greek matching |
| Dialpad memory | Remembers the last entry |
| Voicemail key, speed dial | One-tap speed dial per contact |
| DTMF tones and haptics | Optional |
| Network codes | MMI / USSD / secret codes such as `*#06#` or `*21*...#` are passed to the network unchanged |

## Recents

- Swipe a row to call or to message.
- Filters: missed, incoming, outgoing, rejected, talk time. Filters are shown on demand.
- Repeated calls are grouped.
- The call log can be exported.
- SIM badges (with configurable colors for SIM 1 and SIM 2) and number type labels (Mobile, Home, Work).

## Contacts

### Contact page

Call history, chat, voice and video buttons for WhatsApp, Telegram, Signal and Viber, SIM choice, notes,
copy number, call reminder, QR code, favorites toggle, speed dial and ringtone per contact.

### Managing contacts

| Feature | Details |
| --- | --- |
| A-Z index | Fast scrolling in the list |
| Contact groups | Organize contacts |
| Duplicate finder | Find and merge duplicates |
| vCard | Import and export |
| QR code sharing | Share a contact as a code |
| Defaults | Per-contact default number and default SIM |

## Dual SIM

Per-call SIM choice, a default SIM per number, and "last used SIM". The call history shows which SIM was used.

## Calls

### During a call

Mute, speaker, Bluetooth routing, hold, keypad, merge, swap, add call, floating notes and caller notes.

### Incoming calls

- Call screen with a blurred contact photo and two answer styles: buttons or swipe.
- Quick reject with an SMS message, and "remind me" callback reminders.
- Missed-call and post-call popups, and call state in the Dynamic Island.

### Smart gestures

| Gesture | Effect |
| --- | --- |
| Raise to answer | Answers when you lift the phone |
| Flip to decline | Declines when you turn the phone over |
| Rain mode shake | Shake gesture for use with wet hands |
| Pocket mode | Avoids accidental touches |
| Proximity speaker | Speaker depends on the proximity sensor |
| Volume button | Do Not Disturb shortcut |

Also available: auto redial and scheduled fake incoming calls.

### Caller ID masking

Outgoing caller ID masking (CLIR) is available. Emergency numbers bypass it.

## Call recording

Three backends: **Shizuku**, **root** or the **microphone**. Quality presets, auto-record and automatic
deletion of old recordings are available.

::: warning Legal notice
Recording calls may be restricted or require consent where you live. You are responsible for using it lawfully.
:::

::: warning Limitations
Which audio source works depends on the device and Android version. The microphone backend records
what the microphone hears, which may not include the other party. Root features are
<Badge type="info" text="untested" /> on devices.
:::

## Call screening

Offline screening, no data leaves the phone. You can block hidden numbers, unknown numbers and
international numbers, and keep a personal block list.

## Privacy

| Feature | Details |
| --- | --- |
| Hidden contacts | Hidden behind a dialpad passcode, also kept out of [Messages](./messages) |
| Stealth settings menu | Hides the settings entry |
| Biometric lock | For the whole phone app, and for chosen numbers |
| Secure call screen | No screenshots |
| Encrypted recordings | Stored encrypted with a key in the Android Keystore |
| Vault PIN | Hashed with PBKDF2, guesses slowed after five wrong ones |
| Backups | Settings backups leave out passwords, keys and hidden contacts |

## SIP / VoIP <Badge type="warning" text="experimental" />

Add a SIP account, for example a FRITZ!Box IP telephone. The engine is
[baresip](https://github.com/baresip/baresip).

- The account is registered in the background only while it is switched on.
- Incoming SIP calls get a call screen, a notification and entries in the call log.
- Outgoing: as a separate SIP button, as the default, or not at all (receive only).

::: warning Limitations
Untested on devices. SIP calls use their own call screen, not Android Telecom. Text messages, video and
Bluetooth routing for SIP are planned, not implemented.
:::

## FRITZ!Box remote phonebook

Caller names come from an AVM FRITZ!Box telephone book (TR-064). The book is cached locally and used for
incoming calls and recents. The password is kept encrypted with a key in the Android Keystore.

## More

- Cellular network mode switcher with a Quick Settings tile and screen-off or battery-saver automation (needs a privileged backend).
- Home screen widgets: recents and direct call.
- Encrypted backup of all phone settings.

## Tips

- Set the default SIM per number for contacts you always call on the same line.
- Use hidden contacts together with the biometric lock for sensitive numbers.
- Try the call screening settings with a test number before enabling blocking of unknown numbers.
