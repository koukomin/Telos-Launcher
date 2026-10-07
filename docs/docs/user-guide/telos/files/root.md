# Root explorer <Badge type="warning" text="root" /> <Badge type="info" text="untested" />

An optional superuser mode of Telos Files for system folders such as `/system`, `/data` and `/vendor`. It is off
until you switch it on, it is never needed for normal use, and it is <Badge type="info" text="untested" /> on many
devices. You need a rooted phone with a root manager such as Magisk or KernelSU.

::: danger Be careful
Changing system files can make the phone stop working, bootloop or erase your data. **Nobody can undo a deletion**,
there is no trash, and a wrong permission can stop the phone from starting. Telos shows several warnings on purpose.
Only use this mode if you know what you are doing, and keep a backup.
:::

## Turn it on

There are three ways in, all lead to the same dialog.

| From | Step |
| --- | --- |
| Folder view | **More > Root explorer...** |
| Storage drawer | **Tools > Root explorer** (it says "Needs a rooted phone") |
| A folder that cannot be opened | The error page has **Open with root explorer...** |

1. The **Root explorer** dialog explains that Telos Files will ask for superuser access to see and change files that
   apps normally cannot reach.
2. Tick **I understand the risks**. **Continue** stays disabled until you do.
3. Your root manager (Magisk, KernelSU and similar) asks you to allow Telos. Allow it.
4. If the grant works (the check runs `id` as superuser and expects `uid=0`) the mode is on. Otherwise a message says
   "Root access was not granted".

Turn it off with **More > Turn root explorer off**, or with the drawer entry "Root explorer: on, Tap to turn off".
Root access ends in Telos Files when you do that. The mode is also gone the next time the Files screen is created
anew, so you need to switch it on again. The grant in your root manager stays until you revoke it there.

## What changes while it is on

| Area | Behavior |
| --- | --- |
| Home page | The **Root file system** card is enabled. It opens `/` |
| Drawer | **Root file system (/)**, **Make /system writable** and **Make /system read-only** appear |
| File access | Every local path is read and changed through `su`, including `/storage`, so you see real owners and permissions |
| Listing | Uses `ls -lA` and shows permissions, owner and symbolic links. A link to a folder opens like a folder |
| Warning banner | A red bar "System files. Changes here can break the phone." is shown in every folder outside `/storage`, `/sdcard` and `/mnt/sdcard` |
| Search | The superuser `find` with at most 300 results |
| Size | Folder sizes come from `du` |
| Sharing and opening | A file only root can read is copied with `su cp` into the cache and made readable (`chmod 644`) for the other app |

### Operations in root mode

| Operation | Command used |
| --- | --- |
| New folder | `mkdir -p` |
| New file | `touch` |
| Rename, move | `mv` |
| Copy | `cp -a` (keeps permissions and links) |
| Delete | `rm -rf`. Selecting system files shows "This is a system location. Deleting files here can stop apps or the whole phone from working, and it cannot be undone." |
| Read a file | `cat` streams the file |
| Copy to or from another storage | Reading from root works. **Writing into root paths from remote storages or archives is not supported** |

All paths are quoted so names with spaces or quotes are safe.

## Change permissions

1. Select one entry, **More > Properties**.
2. In root mode the dialog adds **Change permissions (octal, for example 644)**.
3. Enter 3 or 4 octal digits (digits 0 to 7 only) and tap **Apply**.

A red note "This is a system location. A wrong permission can stop the phone from starting." shows for system
paths. The message "Permissions changed" or "Could not change the permissions" follows. Ownership (`chown`) cannot
be changed.

## Make /system writable or read-only

**Drawer > Tools > Make /system writable** (marked "Careful") runs `mount -o remount,rw /system`; **Make /system
read-only** remounts it `ro`. A message tells you the result, "/system is now writable" or "Could not remount
/system". Many devices refuse this because `/system` is on a read-only or dynamic partition. Always put it back to
read-only when you are done.

## Safety checklist

- Make a backup of what you plan to change, for example copy the file next to itself first.
- Do not delete or rename anything in `/system`, `/vendor`, `/data/system` or `/data/data` unless you know the
  consequence.
- Change one thing at a time and restart only when you are sure the phone can boot.
- Turn the root explorer off when you are done.

## Other root features in Telos

Root is optional everywhere. These other places use it when you choose it:

| Feature | Page |
| --- | --- |
| Call recording backend "Root" | [Recents and recording](../phone/recents-recording#backends) |
| Cellular network mode | [Calls](../phone/calls#cellular-network-mode) |
| Silent app installs in the Store | [Store sources and updates](../store/sources-updates#install-and-uninstall) |
| Freezing apps | [Freeze backends](../freeze/backends-profiles#backends) |

## Limitations

- <Badge type="info" text="untested" /> Behavior differs between root managers and Android versions.
- Listing relies on the output format of `ls -l`, so unusual entries may be skipped.
- No `chown`, no SELinux context editing, no mount of other partitions.
- Remote storages and archives cannot be written into root paths.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "Root access was not granted" | Allow Telos in your root manager, and check that `su` works |
| The mode switched itself off | It does not survive the screen being recreated. Turn it on again |
| A folder is still unreadable | The directory may be protected by SELinux or an encrypted partition |
| "Could not remount /system" | The partition is read-only on this device. Leave it as it is |
| Copy into a system folder fails | Copy to a local folder first, then move it as root |
