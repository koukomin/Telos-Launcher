# Calls, keypad and SIM

Everything about placing and handling calls: the keypad with T9 search, speed dial, network codes, the in-call
screen, gestures, auto redial, caller ID masking and dual SIM routing. Settings named below are in the
[Phone settings](./#phone-settings). Call recording is on [Recents and recording](./recents-recording).

## The keypad

Open it with the round button on the Dialer screen, from a `tel:` link, or from the Direct call widget.

| Action | Result |
| --- | --- |
| Tap a digit, `*` or `#` | Adds it to the number. The number is formatted for your country while you type |
| Long-press `0` | Types `+` |
| Long-press `1` | Calls the voicemail number, unless you assigned a speed dial to slot 1 |
| Long-press `2` to `9` | Calls the number saved for that slot, or shows "Speed dial not assigned" |
| Tap the typed number | Nothing (it is display only) |
| Long-press the typed number | Pastes the clipboard. Only digits, `+`, `*` and `#` are kept |
| Tap backspace | Removes the last character |
| Long-press backspace | Clears the whole number |
| "Add to contacts" (under the number) | Opens Android's "new contact" screen with the number filled in |

While the number is empty, the list under the display shows your recent calls with the same swipe gestures as
[Recents](./recents-recording#the-list). Once you type, it shows contacts that match the digits.

### T9 search

Typing digits searches your contacts as you would on an old phone: `226` finds "Bambi" and "Anna Bambi".

- A name matches when the digits are the start of any word of the name, or appear anywhere in the digit
  sequence of the whole name.
- Name matches are listed first, then contacts whose number contains the typed digits.
- Alphabets: Latin (English), Greek and Cyrillic. Greek matching ignores accents.
- The key labels follow the chosen alphabet.

### Network codes (MMI, USSD, secret codes)

Codes such as `*#06#` or `*21*number#` are dialed like any number and handed to the network unchanged. They are
exempt from the caller ID prefix described [below](#caller-id-masking-clir). Typing `#` followed by 4 to 6 digits
and `#` is *not* sent to the network, it unlocks hidden contacts, see
[Privacy and screening](./privacy-screening#hidden-contacts).

### Keypad settings

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| T9 Language Alphabet | T9 Search Settings | Latin (English) | Latin, Greek or Cyrillic key labels and matching |
| Dialpad Sounds | Sounds & Vibrations | off | DTMF tone for each key while typing (150 ms) |
| Dialpad Vibration | Sounds & Vibrations | on | Haptic tick for each key |
| Hide dialpad letters | Sounds & Vibrations | off | Shows only digits on the keys |
| Remember dialpad digits | Calling | on | Keeps the typed number when you reopen the keypad. A number starting with `#` is never saved |
| Open dialpad on launch | Calling | off | Phone starts on the keypad |
| Speed Dial Setup (Long-Press 1-9) | Speed Dial Setup | empty | Assigns a number to each slot. Slot 1 overrides the voicemail shortcut |
| Confirm before calling | Calling | off | Shows a "Place call" sheet for taps in lists |
| Tap to call | Calling | on | A tap on a recent or contact calls at once. When off, the tap opens the contact page |

::: tip Assign a speed dial from a contact
Open the contact page, choose **Speed dial** and pick a slot. See [Contacts](./contacts#the-contact-page).
:::

## Placing a call

1. Type a number, or tap a contact or recent.
2. On a dual SIM phone with "Always Ask", tap the button of the SIM you want. See [Dual SIM](#dual-sim).
3. If the phone is not the default dialer the call is handed to the system dialer.

Biometric confirmation, caller ID prefix and SIP are applied on the way, in this order: caller ID prefix, then
the biometric check (when the number is guarded), then SIP if SIP is the default, otherwise the SIM.

## The in-call screen

Shown for SIM calls when Telos is the default dialer. It appears over the lock screen and turns the screen on.

### Incoming call

- A blurred copy of the contact photo is the background; the name or number and "Incoming call" are shown.
- **Answer style** decides how you answer: two buttons (Answer, Reject) or a swipe (left to reject, right to
  answer).
- **Remind me** offers a callback notification in 5, 15, 30 or 60 minutes.
- **Reject + SMS** (a link above the buttons, shown while a template is set) declines the call and sends the template
  text to the caller. See [Messages](../messages/sms-mms#quick-replies).
- The call also shows as a heads-up notification with a full-screen intent.

### During a call

| Button | Effect |
| --- | --- |
| Mute, Speaker | Toggle |
| BT | Cycles the audio route (earpiece, speaker, Bluetooth headset) |
| Keypad | Sends DTMF tones |
| Hold | Puts the call on hold |
| Record or REC | Starts or stops recording, see [Recents and recording](./recents-recording#call-recording) |
| Add | Starts another call |
| Merge, Swap | Conference two calls or switch between them. A conference shows the participant count |
| Note | Opens the call note. Hidden when **In-call notes** is off |

**In-call notes** keep one note per number. The note is saved under **Calling > Notes** and shows on the contact
page. A floating note window can stay on screen after you leave the call screen (needs "Display over other apps").

### After a call

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Missed call popup | Calling | on | A sheet with Dismiss, Message (sends the reject template), Call and WhatsApp, and how long it rang |
| Popup after every call | Calling | off | The same sheet after answered calls |
| Vibrate on Answer | Sounds & Vibrations | off | One short pulse when an outgoing call is answered |
| Vibrate on Hangup | Sounds & Vibrations | off | One short pulse when a call ends |
| Secure call screen | Incoming call | off | Blocks screenshots and hides the call screen in the recent apps overview |
| SIM 1 color, SIM 2 color | Incoming call | green, blue | Accent for SIM badges (green, blue, orange, red, purple, pink, teal) |
| Answer style | Incoming call | Buttons | Buttons or Swipe |

Call state can also appear in the launcher's Dynamic Island.

## Smart gestures

All gestures run only while the Telos call screen of a SIM call is open (not SIP calls) and are off by default.

| Gesture | Setting | Effect |
| --- | --- | --- |
| Raise to answer | Gestures | Lift the phone to your ear (proximity sensor covered and an ear pose) |
| Flip to decline | Gestures | Turn the phone face down for a short moment |
| Rain mode | Gestures | Four quick sideways strokes, left-right-left-right, answer the call |
| Pocket mode | Calling | Ignores taps on an incoming call while the proximity sensor is covered |
| Proximity speaker | Calling | Switches to speaker when the phone is away from your ear |
| Volume keys toggle DND | Gestures | Up, Up, Down, Down within about 1.6 seconds toggles Do Not Disturb (priority only) |
| Volume DND only on lock screen | Gestures | On by default: the shortcut works only while the screen is off |

The volume shortcut needs two things the settings screen links to: the **Accessibility service** and **Do Not
Disturb access**. The accessibility service only reads volume key presses.

## Auto redial

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Auto redial | Calling | off | Retries an outgoing call that ended busy, missed or rejected |
| Attempts | stored value | 3 (1 to 10) | Number of retries |
| Delay | stored value | 5 s (3 to 60) | Pause before each retry |

Hanging up yourself, an answered call or an incoming call stops the retries. Attempts and delay have no control
in the settings screen at this time and keep their defaults.

## Caller ID masking (CLIR)

**Privacy & Spam > Withhold caller ID (CLIR)** adds a prefix to the number you dial.

| Prefix | Region |
| --- | --- |
| `#31#` (default) | GSM standard |
| `*67` | US and Canada |
| `141` | UK |
| `1831` | Japan |
| Custom | Any prefix you type. Choosing it opens the editor; a blank prefix sends `#31#` |

Emergency numbers and network codes starting with `*` or `#` are never prefixed. Whether your carrier honors the
prefix is up to the carrier.

## Dual SIM

| Rule | Where | Order |
| --- | --- | --- |
| Per-number SIM | Contact page, "Always use this SIM for this number" (tap 1 or 2) | Checked first |
| Default Call SIM | Call Options | Then this setting |

Default Call SIM values: **Always Ask** (default, shows one call button per SIM), **Last used SIM**, **Same SIM
as last call to this number**, **SIM 1**, **SIM 2**. A phone with one SIM always uses it. Recents show a coloured
SIM badge when two SIMs are present.

## Cellular network mode

Needs a privileged backend, **Shizuku** or **root**, and is <Badge type="info" text="untested" /> on most devices.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Preferred mode | Cellular network | System auto | System auto, 4G / LTE only, 5G + 4G, 5G only. Applied with a shell command for the default data SIM |
| Control backend | Cellular network | Auto (Shizuku then Root) | Which backend sends the command |
| 4G when screen is off | Cellular network | off | Switches to LTE when the screen turns off and back to the preferred mode when it turns on |
| 4G in Battery Saver | Cellular network | off | Uses LTE while Battery Saver is on |

A **Cellular** Quick Settings tile cycles LTE, 5G + 4G, 5G only and back to auto. The preferred mode is applied
again after a reboot.

## Limitations

- The in-call screen needs the default dialer role.
- Gestures depend on the proximity sensor and accelerometer of the phone.
- Raise to answer and flip to decline can trigger by accident in a pocket or on a table. Try them first.
- Cellular network mode uses a hidden Android command that some carriers and Android versions ignore.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| Calls do not show the Telos call screen | Set Telos Phone as the default dialer |
| The call button opens another dialer | The phone app role is missing, or the Phone permission is not granted |
| No SIM buttons | Only one SIM is active, or the Phone permission is missing |
| T9 shows nothing | The Contacts permission is missing, or the alphabet setting does not match your names |
| Voicemail key does nothing | Slot 1 has a speed dial, or no voicemail number is set by your carrier |
| Volume Do Not Disturb does nothing | Enable the accessibility service and Do Not Disturb access, and check "only on lock screen" |
| Reminders come late | Allow "Alarms & reminders". Call reminders use inexact alarms and can drift a few minutes |
