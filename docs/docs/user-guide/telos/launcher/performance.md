# Performance

Telos has far more in it than a plain launcher: a phone app, players, a store, a file manager, overlays.
This page explains how it stays light, what you can tune, and what it costs. It is written from the build
files and settings screens, not from benchmarks: **no measured numbers are published**, and the baseline
profile is hand-written, not measured on a device.

## What you can tune

Settings > Advanced > **Performance** (summary: "Animations, search speed, caching").

## Graphics API (Vulkan / OpenGL)

Settings > Advanced, at the top of the screen. Forces Android’s HWUI renderer with `debug.hwui.renderer` (`skiavk` for Vulkan, `opengl` for OpenGL), the same switch used by [GAMA](https://github.com/palincat/gama). Telos does not copy GAMA code. Vulkan often uses less power; OpenGL is the fallback if Vulkan glitches. The **active renderer** is shown here and in Telos System.

Applying it needs **Shizuku**, **root**, or the **ADB** command on the screen (copyable). Android clears the property on reboot; **Restore after reboot** re-applies it when Shizuku or root is available. **Switch from System widget** (off by default) lets the home-screen System widget change Vulkan or OpenGL. If the screen freezes, hold Volume down and Power.

| Setting | Where | Default | Range | What it does |
| --- | --- | --- | --- | --- |
| Reduce animations | Performance > Animations | Off | On or off | Makes transitions instant and also switches the bounce off. Covers most, but not all, motion |
| Animation speed | Same | 1.0 | 0.5 to 2.0 (steps of 0.25) | Scales the duration of fade and effect motion. Only shown while reduce animations is off |
| Bounce physics | Same | No bounce | 0 to 100 percent | How much gesture release animations overshoot before settling |
| Search delay | Performance > Search | Off (0 ms) | 0 to 500 ms (steps of 50) | Wait this long after the last keystroke before searching |
| Icon cache size | Performance > Caching | 200 | 50 to 500 (steps of 50) | Number of icons kept in memory |
| **ART App Optimizer** | Performance > ART App Optimizer | Off | On or off | Pre-compiles Android apps using Shizuku or root (`cmd package compile -m speed -f`) |
| • Profile mode | Same | `speed-profile` | `speed-profile`, `speed`, `everything`, `--reset` | Target AOT compilation level |
| • Differential Audit | Same | Live scan | Auto | Categorizes apps into Up-to-Date, Needs Optimization, and Degraded |
| • Smart Optimization | Same | Default action | Button | Compiles only degraded and unoptimized apps to minimize time and NAND wear |
| • Skip & Strip Frozen Apps | Same | Off (`false`) | Off / Skip / Strip | Off keeps frozen apps compiled for instant launch; On can strip `--reset` binaries |
| • Profile Diagnostic | Same | Auto | Auto | Detects Baseline/Cloud profiles; auto-upgrades empty profiles to full `speed` |
| • Cold-Start Benchmark | Same | Per app | Button | Tests cold startup latency (`am force-stop` + `am start -W`) before and after |
| • SQLite Maintenance | Same | Off | Button | Executes `VACUUM; REINDEX;` to defragment internal SQLite databases |

Reading the table:

- **Search delay.** With 0 ms every keystroke starts a search. A value of 100 to 150 ms means that while you
  type fast, only the final text is searched. It saves work on slow devices and feels less instant on fast
  ones.
- **Icon cache size.** A larger cache means less re-decoding when you scroll back through results, at the
  cost of memory. The cache is resized live.
- **Background note.** The page itself points out that background updates (currency rates, weather) already
  wait for a network connection, and currency updates also wait until the battery is not low.

## Settings that cost something

Not in the Performance page, but worth knowing:

| Setting | Cost | Cheaper option |
| --- | --- | --- |
| [Video wallpaper](./home-screen#wallpaper) | Continuous decoding and drawing | Keep **Pause in Battery Saver** and **Pause on overheating** on (both default), or use a photo |
| Blur wallpaper | A blur effect over the wallpaper (on by default, radius 32) | Turn it off, or lower the radius |
| [Floating launcher](./desktop-and-overlays#floating-launcher), [Dynamic Island](./desktop-and-overlays#dynamic-island), [App Lock](./privacy-protection#app-lock) | Each is a foreground service with an overlay window, only while switched on | Leave off what you do not use |
| Online results as default filter | Network on every keystroke, see [Search](./search#online-results) | Leave online off by default |
| Many search sources | Each enabled source runs per query | Switch off sources you never use (Settings > Search) |
| Places, Wikipedia | Delayed by 250 and 750 ms so they do not run per letter | Already throttled |
| Notification badges | A notification listener | Switch off badges if not needed |

## How Telos keeps itself light

### Startup

On every process start the application class does a fixed amount of work:

1. Starts the dependency injection container with all feature modules (about 50).
2. Installs the crash guard exception handler, see [Privacy and protection](./privacy-protection#crash-guard).
3. Restarts the overlays that you have switched on (floating launcher, Dynamic Island, App Lock), only if the
   overlay permission is still granted.
4. Schedules the Store update check as one unique periodic job (safe to call on every start).
5. Runs the virtual app guard and the video settings mirror on a background dispatcher, not on the main
   thread.

What is **not** done at startup: decoders, the torrent engine and the SIP stack are created on first use,
and player services start only when something plays.

In the separate video player process the application class returns right away, with no dependency
injection and no settings store.

### Baseline profile

The file `app/app/src/main/baseline-prof.txt` tells Android which code to compile ahead of time, so the
first launch is faster. It is **hand-written** and covers the application class, the launcher UI, the
theme, the base UI package and the preferences. Compose and AndroidX ship their own profiles, which the
Android Gradle plugin merges in.

::: warning Not a measured profile
The file's own comment says to replace it with a profile generated by a Macrobenchmark module on a device.
There is no such module in the repository yet. Treat the profile as a reasonable default, not a tuned one.
:::

### Search work

- App search normalizes each app name once and caches the result per normalizer, then keeps only matches
  with a score of at least 0.8.
- Local file search stops after ten matches or 500 scanned rows. Contact search stops at about fifteen.
- Slow remote sources are delayed, and their results arrive as they finish.

### Apps that are switched off cost nothing

A Radio, Music, Video or Photos app that is hidden has its manifest components disabled and its service
stopped. See [Privacy and protection](./privacy-protection#idle-behavior).

## APK size choices

These are visible in the build files. The final size depends on the build, and none is promised here.

| Choice | Effect |
| --- | --- |
| One APK per CPU type: **arm64-v8a** and **armeabi-v7a** (`-Ptelos.abi`) | Each release APK has only the native libraries (and the torrent engine) of its own CPU, about half the native size of one APK with both. No x86 copies either |
| R8 minification and resource shrinking in release and nightly builds | Removes unused code and resources. Debug builds skip it |
| R8 full mode, with optimization enabled | Inlining, dead code removal and constant propagation (an old setting that disabled optimization was removed) |
| `Log.v` and `Log.d` calls stripped in release builds | Less code and no logging cost |
| R8 obfuscation is **on** in release and nightly builds | Shorter class and member names, so smaller DEX. Stack traces need the build's `mapping.txt` (kept as a CI artifact) and `retrace` to be read. `-Ptelos.keepNames=true` turns renaming off |
| One APK, two flavors (`default` and `fdroid`) | No separate APK per feature |
| Bouncy Castle pinned to one version | Avoids duplicated classes from different libraries |
| Bouncy Castle keep rule limited to its security providers | R8 removes the Bouncy Castle classes that neither Telos nor the providers use |
| Picnic lookup tables left out of the APK | About 1 MB of Bouncy Castle data files for a signature scheme Telos does not use |
| Only the translated languages are packaged (`localeFilters`) | Library translations for languages Telos does not support are dropped from `resources.arsc`, which is stored uncompressed |
| Minimum Android 8.0 (API 26) | One APK for every supported release |

::: tip Keep rules in mind
Libraries that use reflection or JNI (Shizuku, the SSH, SMB and FTP clients used by Telos Files, libtorrent4j,
firestack, Tor, baresip) have keep rules, which limits how much R8 can remove or rename.
:::

## Low-end device checklist

1. Settings > Performance: switch on **Reduce animations**, set **Search delay** to 100 or 150 ms.
2. Use a photo wallpaper (or none), turn off **Blur wallpaper**.
3. Switch off search sources and widgets you do not use, and keep **Online results** off by default.
4. Leave the floating launcher, Dynamic Island and App Lock off unless you need them.
5. Switch off Telos apps you do not use in the [Store](../store/). Their components are then disabled.
6. Use [Smart Freeze](../freeze/) for apps you rarely open.

## Limitations

- No benchmark numbers exist for startup time, memory or APK size.
- The baseline profile is a first guess.
- The application class still starts all feature modules eagerly.
- Some motion is not covered by **Reduce animations**.
