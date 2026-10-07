# Update Problems

If installing a new Telos build fails with a message such as "App not installed" or "Package conflicts with an
existing package", the new APK is signed with a different key than the one that is installed. Android never
updates an app over one that was signed with another key.

## Why this happens

The builds on the [releases page](https://github.com/koukomin/Telos-Launcher/releases) are debug-signed test
builds. A debug build can be signed with a different debug key than an earlier one, for example when it is
built on a different machine. Telos Store has the same limit for the apps it updates: Android refuses an update that
is signed with another key, see [Store](../telos/store/).

## What to do

You cannot keep the data of the installed build and switch to a build with another signature in one step. Back up
first, then reinstall:

1. Open Settings > Advanced > **Backup and restore** and create a backup. It is a plain ZIP file with your
   settings, favorites, hidden items, names, icons, tags, themes, widgets and quick actions. It leaves out cloud
   logins, intruder photos, and the passwords and API keys of the Telos apps, so note those separately.
2. Uninstall Telos and install the new APK.
3. Open Settings > Advanced > **Backup and restore** > **Restore** and pick your backup.

::: warning
A backup from a different *major* format version cannot be restored, and one from a different *minor* version
restores with a warning. If a restore is refused, install the build that made the backup first, restore, and
then update.
:::

## Before you uninstall

Uninstalling removes everything the backup does not contain. The backup leaves out cloud logins, intruder photos,
the passwords and API keys of the Telos apps, and the hidden and protected call numbers, so write those down first.
On restore, secrets that are already on the device are kept.
