# Privacy, call screening and backups

Call blocking, hidden contacts, the stealth settings menu, the biometric phone lock, protected calls, the secure
call screen, how Telos stores sensitive data and the encrypted settings backup. Everything runs on the phone:
screening compares numbers with your contacts and your own list, and nothing is sent anywhere.

## Call screening

Screening happens in a call screening service of Telos Phone. It needs the default dialer role (or the call
screening role) so that Android hands incoming calls to it.

::: warning Blocked calls leave no trace
A blocked call is rejected silently: it is **not written to the call log** and **no notification is shown**. You
cannot see afterwards that someone tried to call you. Turn rules on one at a time and watch for a while.
:::

### Rules

**Phone settings > Privacy & Spam**. All switches are off by default. The rules are checked in this order, and
the first one that matches blocks the call.

| Rule | Setting | Blocks |
| --- | --- | --- |
| Hidden numbers | Block hidden numbers | Private, restricted or unknown caller ID (an empty number) |
| Offline spam list | Offline spam list | Numbers on the Telos block list |
| Unknown callers | Block unknown callers | Numbers that are not in your Android contacts |
| International | Block international | Numbers starting with `+` or `00`, except emergency numbers |

::: danger Side effects
Blocking unknown or international callers also blocks legitimate calls such as a courier, a doctor's office or a
call from abroad. A number from your own contacts is never an "unknown caller", but it can still match the
international rule.
:::

### The two block lists

| List | Used by | How to add a number | How to manage |
| --- | --- | --- | --- |
| **Telos block list** | The "Offline spam list" rule | **Block number** in a contact's long-press sheet or on the contact page | Contact page: **Unblock number**. The list is part of the encrypted backup |
| **Android's blocked numbers** | The system, before and independent of Telos | Android's own screens | **Call Options > Blocked Numbers** opens the Android screen |

The Telos list is stored in an encrypted database (SQLCipher, key wrapped by the Android Keystore). The match is
an **exact comparison of the number text** the carrier presents, so `+49 30 123` and `030123` are different
entries. Block the number in the form shown in your call log.

## Hidden contacts

Hide numbers from Telos Phone's lists and unlock them with a code typed on the keypad.

### Set it up

1. **Privacy > Hidden contacts PIN**, enter 4 to 6 digits and save.
2. Open a contact page and choose **Hide contact** (or **Unhide contact**).
3. To see hidden items, dial `#`, your PIN and `#` on the keypad, for example `#4821#`. The **Hidden contacts**
   screen opens and hidden numbers show up in the lists again.

If no PIN has been set, dialing `#`, any 4 to 6 digits and `#` asks for biometrics or the device credential
instead.

### What is hidden

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Hide from contacts tab | Privacy | on | Hidden numbers are removed from the Contacts and Favorites lists |
| Hide from recents | Privacy | on | Their calls are removed from Recents |
| Mask name on incoming calls | Privacy | on | The incoming call screen shows "Private" instead of the name and number |
| Hidden contacts (list) | Privacy | | Lists every hidden number with an **Unhide** button and a **Lock hidden contacts** button |
| Hide this menu after PIN is set | Privacy | off | Stealth: the Hidden contacts items disappear from settings until you dial `#PIN#` |

Conversations with hidden numbers are also left out of the [Messages](../messages/#privacy) list, and the
notification of a text from a hidden number reads "New message" without the text.

### Unlock lifetime and lockout

- The unlock lasts until you tap **Lock hidden contacts** or Android ends the launcher process.
- The typed `#PIN#` is never saved: numbers starting with `#` are not kept by "Remember dialpad digits".
- After **5 wrong PINs** the next guess is refused for 30 seconds, then the delay doubles for every further
  failure, up to one hour. A correct PIN resets the counter.
- The PIN is stored as a PBKDF2-HMAC-SHA256 hash (200,000 rounds, random salt), never as text.
- There is no PIN recovery. If you forget it, you can reset by clearing Telos' data, which also removes the other
  Phone settings.

::: details What hiding does not do
Hiding only changes what Telos shows. The contact stays in Android, the call is still in Android's call log, and
a text from a hidden number is still stored by the system so another messaging app can show it. Do not rely on
it as a vault.
:::

## Locking the Phone app

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Lock Phone app | Privacy | off | Phone shows "Phone is locked" with an **Unlock** button. You unlock with biometrics (strong) or the device PIN, pattern or password |
| Biometric before placing a call | Privacy | Off | **Off**, **Every call**, or **Listed contacts only** |
| Require biometric to call | Contact page | | Adds one number to the list used by "Listed contacts only" |

- The Phone lock stays open until Android ends the launcher process. There is no timeout.
- The call check shows a "Confirm call" prompt before a call placed from the Telos keypad, lists or contact page,
  including SIP calls placed from there.
- Calls from other apps' `ACTION_CALL` links only open the keypad, so you press call yourself and the check applies. The check is not applied to auto redial or to the
  callback buttons of the missed-call popup.

## Secure call screen

**Incoming call > Secure call screen** (off by default) sets the secure window flag on the Telos call screen of SIM calls (not on the SIP call screen): screenshots
and screen recording are blocked and the screen is blanked in the recent apps overview.

## Where sensitive data is kept

| Data | Stored as |
| --- | --- |
| Call recordings | AES-256-GCM files, key in the Android Keystore |
| Hidden-contacts PIN | PBKDF2 hash in a private preferences file |
| Telos block list | SQLCipher database, key wrapped by the Keystore |
| SIP password, FRITZ!Box password | Encrypted with a Keystore key before they are saved |
| Notes, hidden and protected numbers, speed dials, scheduled SMS | Private app storage, not individually encrypted |
| Cached FRITZ!Box phonebook | Private app file `remote_phonebook.json` |

"Private app storage" means other apps cannot read it, but a rooted device or a full device backup could.

## Encrypted backup

**Phone settings > Encrypted backup**.

1. Choose **Export backup**, pick a place in the system file picker, and enter a password of at least 4 characters.
   A message says "Backup saved".
2. Choose **Import backup**, pick the file, and enter the same password.

The file is Base64 text: a random salt and IV followed by data encrypted with AES-256-GCM, with the key derived
from your password by PBKDF2-HMAC-SHA256 (120,000 rounds). A wrong password or a damaged file cannot be restored.

| Included | Not included |
| --- | --- |
| Every Phone setting in this section, speed dials, caller notes, per-number SIM and default numbers | Call recordings |
| Hidden numbers and protected numbers | The hidden-contacts PIN itself |
| The Telos block list | SMS, MMS and scheduled SMS |
| Disabled Telos apps (Store state) | The cached FRITZ!Box phonebook |
| Video service settings | Radio stations, music and video settings outside Video services |

An import **replaces** all Phone settings with the backup's, and **adds** the backup's blocked numbers to the
existing list without removing others.

::: warning Treat the file as sensitive
It contains your notes, block list and hidden numbers. Passwords for SIP, FRITZ!Box and the Video services are
inside the file as values encrypted with the Keystore key of **the phone that made the backup**. On another
phone they cannot be decrypted, so enter those passwords again after restoring. The file is the full Phone settings
snapshot, unlike the launcher-wide settings backup, which leaves out passwords, keys and hidden contacts.
:::

## Limitations

- Blocked calls are invisible; there is no "blocked calls" list.
- The Telos block list needs exact number matches.
- There is no PIN recovery and no automatic re-lock.
- The biometric call check can be bypassed by the missed-call popup.
- The backup does not move secrets between phones.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| A number is blocked and I do not know why | Check the four rules and Android's blocked numbers list. Turn rules off one by one |
| Calls from a number are not blocked | The Telos list matches exactly. Add the number as shown in the call log, or use Android's blocked numbers |
| `#PIN#` does nothing | The PIN was changed, or the lockout is active. Wait 30 seconds or more and retry |
| The Privacy items are gone from settings | Stealth is on. Dial `#PIN#` on the keypad first |
| Hidden contacts reappear | Android ended the launcher process, which locks them again |
| "Could not read backup" or a failed restore | Wrong password, or the file is not a Telos Phone backup |
| SIP password empty after restoring | Passwords are device bound. Enter it again |
