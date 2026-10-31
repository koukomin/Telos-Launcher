# Protection and optimization

The Telos apps (Phone, Messages, Radio, Music, Video, Viewer, Store) are *virtual apps*: they live
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
- Heavy parts (FFmpeg decoders from official NextLib, the torrent engine, the SIP stack) are created on first use, not at
  launcher start. A torrent is stopped and its cache deleted when the player closes.
- `MediaDecoderPack` in `:services:comms` builds Media3 renderers with NextLib (`io.github.anilbeesetti:nextlib-media3ext`).
  Music, Radio, YouTube, TV and Video all use it. Playback stays in the Telos UI.
- The Store checks for updates with WorkManager (a periodic job), not with a service of its own.

## The video player in its own process (experimental)

Settings, Video services, "Play in a separate process" starts videos opened from Telos in
`IsolatedVideoPlayerActivity`, which the manifest runs in the `:player` process. A crash of a
decoder or of the torrent engine then ends that process, not the launcher.

- `LauncherApplication.onCreate` returns right away in that process (`ProcessInfo.init`): no Koin,
  and the settings store is never opened by two processes.
- The video settings are copied to `files/video_services.json` by the main process (keys and
  passwords stay encrypted); the player reads that file (`VideoServices.config`).
- Watch positions and Trakt scrobbles are sent back to the main process with a broadcast
  (`PlayerBridge`), because it owns the stored data.
- The player process ends itself when the player is closed, so every start reads fresh data.
- Videos opened from other apps (`ACTION_VIEW`) always use the normal player. The switch is off by
  default.

## Not done yet

SIP and the photo editor still run in the launcher process. SIP would need a service with an
interface between the processes.

## Runtime dispatchers, bitmaps, and ART baseline profiles

- `TelosDispatchers.Icon` and `TelosDispatchers.Disk` (`core/base/.../coroutines/TelosDispatchers.kt`) cap icon baking and file/database work at four threads each (`Dispatchers.Default` / `Dispatchers.IO` with `limitedParallelism(4)`).
- Opaque wallpaper glass and media thumbnails decode as `RGB_565` and blur after `inSampleSize = 8`. Adaptive icons keep `ARGB_8888`.
- Package metadata is read from `TelosPackageRepository` on the disk dispatcher; Compose grids use cached labels/icons. `PackageManager` / `LauncherApps` Binder calls stay off the UI thread during scrolling.
- `app/app/src/main/baseline-prof.txt` plus `androidx.profileinstaller` AOT-compile `de.mm20.launcher2` and `com.telos` launcher classes on install.

## Stealth cloak and privileged daemon

Code: `services/app-management/.../stealth/` (`TelosPrivilegedDaemon`, `TelosStealthBlacklist`, `TelosDevOptsCloak`), UI in Settings → Advanced → Stealth and banking cloak.

- `TelosPrivilegedDaemonService` is not exported. Binder callers must be the Telos UID (`Binder.getCallingUid() == Process.myUid()`).
- Commands run through the existing Telos `ShizukuCommandService` UserService (Telos package name, shell UID 2000) or Shizuku/root fallback. That UserService is the internal daemon; the `moe.shizuku.privileged.api` UI can be hidden after it is bound.
- Before a protected app is launched from the launcher, `AppLaunchGate` cloaks `development_settings_enabled`, `adb_enabled` and `adb_wifi_enabled`. With `WRITE_SECURE_SETTINGS` this is a Java `Settings.Global.putInt`. Returning to the home screen restores the previous values unless permanent stealth is on.
- The seeded package list covers Greek banks/gov, common fintech, Play/GMS/Intune/Authenticator, and several anti-cheat titles. Heuristics match bank/wallet/pay/gov/intune/authenticator in the package name or label. Users can toggle any installed app from search.

## Shizuku manager and clipboard shield

Code: `services/app-management/.../shizuku/TelosShizukuManager.kt`, `.../clipboard/TelosClipboardShield.kt`, UI `TelosSecurityHubScreen`.

- Client scan uses `GET_PERMISSIONS` and `GET_META_DATA` for `moe.shizuku.manager.permission.API_V23`, `rikka.shizuku.permission.API_V23`, and `moe.shizuku.client.V3_SUPPORT`.
- Package enable/disable, `pm grant`/`revoke`, `am force-stop`, and `cmd appops set READ_CLIPBOARD` run on `TelosDispatchers.Disk` through `TelosPrivilegedDaemon` / `TelosShellEngine`.
- Pre-launch clipboard work stays on the launch path (ClipboardManager). Pattern matching is compiled once. The vault is process memory only.
- `AppLaunchGate.preLaunch` chains cloak then clipboard scrub; `onHomeResumed` restores cloak flags and optionally the vault.

## ART App Optimizer and Dex2Oat Architecture

The ART App Optimizer subsystem resides in `:services:app-management` (`de.mm20.launcher2.appmanagement.art`) with UI in `:app:ui`:

- **StartupOptimizationAudit (`StartupOptimizationAudit.kt`):** A streaming parser for `dumpsys package dexopt` (with fallback to `dumpsys package`) and batched `stat -c '%Y|%n'` `.odex` modification checks. Categorizes packages into `UP_TO_DATE`, `NEEDS_OPTIMIZATION`, `DEGRADED` (e.g. newly updated or reset), and `FROZEN_SKIPPED`.
- **TelosArtOptimizer (`TelosArtOptimizer.kt`):** Privileged execution coordinator supporting Shizuku (`ShizukuProcesses`) and root (`su`). Runs `cmd package compile -m <profile> -f <package>` with streaming pipes (180s bounded timeout) to prevent pipe blocking. Handles `optimizeSmartPackages`, `optimizeAllPackages`, and `stripFrozenPackages`.
- **Frozen Apps Synergy:** By default, frozen and disabled apps are included in bulk optimization (`skipFrozenApps = false`) so launcher unfreeze-to-launch transitions are instant. If the user opts into `skipFrozenApps = true`, apps can either be skipped (`skip`) or stripped of `.odex` binaries (`strip_reset`).
- **ProfileDiagnostic:** Inspects `assets/dexopt/baseline.prof` in the base APK, `.dm` Cloud Profile files, and `/data/misc/profiles/{ref,cur}/.../primary.prof`. If empty (header $\le 64$ bytes), it auto-upgrades `speed-profile` to full `speed` (`artFallbackEmptyProfileToSpeed = true`).
- **AppLaunchBenchmark:** Cold-start benchmark executing `am force-stop` followed by `am start -W -n <component>`, parsing `TotalTime` and `WaitTime` to measure real-world performance gains.
- **SqliteVacuumEngine (`SqliteVacuumEngine.kt`):** Executes SQLite `VACUUM; REINDEX;` on internal Telos Room databases and external app databases when privileged access is available.
- **Background Worker (`ArtOptimizationWorker.kt`):** Periodic WorkManager job adhering to user constraints (charging, idle, battery $> 30\%$, power saver off).



## Wallpaper contrast

`ui/theme/ContrastPolicy.kt` contains the pure sRGB/WCAG black/white policy;
`WallpaperSamples.kt` selects a conservative region from a small extrema grid.
`WallpaperReadability.kt` loads permitted static wallpaper files on IO, gates legacy APIs,
checks the viewport, caches one result and observes resume/wallpaper changes. It never requests
additional permissions. Failed reads, live wallpapers, blur/dim and ambiguous crops use a scrim.
`WallpaperContentStyle` attaches geometry and backing to wallpaper content; `LocalOnWallpaper`
is scoped to clock/home content and reset by shared cards.

`LauncherCard` propagates composited surface ranges and resolved content colors to nested cards.
Tonal overlays are disabled there so the surface does not change after the contrast decision.
`TransparencyScheme` preserves explicit stored values; only missing values and the built-in
Default scheme use 0.80. Existing clock, custom-label and third-party widget styling remains an
explicit limitation. Unit tests live in `ui/theme/ContrastPolicyTest.kt` and run with
`./gradlew :app:ui:testDefaultDebugUnitTest --tests '*ContrastPolicyTest'`.

Device review should cover bright/dark/checkerboard static wallpapers, denied permissions,
Android 13+, live/video wallpapers, blur/dim, forced white/black, clock/theme-color overrides,
custom labels, both widget-background settings, custom transparency (0/0.8/1), drawer transitions,
rotation, split screen, changing wallpapers with the same palette and returning from the picker.
