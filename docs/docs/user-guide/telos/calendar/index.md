# Telos Calendar

A month and agenda calendar on the calendars of your phone, with a local calendar and sync through your accounts.

::: tip At a glance
Month view with an agenda of the chosen day, a local calendar that needs no account, Google, CalDAV and Exchange calendars
through the system, repeating events and reminders, import and export of `.ics`. Local calendars are part of the Telos backup.
:::

## What it is

Telos Calendar is a [virtual app](../#how-the-built-in-apps-work). It does not keep its own database. It reads and writes the
**calendar storage of Android**, the same one the system calendar and Telos search use. So events you add show up in the
other calendar apps and in calendar search, and events from your accounts show up here. Switching it off in
[Telos Store](../store/) hides its icon.

The first time it asks for the **calendar** permission (read and write).

## Using it

| Part | What it does |
| --- | --- |
| Arrows, **Today** | Change the month, jump to today |
| A day | Shows its events below. Dots under a day are the colours of its calendars |
| **+** | New event: title, all day, start and end, location, description, repeat, reminder, calendar |
| An event | Tap to edit or delete. Only calendars you may write to can be edited |
| Menu > **Calendars** | Show or hide each calendar, create or delete a local calendar |
| Menu > **Sync accounts now** | Asks the system to sync your accounts |
| Menu > **Add account** | Opens the Android screen to add a Google, CalDAV or Exchange account |
| Menu > **Import** and **Export** | Read an `.ics` file into a calendar, or write the visible calendars to one |

Repeats are every day, week, month or year. Editing or deleting a repeating event changes the whole series. A reminder is a
notification of the calendar storage (minutes before the start).

## Local and remote calendars

- **Local:** a calendar of the type "on this phone", created from the Calendars dialog (or automatically if you import into an
  empty phone). It never leaves the phone.
- **Google:** add your Google account in Android settings. The system sync adapter of Google Play services syncs it, Telos
  shows and edits the events.
- **CalDAV (Nextcloud, Radicale, Fastmail, iCloud and others):** install a sync app such as DAVx5 and add the account there.
  The calendars then show up in Telos Calendar.
- **Exchange and Outlook.com:** add the account in Android settings.

Telos does not log in to these services itself, the system or the sync app does. That is why sync follows the system's rules
(for example, background data and battery restrictions).

## Backup

Local calendars are the **Calendar** part of the Telos backup (Settings > Advanced > Backup and restore) and are written as
`.ics`. Calendars of accounts are not in the backup because the account has them. On restore, events that are already in the
calendar (same title and start) are not added twice. See [Backup and restore](../launcher/privacy-protection#backup-and-restore).

## Limitations

- No week or day grid, only the month and the agenda
- No guests, no video links, no tasks, one reminder per event
- Editing one occurrence of a repeating event is not possible, only the whole series
- Sync is done by the account's sync adapter, so Telos cannot show a sync error
