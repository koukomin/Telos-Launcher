# Telos Messages

Text and picture messages (SMS and MMS) as conversations with a reply field. It lives in the same app
as [Telos Phone](./phone) and can be opened from its messages tab.

## What you can do

| Feature | Details |
| --- | --- |
| Conversations | All messages with one person or group in a thread |
| Reply field | Reply directly in the conversation |
| Pictures | Shown in the conversation, a button attaches pictures |
| Group messages | Supported |
| Quick replies | Predefined answers |
| Scheduled SMS | Send a message at a chosen time |

## Default SMS app

Telos can be the phone's default SMS app. Android asks once.

| | Default SMS app | Not the default |
| --- | --- | --- |
| Stores received messages | Yes | No |
| Notifies about received messages | Yes | No |
| Sends from the message field | Yes | Replies with text only |
| Answers "reply with a message" from the call screen | Yes | No |
| Reads existing messages | Yes | Yes |

::: tip
Without being the default app Telos only **reads** messages and replies with text. The system's
messaging app still shows everything.
:::

## Share targets and links

"Telos Messages" appears in the share menu of other apps for a **text, picture or video**, and Telos
opens `sms:` links. The entry is only present while the app is installed (see [Store](./store)).

## Scheduled SMS

Messages are sent on the exact minute once **Alarms & reminders** is allowed in Android settings.
Without it, the time can be off.

## Hidden contacts

Conversations with hidden contacts are only listed while the hidden contacts are unlocked. See
[Phone privacy](./phone#privacy).

## Permissions

- SMS permissions (read, send, receive).
- Notification permission for new messages.
- "Alarms & reminders" for scheduled messages.

## Limitations

::: warning
- <Badge type="info" text="untested" /> MMS has not been tested on all devices and carriers.
- Not being the default app means no receiving, storing or notifying.
:::
