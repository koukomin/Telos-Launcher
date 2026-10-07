# Telos Messages

Telos Messages shows text messages (SMS) and picture messages (MMS) as conversations with a reply field. It is
part of [Telos Phone](../phone/): open Phone and choose **Messages** from the three-dot menu at the top. Opening
an `sms:` link, or sharing something to "Telos Messages", jumps straight to that tab.

::: warning MMS is experimental
MMS (pictures, videos, group messages) <Badge type="warning" text="experimental" /> <Badge type="info" text="untested" />
has not been tried on many devices and carriers. Sending and receiving pictures depends on your carrier's settings.
:::

::: info This section
- This page: the screens, permissions, the default SMS app decision and the feature matrix.
- [SMS and MMS](./sms-mms): sending, receiving, attachments, share targets, quick replies, scheduled SMS,
  notifications, storage and limits.
:::

## A tour of the screens

### Conversation list

| Element | What it does |
| --- | --- |
| Banner "Make Telos your SMS app ..." with **Set** | Shown while Telos is not the default SMS app. **Set** opens Android's confirmation |
| **New message** button | Asks for a number, then opens the conversation. An existing conversation with that number is reused |
| A conversation row | Contact name or number, a snippet of 2 lines, the date. Unread conversations are bold |
| Empty state | "No conversations. Your messages appear here." |
| Back arrow | Returns to Recents |

If the SMS permissions are missing, the tab shows "Allow Telos to read and send text messages to see and answer
them here." with an **Allow** button.

### A conversation

| Element | What it does |
| --- | --- |
| **Back** and the name | Return to the list. The name is the contact, or the number |
| Message bubbles | Your messages at the right, theirs at the left, newest at the bottom. Pictures and videos are shown inline |
| "not sent" next to the date | The message failed. A red "The message could not be sent." appears after a failed attempt |
| `[type]` | An attachment that is not a picture or video is shown as its file type, for example `[application/pdf]` |
| **+** | Attach pictures. Only as the default SMS app |
| "N attached" with **Remove** | Appears after attaching or sharing into Messages |
| Reply field and **Send** | Sends a text. With attachments, or more than one recipient, it is sent as MMS |

Opening a conversation marks it read.

## Permissions

| Needed for | Permission | When |
| --- | --- | --- |
| Reading and sending | SMS (`READ_SMS`, `SEND_SMS`) | Asked when you tap **Allow** |
| Receiving SMS and MMS | `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Used when Telos is the default SMS app |
| New message notifications | Notifications | Android 13 and later |
| Scheduled messages on the exact minute | "Alarms & reminders" | Optional, see [SMS and MMS](./sms-mms#scheduled-sms) |
| Pictures from the picker | The normal Android picker, no extra permission | |

## Default SMS app or not

Android allows exactly one default SMS app. Telos asks once, and you can give the role back at any time in
Android's default apps settings.

| | Telos is the default SMS app | Not the default |
| --- | --- | --- |
| Reads existing messages | Yes | Yes |
| Stores received messages | Yes | No, the system app does |
| Notifies about received messages | Yes | No |
| Sends from the message field | Yes, into the system message store | Text only, through Android's SMS service. A private copy is kept in Telos (up to 500) |
| Sends pictures and MMS, group messages | Yes | No |
| Answers other phone apps' "reply with a message" requests | Yes | No |
| Receives `sms:` links and shared files | Yes | Yes, but the system may offer another app |

::: tip
If you try Telos Messages next to another app, leave the other one as the default. Telos then only reads, and the
system's own messaging app keeps everything.
:::

Because the system database is shared, switching the default SMS app back to another app later does not lose
your history.

## How it works

| Part | Role |
| --- | --- |
| Messages tab | Lists conversations, shows threads, sends replies |
| System SMS database | Where messages are stored. Telos reads it and, as default app, writes to it |
| Phone's messaging service | Does the actual SMS and MMS network transfer |
| Compose entry (`ComposeSmsActivity`) | Receives `sms:` links and shared content and forwards them to the Messages tab |
| Receivers | SMS deliver, WAP push (MMS announcements), MMS download and sent results |
| Respond-via-message service | Lets a phone app send a quick reply through Telos when Telos is the default SMS app |

## Feature matrix

| Feature | Status | Needs default SMS app | Where |
| --- | --- | --- | --- |
| Read conversations | stable | no | Messages tab |
| Send a text | stable | no | Reply field |
| Receive, store and notify | stable | yes | |
| Send and receive pictures, videos (MMS) | <Badge type="warning" text="experimental" /> | yes | **+** button, notifications |
| Group messages | <Badge type="warning" text="experimental" /> | yes | Reply field with several recipients |
| `sms:` / `smsto:` / `mms:` / `mmsto:` links | stable | no | Compose entry |
| Share text, pictures, videos into Telos Messages | stable | no (attachments need yes) | Share menu |
| Prefilled text from a link | stable | no | `sms_body` or `?body=` |
| Reject with SMS | stable | no (needs the SMS permission) | Phone settings > Calling |
| Scheduled SMS | stable | no | Phone settings > Tools |
| Hidden contacts stay out of the list | stable | no | Phone settings > Privacy |
| Search, delete, mute or archive conversations | not available | | |
| RCS, end-to-end encryption | not available | | |

## Where each setting lives

| Item | Location |
| --- | --- |
| Default SMS app | Messages tab, banner with **Set** |
| Reject with SMS template | Phone settings > Calling > Reject with SMS (up to 160 characters, default "I'll call you back") |
| Scheduled SMS | Phone settings > Tools > Scheduled SMS |
| Hidden contacts and PIN | Phone settings > Privacy |
| Secure call screen, phone lock | Phone settings > Privacy and Incoming call |
| Enable or disable the app | [Telos Store](../store/) |

Messages has no settings screen of its own.

## Limitations

- <Badge type="info" text="untested" /> MMS across devices and carriers.
- Not the default app: no receiving, storing or notifying, no MMS, text replies only.
- Attachments: only pictures can be attached from the app; videos and pictures can be shared into it.
- There is no way to delete, search or archive messages in Telos; use another app or the system messaging app.
- No RCS and no end-to-end encryption; this is plain SMS and MMS.
- The conversation list shows the messages Android returns for each thread, with MMS limited to the newest 200 of a
  thread.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| No messages listed | Tap **Allow** and grant the SMS permissions |
| New messages do not appear or notify | Make Telos the default SMS app; otherwise the system app receives them |
| Cannot attach a picture | The **+** button is shown only for the default SMS app |
| Picture message fails | Check mobile data and the carrier's MMS (APN) settings. Pictures are shrunk, see [SMS and MMS](./sms-mms#attachments-and-limits) |
| Scheduled message is late | Allow "Alarms & reminders" for Telos |
| Messages from a number are missing | The contact may be hidden. Unlock hidden contacts |
| Telos Messages is missing from the share menu | Install Messages again in [Store](../store/) |

::: details Is anything sent to Telos servers?
No. The messaging code reads and sends through Android's SMS and MMS services and has no upload feature. Messages
move only between your phone, the system SMS service and your carrier.
:::
