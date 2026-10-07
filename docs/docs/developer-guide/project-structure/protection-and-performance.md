# Protection and optimization

The Telos apps (Phone, Messages, Radio, Music, Video, Photos, Store) are *virtual apps*: they live
inside the launcher process. That is cheap, but it also means a bug in one of them can take the
whole launcher down. This page describes what Telos does about that, and how it keeps these apps
from using memory and CPU while they are not in use.

## Crash guard

Code: `core/base/.../base/VirtualAppGuard.kt`, wired up in `LauncherApplication`.

- When Radio, Music, Video or Photos comes on screen, the guard writes a marker with the app's key
  (`enter`). When the screen is left normally, the marker is removed (`leave`).
- An uncaught exception handler, chained in front of the existing one, counts a crash for the app
  whose marker is set (`recordCrashIfOpen`) and then passes the exception on.
- Native crashes and ANRs cannot be caught that way. On the next start, if a marker is still set,
  the guard asks `ApplicationExitInfo` (Android 11+) whether the process died of a native crash or
  an ANR since the app was opened, and counts it.
- Two crashes within 24 hours switch the app off (`CommsSettings.setVirtualAppEnabled(key, false)`)
  and a notification points to the Store. Using an app for two minutes without a crash resets its
  counter. Installing it again from the Store resets it too.
- Phone and Messages are not guarded: they are default-role apps and must stay reachable.

## Exception containment

Code: `core/base/.../base/SafeCoroutines.kt`.

An exception that nobody catches in a coroutine ends the process. Background scopes of the Telos
apps (SIP, Radio service, network automation, Store update scheduler, call recording, call
screening, auto redial, SMS receiver, install receiver, network tile) are created with
`containedScope(dispatcher)`, a supervisor scope with a handler that logs the failure. The
scrobbler's network executor and the torrent server loop wrap their work in `contained(...)`.

## Nothing runs for apps that are switched off

When a Radio, Music, Video or Photos app is hidden (from the Store or by the crash guard),
`LauncherApplication` disables its manifest components with `setComponentEnabledSetting`
(`DONT_KILL_APP`) and stops its service. A disabled app

- uses no memory or CPU,
- cannot be started by another app, and
- does not appear in the "Open with" list.

Enabling it again restores the components.

## Idle behaviour of apps that are on

- Player services (Radio, Music) are started only when something plays and stop by themselves when
  playback ends.
- Heavy parts (FFmpeg decoders, the torrent engine, the SIP stack) are created on first use, not at
  launcher start. A torrent is stopped and its cache deleted when the player closes.
- The Store checks for updates with WorkManager (a periodic job), not with a service of its own.

## Not done yet

Everything above shares one process. Moving the video player, SIP and the photo editor into
separate processes would isolate them completely, but needs settings to be passed by intent and an
IPC layer for SIP. It is not implemented.
