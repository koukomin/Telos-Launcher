# Contacts, favorites and the contact page

The contact list, the favorites tab, the contact page with its action buttons, and the contact tools (groups,
duplicate finder, vCard import and sharing). Telos Phone reads and writes the **Android contacts**; it has no
contact database of its own, apart from the notes, hidden numbers and defaults it stores in its settings.

## The lists

**Contacts** and **Favorites** are the same list screen. Favorites shows only starred contacts.

| Element | What it does |
| --- | --- |
| Search field (top of the Dialer screen) | Filters by name or number. The match is highlighted in the row |
| A-Z index on the right edge | Tap a letter to jump to it. Hidden while a search is active |
| Section letters | Contacts are grouped by the first letter of the name |
| Contact photo or initials | Shown at the left of every row |
| Green phone button at the right | Calls the contact's default number (see below) |
| Tap a row | Opens the contact page |
| Swipe right | Starts a text message to that number (an `smsto:` link, see below) |
| Swipe left | Calls the contact. Honors **Confirm before calling** |
| Long-press a row | Sheet with Call, Message, Contact Details, Share and Block number |

Only contacts that have at least one phone number are listed. Contacts are sorted by name.

::: info The list screen also has a starred filter
The list screen can show a star filter and a strip of favorites at the top. In Phone these are hidden because
the tabs of the Dialer screen already do the job. The Favorites tab behaves the same way as the filter.
:::

### Which number is called

Tapping the green button, swiping or using a speed dial calls the **default number** of the contact: the one you
marked with **Set as default** on the contact page, or else the first number. Tapping a row when **Tap to call**
is off opens the contact page instead.

## The contact page

Open it by tapping a contact, a recent's photo, or **Details** in the long-press sheet.

### Top bar

| Button | Effect |
| --- | --- |
| Star | Adds or removes the contact from favorites (writes to the Android contact) |
| Share | Shares the contact as vCard text to any app |
| Trash | Clears the call history with this contact, after a confirmation. Enabled only when there is history |

### Header and action cards

- Large photo, name, company, job title and birthday when the contact has them.
- **Message** starts a text message to the default number. It is an `smsto:` link, so Android opens the messaging
  app that handles it. Telos Messages is one of the candidates, see [Messages](../messages/).
- **Call** calls the default number.
- **Video** opens a WhatsApp chat for the number. Voice and video calls through chat apps are listed below.
- **Email** opens your mail app when the contact has an address.

### History and numbers

- The last 8 calls with this contact, with type, time and duration.
- One card per phone number. Tapping it calls that number. With more than one number, a **Set as default** button
  chooses the default (stored per contact). Every number is labeled "Mobile" on this card.

### Integrations

Buttons for **WhatsApp, Telegram, Signal and Viber** open the other app, so it must be installed. For each app that
adds a voice or video entry to the Android contact, additional call buttons appear. They work only when that
app has synced its entry into your contact.

### Other cards

| Card | What it does | Stored where |
| --- | --- | --- |
| "Always use this SIM for this number" (1, 2) | Per-number SIM, only with two SIMs. Tap again to remove | Phone settings (device) |
| "Call over SIP" | Calls the number through your SIP account. Only when SIP is registered and not set to receive-only | Needs [SIP](./sip-fritzbox) |
| Notes | A free text note per number, also shown during calls | Phone settings (device) |
| Copy number, Remind me, QR code, Speed dial, Ringtone | See the next table | |
| Hide contact, Unhide contact | Adds or removes the number from hidden contacts | Phone settings |
| Require biometric to call | Adds the number to the list guarded by the biometric check | Phone settings |
| Block number, Unblock number | Adds or removes the number in the Telos block list | Telos block database (not Android's list) |

| Chip | Effect |
| --- | --- |
| Copy number | Puts the default number on the clipboard |
| Remind me | "Remind me to call back" in 5, 15, 30, 60 or 180 minutes. A notification opens the keypad with the number |
| QR code | Shows the contact as a QR code that another phone's camera can read (a vCard with name, numbers, emails) |
| Speed dial | Choose a slot from 1 to 9. Then long-press that digit on the keypad |
| Ringtone | Opens Android's ringtone picker and stores it as the contact's ringtone in the Android contact |

::: warning Notes, hidden numbers and defaults are not contact data
Notes, per-number SIM, default numbers, hidden numbers, speed dials and block entries live in Telos settings, not
in your Android contact. They are not synced to your Google account and are lost when you clear Telos data. Use
the [encrypted backup](./privacy-screening#encrypted-backup) to keep them.
:::

## Contact tools

Under **Phone settings > Tools**.

| Tool | What it does |
| --- | --- |
| Fake call | Schedules a simulated incoming call, see [Calls](./calls) and below |
| Contact groups | Lists the system and Google contact groups that are visible, with their member count. Tapping a group opens the Contacts tab. It does not filter by that group |
| Scheduled SMS | See [Messages](../messages/sms-mms#scheduled-sms) |
| Duplicate contacts | Finds contacts that share a number of 7 or more digits |
| Import vCard | Imports a `.vcf` file |

### Duplicate contacts

The finder groups contacts by shared number. For each group you can **Open** a contact, choose **Keep first,
delete extras**, or use **Merge all groups** at the bottom.

::: danger Deleting is permanent
"Keep first, delete extras" and "Merge all groups" **delete the other Android contacts** with all their data. They
are not merged field by field. Telos does not ask again and has no undo. Back up your contacts (export a `.vcf`
in your contacts app) first.
:::

### Import vCard

1. Open **Phone settings > Tools > Import vCard** and pick a `.vcf` file.
2. Telos reads each card: the name (`FN`, or `N` if there is no `FN`), every `TEL` and every `EMAIL`.
3. A message says how many contacts were imported.

Imported contacts are stored on the phone only (no account) and every number is typed "Mobile". Photos,
addresses, birthdays and other fields in the file are ignored. Several cards in one file are supported.

### Fake call

**Tools > Fake call**: enter a caller name and number (the chips Boss, Mom and Doctor fill in sample values),
choose a delay in seconds (5, 10, 30 or 60, or type your own) and tap **Schedule fake call**. At the chosen time a
call screen rings over the lock screen with that name. Answering shows a running timer; hanging up closes it. Only
one fake call can be scheduled at a time, and **Cancel scheduled** removes it. It uses an exact alarm when
"Alarms & reminders" is allowed.

## Settings summary

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Tap to call | Calling | on | Row tap calls or opens the contact page |
| Confirm before calling | Calling | off | Asks before calling from lists |
| Notes | Calling | | Lists all notes. Shows "No contact notes yet." when empty |
| Speed Dial Setup | Speed Dial Setup | empty | Slots 1 to 9 |
| Hide from contacts tab | Privacy | on | Hidden numbers disappear from this list until unlocked |

## Limitations

- Only contacts with a phone number are shown. Contacts without number cannot be found here.
- There is no add-contact form in the list screen. Use the keypad's "Add to contacts" or your contacts app.
- Contact groups are shown but cannot be edited or used as a filter.
- vCard sharing sends text, not a `.vcf` file attachment. Photos are not included.
- The "Video" card opens WhatsApp, not a generic video call.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "Telos needs access to your contacts" | Grant the Contacts permission |
| A contact is missing | It has no number, or its number is in the hidden list. Unlock hidden contacts |
| Chat app buttons are missing | The other app has not added call entries to the Android contact yet, or is not installed |
| Duplicates were removed by mistake | Restore the contact from your account's trash or a backup. Telos cannot undo |
| vCard import says 0 contacts | The file has no `TEL` or `FN` lines, or the Contacts write permission is missing |
