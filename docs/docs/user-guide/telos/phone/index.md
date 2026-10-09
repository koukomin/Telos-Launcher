# Telos Phone

Telos Phone is a complete phone app that lives inside the launcher: dialer, recents, contacts, favorites,
in-call screens, call recording, call screening and a long list of call settings. Its layout follows the
*Right Dialer* project; several privacy and power-user ideas come from *Secure Dialer* and *Ever Dialer*
(see the [credits](https://github.com/koukomin/Telos-Launcher#projects-telos-is-based-on)).

Phone is a [virtual app](../#how-the-built-in-apps-work): it is part of the launcher, not a separate APK.
Text messages are in the same app, see [Messages](../messages/).

::: info This section
This is the feature reference of Telos Phone. It is split into pages:

- [Calls](./calls) for the keypad, T9 search, network codes, the in-call screen, gestures and dual SIM
- [Contacts](./contacts) for the contact list, the contact page, groups, duplicates and vCards
- [Recents and recording](./recents-recording) for the call log and call recording
- [Privacy and screening](./privacy-screening) for blocking, hidden contacts, app lock and backups
- [SIP and FRITZ!Box](./sip-fritzbox) for VoIP calls and the remote phonebook
:::

## How to open it

| From | What happens |
| --- | --- |
| The Telos Phone icon in the app grid or search | Opens the Recents tab |
| A `tel:` link or a "dial" request from another app | Opens the keypad with the number filled in, nothing is dialed |
| An `ACTION_CALL` request for a `tel:` number | Opens the keypad with the number filled in. You press the call button yourself, so no other app can start a call for you |
| The Recents or Direct call home screen widget | Opens Recents or the keypad |
| The overflow menu of a Messages screen | Switches between Phone and [Messages](../messages/) |

## A tour of the screens

### Dialer screen (the main screen)

- The title is "Dialer". Under it is a rounded **Search** field that filters the current tab.
- A bottom bar switches between **Favorites**, **Recents** and **Contacts**.
- A round button at the bottom right opens the **Keypad**.
- The **three-dot menu** at the top has **Messages** and **Settings**.
- On the Recents tab the search field has a **filter** button that shows or hides the filter chips.
- Back from the Keypad or Messages returns to Recents.

### Keypad

Digits, a live T9 result list, a green call button (or one button per SIM on dual SIM phones), a SIP button
when a SIP account is ready, and a backspace key. Details are on [Calls](./calls#the-keypad).

### Recents, Contacts, Favorites

Recents is the call log; Contacts and Favorites share one list screen, Favorites showing only starred
contacts. See [Recents and recording](./recents-recording) and [Contacts](./contacts).

### Contact page

Opens when you tap a contact photo, a recent's photo or the **Details** action. Call, message and chat
buttons, every number of the contact, notes, call history and the privacy toggles. See
[Contacts](./contacts#the-contact-page).

### Call screens

The system call screen (`CallActivity`) for SIM calls, a separate SIP call screen, the fake call screen, a
missed-call popup and a floating note window. See [Calls](./calls#the-in-call-screen).

### Phone settings

Reached from the three-dot menu. All settings tables in this section name the caption they live under, for
example "Calling" or "Privacy". The settings screen uses rounded cards with accent colored section captions.

## Permissions

Android asks when a feature needs a permission. These are declared by the app:

| Needed for | Permission or access | Notes |
| --- | --- | --- |
| Placing and answering calls | Phone (`CALL_PHONE`, `ANSWER_PHONE_CALLS`, `READ_PHONE_STATE`, `MANAGE_OWN_CALLS`) | Without it calls go through the system dialer |
| Recents and recents widget | Call log (`READ_CALL_LOG`, `WRITE_CALL_LOG`) | Swipe-to-delete removes the entry from the system log |
| Contacts | Contacts (`READ_CONTACTS`, `WRITE_CONTACTS`) | Write is used for favorites, vCard import and deleting duplicates |
| Call recording | Microphone (`RECORD_AUDIO`) | Shizuku or root for two-way capture |
| Reminders and fake calls | "Alarms & reminders" (`SCHEDULE_EXACT_ALARM`) | Optional; without it alarms can be late |
| Call and message notifications | Notifications | Android 13 and later |
| Floating in-call note | "Display over other apps" (`SYSTEM_ALERT_WINDOW`) | Only for the floating note window |
| Volume-key Do Not Disturb | Accessibility service and Do Not Disturb access | See [Calls](./calls#smart-gestures) |
| Biometric lock | A fingerprint, face or device PIN in Android | |
| SMS | `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | See [Messages](../messages/) |
| SIP | Microphone, foreground service of type phone call | See [SIP and FRITZ!Box](./sip-fritzbox) |

## Make Telos the default phone app

Open **Phone settings > Default phone app > Set Telos Phone as default dialer**. The summary states what the
role is for: the in-call UI, answering, and mute and speaker.

| | Default phone app | Not the default |
| --- | --- | --- |
| Call button | Places the call through Android's telecom service, with the chosen SIM | Hands the number to the system dialer, which still asks you to confirm |
| In-call screen | Telos screen | The system's screen |
| Call recording, gestures, auto redial, screening | Work | Not available, they hang on the in-call service |
| Call log, contacts, keypad | Work | Work (read permissions needed) |

The role is requested through Android's role manager (Android 10 and later) or the older "change default
dialer" dialog. You can give the role back at any time in Android's default apps settings.

::: tip
Phone can be switched off in [Telos Store](../store/) like the other Telos apps, but it is never touched by the
crash guard because Android needs a working phone app.
:::

## Feature matrix

| Feature | Where | Needs default dialer | Status |
| --- | --- | --- | --- |
| Keypad, T9 search, speed dial, voicemail key | Keypad | no | stable |
| Network codes (MMI, USSD) | Keypad | no | stable |
| Recents with filters, grouping, SIM badges | Recents | no | stable |
| Contact list, A-Z index, favorites | Contacts, Favorites | no | stable |
| Contact page, notes, QR code, ringtone | Contact page | no | stable |
| Duplicate finder, groups, vCard import | Settings > Tools | no | stable |
| Dual SIM routing | Calls and settings | yes for choosing a SIM | stable |
| In-call controls, conference, notes | Call screen | yes | stable |
| Smart gestures | Settings > Gestures | yes | depends on the sensors |
| Auto redial | Settings > Calling | yes | stable |
| Call recording | Call screen, settings | yes | <Badge type="warning" text="device dependent" /> |
| Root recording backend | Settings > Call recording | yes | <Badge type="info" text="untested" /> |
| Call screening and block list | Settings > Privacy & Spam | yes | stable |
| Hidden contacts, stealth menu, app lock | Settings > Privacy | no | stable |
| Fake call, call reminders | Settings > Tools, call screen | no | stable |
| Cellular network mode | Settings > Cellular network | no, needs Shizuku or root | <Badge type="info" text="untested" /> |
| SIP / VoIP account | Settings > SIP | no | <Badge type="warning" text="experimental" /> |
| FRITZ!Box remote phonebook | Settings > Remote phonebook | no | stable |
| Encrypted settings backup | Settings > Encrypted backup | no | stable |
| Home screen widgets | Widget picker | no | stable |

## Home screen widgets and tile

| Item | Where | What it does |
| --- | --- | --- |
| **Recent calls** widget | Widget picker | Shows the names (or numbers) of your last 4 calls as text. Tapping it opens the Recents tab. It needs the call log permission and says "Grant call log permission" without it |
| **Dialpad** widget (direct call) | Widget picker | A button that opens the keypad |
| **Cellular** Quick Settings tile | Android's Quick Settings editor | Cycles the preferred network mode, see [Calls](./calls#cellular-network-mode) |
| Dynamic Island | Launcher settings | Shows the state of a running call |

## Limitations

- Most call features need Telos to be the default phone app.
- Call recording quality and the other party's audio depend on the device.
- SIP and root features are untested on real devices.
- Gesture features use sensors and behave differently between phones.
- Phone has no export of settings except the encrypted backup, and no cloud sync.

## Where to go next

Use the pages listed at the top. If something does not work, each page ends with a troubleshooting table.
