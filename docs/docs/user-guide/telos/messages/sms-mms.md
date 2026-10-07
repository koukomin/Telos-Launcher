# SMS and MMS in detail

How sending, receiving, pictures, group messages, share targets, quick replies, scheduled messages and
notifications work, with the numbers and limits taken from the code. For the screens and the default-app decision
see the [Messages overview](./).

## Getting started

1. Open **Phone > three-dot menu > Messages** and tap **Allow** to grant the SMS permissions.
2. To receive, store and be notified, tap **Set** in the banner and confirm Android's dialog.
3. Tap **New message**, enter a number and tap **Write**, or open an existing conversation.

## Sending

| Case | What happens |
| --- | --- |
| One recipient, text only | Sent with Android's SMS service. Long texts are split into several parts automatically |
| Default SMS app | The message is first written to the system message store as *outgoing*, then sent. The result (sent or failed) updates the entry |
| Not the default app | The system does not store what another app sends, so Telos keeps a private copy in its own files (newest 500). It appears in the conversation, and a conversation that only has sent messages is still listed |
| Several recipients | Sent as a group MMS. Needs the default SMS app, otherwise "The message could not be sent." |
| With attachments | Sent as MMS. Needs the default SMS app |
| No SEND_SMS permission | The reply field fails; the quick-reply and scheduled-SMS paths open your messaging app with the text prefilled instead |
| Empty text and no attachment | The **Send** button stays disabled |

Telos does not choose a SIM for text messages; Android's default SMS SIM is used. Delivery reports are not shown.

## Receiving

As the default SMS app:

1. Android delivers the SMS to Telos. It is written to the system inbox as unread, with the original send time kept.
2. A notification is posted (see [Notifications](#notifications)).
3. The conversation appears in the list.

An MMS arrives in two steps. A *push message* announces it, then Telos asks Android's phone service to download it
over mobile data. The result is read, stored in the system message store and notified. If the download fails, the
announcement is dropped and nothing is stored.

Not the default app: nothing is received or stored by Telos, and no notification is posted. The system messaging app
does all of it. Telos only reads the system database when you open the tab.

## Attachments and limits

### Pictures and videos

- The **+** button next to the reply field opens the Android picker for **pictures** (multiple selection). Only the
  default SMS app has this button.
- Pictures from the share menu and videos are accepted as attachments too. Other file types shared to Telos Messages
  are shown as `[type]` and are not sent.

### Size limits for MMS

Carriers only accept small messages, so Telos shrinks pictures before sending.

| Item | Behavior |
| --- | --- |
| Pictures | Scaled so the longer side is at most about 1600 pixels, then saved as JPEG with quality 85, lowered in steps down to about 25, until the picture is under 600 KB |
| Videos and other files | Sent unchanged, but a file of **more than about 2.4 MB is silently left out** of the message |
| Text | Sent as a text part together with the attachments |
| Transfer | Android's phone service sends it with the carrier's MMS settings (APN) over mobile data |

::: warning A big video may just disappear
If every attachment is too large and there is no text, the message is not sent. If only some are too large, the
rest is sent without telling you which part was dropped.
:::

### Shared files

"Telos Messages" appears in the share menu of other apps for a **text, picture or video** (single or several).
Telos copies the shared files into a temporary cache before the other app's permission expires:

- up to **10 files**, each under **25 MB**,
- the copies are cleaned up after about an hour,
- the entry exists only while the app is installed in [Store](../store/).

## Links with a prefilled text

Telos opens `sms:`, `smsto:`, `mms:` and `mmsto:` links. An `sms:` link can carry a number and a text, as
`sms_body` or `?body=`. Telos opens the Messages tab with the number and text already filled in. Nothing is sent
until you tap **Send**. If no conversation with the number exists, an empty one is opened.

## Quick replies

| Where | What | Needs |
| --- | --- | --- |
| Incoming call screen, **Reject + SMS** | Declines the call and sends the template | `SEND_SMS` |
| Missed-call popup, **Message** | Sends the same template to the caller | `SEND_SMS` |
| Other apps' "reply with a message" | Handled by the respond-via-message service of Telos | Telos is the default SMS app |

The template is set in **Phone settings > Calling > Reject with SMS**. Default text: "I'll call you back". Maximum
160 characters. Leave it empty to turn it off.

## Scheduled SMS

**Phone settings > Tools > Scheduled SMS**.

1. Enter the **Number** and the **Message**.
2. Tap **Pick time and save**, choose the date, then the time.
3. The message appears in the list below with **Cancel**.

| Topic | Behavior |
| --- | --- |
| Exact time | With "Alarms & reminders" allowed the message goes out on the chosen minute. The screen has an **Allow** button. Without it the system can delay it by a few minutes |
| Phone off or restarted | The alarms are set again at boot. A message that was overdue goes out about 5 seconds after boot |
| Failure | If sending fails (for example the SMS permission is missing), the message stays in the list instead of vanishing |
| Storage | The list is kept in Telos' private preferences, in plain text |
| Limits | Text only, one recipient, no recurring messages |

## Notifications

As the default SMS app Telos posts a notification on the *Messages* channel (high importance) for each received
message. There is one notification per sender; a new text from the same sender replaces the previous one. Tapping
it opens the Messages tab, not the single conversation. The notification permission is needed on Android 13 and
later. When Telos is not the default app it posts none.

A message from a **hidden contact** is announced as "New message" without the sender and without the text.

## Privacy

| Topic | Behavior |
| --- | --- |
| Hidden contacts | A conversation with a hidden number is not listed while hidden contacts are locked. See [Phone privacy](../phone/privacy-screening#hidden-contacts) |
| Display filter only | The message is still received and stored by Android. The system messaging app may show it and notify you |
| Upload | None. MMS transfer is done by Android with your carrier |
| Shared files | Copied to the cache and removed after about an hour |
| MMS working files | Written to the cache for the transfer and shared with Android's phone service only |

## Limitations

- <Badge type="warning" text="experimental" /> MMS is untested across carriers.
- No delivery reports, no SIM choice, no draft saving.
- Non-picture attachments cannot be added from the app.
- Videos over about 2.4 MB are dropped from a message.
- No search, delete or archive.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "The message could not be sent." | No signal, a group or MMS without being the default app, or missing SMS permission |
| "not sent" under a message | The carrier or phone service reported a failure. Send again |
| A picture arrives small | Pictures are shrunk to about 600 KB and 1600 pixels |
| A video did not arrive | It was larger than about 2.4 MB and was left out |
| No notification for new texts | Telos is not the default SMS app, or notifications are off |
| An MMS never downloads | Mobile data is off or the carrier's MMS settings (APN) are missing |
| Scheduled message is late | Allow "Alarms & reminders" for Telos |
| Shared picture is missing | Telos copies at most 10 files of under 25 MB each |
