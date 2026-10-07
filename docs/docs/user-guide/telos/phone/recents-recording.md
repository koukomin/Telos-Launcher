# Recents and call recording

The call log screen, its filters and gestures, the history export, and the call recorder with its backends,
quality presets, storage and encryption. Related: [Calls](./calls), [Contacts](./contacts).

## Recents

Recents shows the **system call log**, so calls made with other apps appear too, and Telos writes SIP calls into
the same log. It needs the call log permission; without it a "Call Log Permission Required" card with a **Grant
Permission** button is shown.

::: warning Only the newest 100 entries
Recents loads the 100 newest calls. Older entries stay in Android's call log but are not listed, filtered or
exported by Telos.
:::

### The list

| Element | What it does |
| --- | --- |
| Day headers | "Today", "Yesterday", or a date such as `12 March 2026` |
| Row | Name (or number), a type icon, duration, and the number label (for example Mobile) or the number. Red for missed and rejected calls |
| Count in brackets | Consecutive calls with the same number are grouped, "Anna (3)" |
| SIM badge | A coloured "1" or "2" appears when the phone has two SIMs. Colours are set in **Incoming call** |
| Tap the row | Calls the number, or opens the contact page when **Tap to call** is off. Honors **Confirm before calling** |
| Tap the photo | Opens the contact page |
| Swipe right | Starts a text message to the number |
| Swipe left | Deletes the entry from the call log. No confirmation, no undo |
| Long-press | Sheet with Call, Message, Contact Details and Delete |
| Search field | Filters by name or number. Greek accents and capitals are ignored |

The grouping only merges calls that follow each other in the list. A different call in between starts a new
group.

### Filters

Tap the **filter button** in the search field to show the chips. They stay visible while a filter is selected.

| Chip | Shows |
| --- | --- |
| All | Every entry |
| Today | Calls since midnight |
| Missed, Incoming, Outgoing, Rejected | One call type |
| Talk | Entries that have a duration above zero. The label is the total talk time of all listed calls, for example `Talk 1h 12m` |

The **share** icon next to the chips exports the call history as plain text, see below.

### Export the call history

1. Tap the filter button, then the share icon.
2. Choose an app in Android's share sheet.

The text has one line per call: `date time`, a tab, the call type, a tab, the name (or number), a tab, the number.
The subject is "Call history". The export always contains the full list of up to 100 entries, not only the
filtered chips.

### Recents settings

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Show numbers in recents | Calling | on | Shows the number below a known name |
| Tap to call | Calling | on | Row tap calls or opens details |
| Confirm before calling | Calling | off | Asks before a call from this list |
| SIM 1 color, SIM 2 color | Incoming call | green, blue | Accent colour of the SIM badge |
| Hide from recents | Privacy | on | Calls with hidden numbers are not listed until unlocked |

### Names that are not in your contacts

When a call has no cached name, Telos looks the number up in the cached
[FRITZ!Box phonebook](./sip-fritzbox#fritzbox-remote-phonebook) (if enabled) and shows that name.

## Call recording

Telos Phone can record the audio of a call. It is an **in-call feature**: it needs Telos to be the default phone
app and the microphone permission.

::: warning Legal notice
Recording calls may be restricted or need the consent of everyone on the call where you live. You are responsible
for using this feature lawfully. Telos plays no announcement to the other party.
:::

### Start and stop

| Way | How |
| --- | --- |
| Manual | Tap **Record** on the call screen. The button shows **REC** while recording. Tap again to stop |
| Automatic | Turn on **Auto-record calls**. Recording starts when a call becomes active |

Recording stops when the call ends. Turning on **Auto-record calls** asks for the microphone permission first.

### Backends

**Phone settings > Call recording > Recording backend** (default **Auto**).

| Choice | Order tried | What it captures |
| --- | --- | --- |
| Auto (Shizuku, Root, microphone) | Shizuku, then root, then microphone | The best available |
| Shizuku (fallback to microphone) | Shizuku, then microphone | Call audio source through Shizuku |
| Root (fallback to microphone) <Badge type="info" text="untested" /> | Root, then microphone | Call audio source through root |
| Microphone only | Microphone | Only what the microphone hears |

With Shizuku or root, Telos grants itself the `CAPTURE_AUDIO_OUTPUT` appop with a shell command and then tries the
call sources *voice call*, *downlink* and *uplink*, falling back to the microphone sources (voice
communication, microphone, voice recognition, default). If a privileged start fails, the next option is used.

| Backend | Both sides? |
| --- | --- |
| Shizuku or root | Often, when the device and Android version allow it |
| Microphone | No. The other party can be faint or silent, even with the speaker on |

### Quality

| Preset | Bit rate | Sample rate |
| --- | --- | --- |
| Compact | 24 kbps | 16 kHz |
| Balanced (default) | 48 kbps | 16 kHz |
| High | 96 kbps | 44.1 kHz |

The format is AAC in an `.m4a` container.

### Storage, encryption and deletion

| Item | Detail |
| --- | --- |
| Folder | `CallRecordings` in Telos' private app storage. Other apps cannot read it |
| File name | `REC_<digits of the number>_<date and time>.m4a` |
| Encryption | After recording stops each file is encrypted with AES-256-GCM. The key stays in the Android Keystore. Older plain files are encrypted when the list is opened |
| Delete old recordings | **Never** (default), after 7, 30 or 90 days. It runs when the next recording **starts**, not on a schedule |

### The recordings list

**Phone settings > Call recording > Recordings** lists each recording with the number, date and size.

- **Tap** decrypts a temporary copy into the cache and opens it in an audio player app of your choice. The copy is
  removed again the next time the list is opened and is at least 10 minutes old.
- **Trash** deletes the file at once. There is no confirmation.

There is no share button in the list. To share a recording, open it in a player app and share from there; that
shares the temporary decrypted copy.

::: details Backing up recordings
Recordings are not part of the encrypted settings backup. They live in the app's private storage, so Android's own
backup or a root file manager is the only way to copy them. Because the key is tied to the device Keystore, a copy
on another phone cannot be decrypted.
:::

## Limitations

- Only the latest 100 calls are listed.
- Which audio source works depends on the device and Android version. Many phones block call audio for normal apps.
- The root backend is <Badge type="info" text="untested" /> on real devices.
- Recording needs the default dialer role. It does not record SIP calls.
- Recordings cannot be moved to another phone.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| Recents are empty | Allow the call log permission, and make Telos the default dialer for new calls to be logged |
| A swiped call disappeared | Swipe left deletes the log entry. Restore is not possible |
| Recording is silent or one-sided | Switch the backend. Microphone only picks up your side on many phones |
| "Cannot play recording" | No audio player app is installed that handles `audio/mp4`, or the Keystore key is gone after a reset |
| Auto-record does not start | Grant the microphone permission and check the call became active (answered) |
| Old recordings do not disappear | Deleting happens when a new recording starts |
