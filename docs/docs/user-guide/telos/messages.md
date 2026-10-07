# Telos Messages

Telos Messages shows text messages (SMS) and picture messages (MMS) as conversations with a reply field.
It is part of [Telos Phone](./phone): open Phone and choose **Messages** from the overflow menu at the
top. Opening an `sms:` link or sharing something to "Telos Messages" jumps straight to that tab.

::: warning MMS is untested
MMS (pictures, videos, group messages) <Badge type="warning" text="untested" /> has not been tried on
many devices and carriers. Sending and receiving pictures depends on your carrier's settings.
:::

## Getting started

1. Open the Messages tab and tap **Allow** to grant the SMS permissions (read and send).
2. To receive, store and notify, make Telos your default SMS app: tap **Set** in the banner. Android
   shows its normal confirmation once.
3. Tap **New message**, enter a number and tap **Write**, or open an existing conversation.

### Permissions

| Needed for | Permission |
| --- | --- |
| Reading and sending | SMS (read, send) |
| Receiving SMS and MMS | SMS and MMS receive permissions (used when Telos is the default app) |
| New message notifications | Notifications |
| Scheduled messages sent on time | "Alarms & reminders" |
| Pictures from the picker | Normal Android picker, no extra permission |

## Default SMS app or not

| | Default SMS app | Not the default |
| --- | --- | --- |
| Reads existing messages | Yes | Yes |
| Stores received messages | Yes | No, the system app does |
| Notifies about received messages | Yes | No |
| Sends from the message field | Yes | Text only (sent through Android's SMS service) |
| Sends pictures and MMS | Yes | No |
| Answers "reply with a message" from the call screen | Yes | No |

::: tip
If you try Telos Messages next to another app, leave the other one as the default. Telos then only
reads. The system's own messaging app keeps everything.
:::

## How it works

| Part | Role |
| --- | --- |
| Messages tab | Lists conversations, shows threads, sends replies |
| System SMS database | Where messages are stored; Telos reads it and, as default app, writes to it |
| Phone's messaging service | Does the actual SMS and MMS network transfer |
| Compose entry | Receives `sms:` links and shared content and forwards them to the Messages tab |

Because the system database is shared, switching the default SMS app back to another app later does not lose
your history.

## Features

### Conversations

All messages with one person or group form a thread, newest first, with unread threads in bold, a
snippet and the date. Opening a thread shows the messages and a reply field with a **Send** button. If
sending fails, the thread shows "The message could not be sent."

### Pictures and group messages

As the default SMS app, the **+** button next to the reply field attaches pictures from the picker; they are
shown in the conversation (other attachments appear as their file type). Group messages are supported.
Telos hands the file to the phone's own messaging service, which does the network transfer over
your carrier's mobile data.

### Share targets and links

"Telos Messages" appears in the share menu of other apps for a **text, picture or video**, and Telos opens
`sms:`, `smsto:`, `mms:` and `mmsto:` links. Shared files are copied into a temporary cache (up to 10
files, each under 25 MB) and cleaned up after about an hour. The entry only exists while the app is
installed in [Store](./store).

### Links with a prefilled text

An `sms:` link can carry a number and a text (`sms_body` or `?body=`). Telos opens the Messages tab with
the number and text already filled in; nothing is sent until you tap **Send**.

### Quick replies

- **Reject with SMS:** the message sent when you decline a call (Phone settings > Calling > Reject with SMS).
- Other apps' "reply with a message" requests are answered by Telos only as the default SMS app.

### Scheduled SMS

Phone settings > Tools > **Scheduled SMS**: enter number, message, pick a date and time. Messages go out
at the chosen minute once **Alarms & reminders** is allowed (the screen has an "Allow" button). Without
it, a message may be a few minutes late. Scheduled messages are restored after a reboot.

## Notifications

As the default SMS app Telos posts a notification for each received message. Notification
permission is needed on recent Android versions. When Telos is not the default app it posts none.

## Privacy

### Hidden contacts

Conversations with hidden contacts are only listed while hidden contacts are unlocked (see
[Phone privacy](./phone#privacy-and-security)).

::: warning This is a display filter
Telos hides the conversation from its own list. It does not remove the message from Android. A text from
a hidden number is still received and stored by the system, and the system messaging app may show it
and notify you. Do not rely on it as a secret vault.
:::

### Data handling

Telos Messages reads and writes the system SMS database like any messaging app. It does not upload
messages anywhere. MMS transfer is performed by Android with your carrier.

## Where each setting lives

| Item | Location |
| --- | --- |
| Default SMS app | Messages tab, banner with **Set** |
| Reject with SMS template | Phone settings > Calling > Reject with SMS |
| Scheduled SMS | Phone settings > Tools > Scheduled SMS |
| Hidden contacts and PIN | Phone settings > Privacy |
| Secure call screen, lock | Phone settings > Privacy and Incoming call |

## Limitations

- <Badge type="warning" text="untested" /> MMS across devices and carriers.
- Not the default app: no receiving, storing or notifying, no MMS, text replies only.
- Attachments: only pictures can be attached from the app; videos and pictures can be shared into it.
- No RCS, and no end-to-end encryption; this is plain SMS and MMS.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| No messages listed | Tap Allow and grant the SMS permissions |
| New messages do not appear or notify | Make Telos the default SMS app; otherwise the system app receives them |
| Cannot attach a picture | The **+** button is shown only for the default SMS app |
| Picture message fails | Check mobile data and the carrier's MMS (APN) settings; MMS is untested |
| Scheduled message is late | Allow "Alarms & reminders" for Telos |
| Messages from a number are missing | The contact may be hidden; unlock hidden contacts |
| Telos Messages is missing from the share menu | Install Messages again in [Store](./store) |

::: details Is anything sent to Telos servers?
The messaging code reads and sends through Android's SMS and MMS services and has no upload feature. Messages move only
between your phone, the system SMS service and your carrier.
:::
