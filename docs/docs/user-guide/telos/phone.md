# Telos Phone

Telos Phone is a full phone app that lives inside the launcher: dialer, recents, contacts, call screens,
call recording, call screening and a long list of call settings. Its layout follows the *Right Dialer*
project; several privacy and power-user ideas come from *Secure Dialer* and *Ever Dialer* (see the
[credits](https://github.com/koukomin/Telos-Launcher#projects-telos-is-based-on)).

Phone is a [virtual app](./index#how-the-built-in-apps-work): it is part of the launcher, not a separate
APK. Text messages are in the same app, see [Messages](./messages).

::: tip Where things are
- **Tabs and menu:** Recents, Contacts, Favorites and the Keypad are the main screens. Messages and
  Settings are in the overflow menu at the top.
- **Settings:** everything on this page lives in the Phone settings screen, grouped by the captions
  quoted below (for example "Incoming call", "Privacy", "Call recording").
:::

## Getting started

### Permissions

Android asks for permissions when a feature needs them. Declared by the app:

| Needed for | Permission or access |
| --- | --- |
| Placing and answering calls | Phone (`CALL_PHONE`, `ANSWER_PHONE_CALLS`, `READ_PHONE_STATE`) |
| Recents | Call log (read and write) |
| Contacts | Contacts (read and write) |
| Call recording | Microphone (`RECORD_AUDIO`); Shizuku or root for two-way capture |
| Reminders, fake calls | "Alarms & reminders" (exact alarms) |
| Call notifications | Notifications |
| Volume-key Do Not Disturb | Accessibility service and Do Not Disturb access |
| Biometric lock | A fingerprint, face or device PIN set up in Android |

### Make Telos the default phone app

Open **Settings > Default phone app > Set Telos Phone as default dialer**. The screen states that this is
required for the in-call UI, answering, and mute/speaker. Without it the call features that depend on
Android's call framework do not work.

::: tip
You can switch Phone on or off in [Telos Store](./store) like any other Telos app.
:::

## Dialer

| Feature | How to use it | Setting |
| --- | --- | --- |
| T9 predictive search | Type digits, matching contacts appear. Latin, Greek and Cyrillic alphabets, Greek matching ignores accents | T9 Search Settings > T9 Language Alphabet |
| Dialpad memory | Reopening the keypad shows the last number | Calling > Remember dialpad digits |
| Open on launch | Phone starts on the keypad | Calling > Open dialpad on launch |
| Tones and haptics | Optional key sounds and vibration | Sounds & Vibrations |
| Letters on keys | Hide the letters to show only digits | Sounds & Vibrations > Hide dialpad letters |
| Voicemail key | A voicemail key is part of the keypad | Keypad |
| Speed dial | Long-press 1 to 9 to call an assigned number | Speed Dial Setup (Long-Press 1-9) |
| Network codes | MMI / USSD / secret codes such as `*#06#` or `*21*...#` are passed to the network unchanged | none |
| Confirmation | Ask before placing a call | Calling > Confirm before calling |
| Tap to call | Tapping a recent or contact calls at once | Calling > Tap to call |

## Recents

- Swipe a row to call or to message.
- Filters: missed, incoming, outgoing, rejected and talk time. They are shown on demand through the
  filter button, not permanently.
- Repeated calls from the same number are grouped.
- The call log can be exported.
- Each row can show a SIM badge (colors for SIM 1 and SIM 2 are configurable under "Incoming call") and a
  number type label such as Mobile, Home or Work.
- **Calling > Show numbers in recents** toggles number display.

## Contacts

### Contact page

The contact page combines the call history with action buttons:

- Call, SMS, and chat, voice and video buttons for WhatsApp, Telegram, Signal and Viber. These open the
  other app, so the app must be installed.
- SIM choice, notes, copy number, favorites toggle, QR code, call reminder.
- Speed dial and a ringtone per contact.

### Managing contacts

| Feature | Details |
| --- | --- |
| A-Z index | Fast scrolling through the list |
| Contact groups | System and Google contact groups (Tools > Contact groups) |
| Duplicate finder | Finds contacts that share a number so they can be cleaned up (Tools > Duplicate contacts) |
| vCard | Import a `.vcf` file (Tools > Import vCard); vCard export is listed as a feature |
| QR code sharing | Show a contact as a QR code |
| Defaults | Per-contact default number and default SIM |

## Dual SIM

Choose the SIM per call, set a default SIM per number, or use the last used SIM. The global fallback is
**Call Options > Default Call SIM**. Recents show which SIM was used.

## Calls

### Incoming calls

- Call screen with a blurred contact photo and two answer styles, buttons or swipe (Incoming call >
  Answer style).
- **Reject with SMS:** a template message sent when you decline (Calling > Reject with SMS). This needs
  the SMS permission.
- **Remind me:** a callback reminder, delivered as a notification.
- Missed-call popup with callback options, optional popup after every call, and call state shown in
  the Dynamic Island.

### During a call

Mute, speaker, Bluetooth routing, hold, keypad, merge, swap and add call. **In-call notes** adds a notes
button and a floating note that stays when you leave the call screen; all notes are collected under
Calling > Notes.

### Smart gestures

| Gesture | Effect | Notes |
| --- | --- | --- |
| Raise to answer | Answer by lifting the phone to your ear | |
| Flip to decline | Decline by turning the phone face down | |
| Rain mode | Answer with a left-right-left-right shake | For wet hands |
| Pocket mode | Ignore taps while the proximity sensor is covered | |
| Proximity speaker | Switch to speaker when the phone is away from the ear | |
| Volume keys toggle DND | Up-Up-Down-Down toggles Do Not Disturb | Needs the accessibility service and DND access; can be limited to the lock screen |

### Other tools

- **Auto redial** retries busy, unanswered or rejected outgoing calls (Calling > Auto redial).
- **Fake call** schedules a simulated incoming call (Tools > Fake call).
- **Caller ID masking (CLIR)** hides your number on outgoing calls, with a selectable or custom prefix.
  Emergency numbers bypass it.
- **Cellular network mode:** a preferred mode, a Quick Settings tile, and automation for screen off
  and Battery Saver. It needs a privileged backend (Shizuku or root, "Control backend").

## Call recording

Settings > Call recording. The backend can be **Auto** (Shizuku, then root, then microphone),
**Shizuku**, **Root** or the plain **microphone**; a privileged choice falls back to the microphone if it
fails. Further options: quality, auto-record (starts when a call becomes active) and automatic deletion
of old recordings after 7, 30 or 90 days. Saved recordings are listed under "Recordings" where you can
play or delete them.

| Backend | What it records |
| --- | --- |
| Shizuku or root | Tries the system's call audio source, which captures both sides where the device allows it |
| Microphone | Only what the microphone hears. The other party may be silent or faint |

::: warning Legal notice
Recording calls may be restricted or require the consent of everyone on the call where you live. You are
responsible for using this feature lawfully.
:::

::: warning Limitations
Which audio source works depends on the device and Android version. Many devices block call audio for
normal apps. The root backend is <Badge type="warning" text="untested" /> on real devices.
:::

## Call screening

Screening runs on the phone: numbers are compared with your contacts and your own list, and nothing is
sent anywhere. Under "Privacy & Spam" you can switch on:

- **Offline spam list:** block numbers you added to the Telos block list.
- **Block hidden numbers:** private, restricted or unknown caller ID.
- **Block unknown callers:** numbers not in your contacts.
- **Block international:** numbers that start with `+` or `00`.

"Call Options > Blocked Numbers" manages the list.

::: warning
Blocking unknown or international callers also blocks legitimate ones (a courier, a doctor's office,
a call from abroad). Try one rule at a time.
:::

## Privacy and security

| Feature | Details | Setting |
| --- | --- | --- |
| Hidden contacts | Numbers are kept out of lists until unlocked by dialing `#PIN#` on the keypad. PIN of 4 to 6 digits | Privacy > Hidden contacts PIN |
| Hide scope | Separate switches for the contacts tab, recents and masking the name on incoming calls | Privacy |
| Stealth menu | After a PIN is set, the privacy items disappear from settings until you dial `#PIN#` | Privacy > Hide this menu after PIN is set |
| Biometric lock | Require biometrics or device PIN to open Phone, and optionally before placing a call or for chosen numbers | Privacy > Lock Phone app |
| Secure call screen | Blocks screenshots and hides the call screen in the recent apps overview | Incoming call > Secure call screen |
| Encrypted recordings | Stored encrypted with a key that stays in the Android Keystore | automatic |
| Vault PIN | Stored as a PBKDF2 hash; guesses are slowed down after repeated wrong entries | automatic |
| Stored passwords | SIP and FRITZ!Box passwords are kept encrypted | automatic |

::: details What hidden contacts do not do
Hiding only changes what Telos shows. A text from a hidden number is still stored by Android, so
the system messaging app can show it (see [Messages](./messages#hidden-contacts)). Hidden contacts are
not removed from the system contacts.
:::

### Backups

**Export backup** and **Import backup** (Encrypted backup) write an AES-256-GCM file protected by a password
you choose. It contains your notes, block list and phone settings.

::: warning Check before sharing
The Phone backup file is a snapshot of the phone settings. The launcher-wide settings backup leaves out
passwords, keys and hidden contacts, but the separate Phone backup is built from the full phone settings and is not documented to omit them.
Treat the file and its password as sensitive.
:::

## SIP / VoIP <Badge type="warning" text="experimental" />

Add a SIP account, for example a FRITZ!Box IP telephone, under "SIP / VoIP account" (server, user,
password, optional display name). The engine is [baresip](https://github.com/baresip/baresip).

- The account stays registered in the background only while it is switched on.
- Incoming SIP calls get a call screen, a notification and entries in the call log.
- **Outgoing calls** can use SIP as a separate button, as the default, or not at all (receive only).

::: warning Limitations
<Badge type="warning" text="untested" /> on real devices. SIP calls use their own call screen, not
Android's call framework. Messages, video and Bluetooth routing for SIP are not implemented.
:::

## FRITZ!Box remote phonebook

Identifies callers from an AVM FRITZ!Box telephone book (TR-064). Set it up under "Remote phonebook
(FRITZ!Box)": address (for example `fritz.box`), user and password, then "Sync now". The book is cached
locally and used for incoming calls and recents. The password is stored encrypted. The phone must reach
the FRITZ!Box, for example over your home Wi-Fi.

## Home screen widgets

Recents and direct call widgets are available from the widget picker.

## Limitations

- Many features need Telos to be the default phone app.
- Call recording quality and the other party's audio depend on the device.
- SIP and root features are untested; MMS and SMS details are on the [Messages](./messages) page.
- Gesture features use sensors; behavior differs between devices.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| Calls do not show the Telos call screen | Set Telos Phone as the default dialer (Settings > Default phone app) |
| Recents are empty | Allow the call log permission |
| Recording is silent or one-sided | Switch the backend; microphone only picks up your side on many phones |
| Reminders or fake calls come late | Allow "Alarms & reminders" for Telos in Android settings |
| Volume-key Do Not Disturb does nothing | Enable the accessibility service and Do Not Disturb access |
| Forgot the hidden-contacts PIN | Wrong guesses are delayed, so wait and retry; there is no recovery option documented |
| Calls to a number are blocked | Check Blocked Numbers and the screening switches |
