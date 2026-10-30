---
sidebar_position: 8
---

# Telos Linux Desktop (`:desktop-linux`)

`:desktop-linux` is a **standalone Compose for Desktop / JVM** module. It is registered in `settings.gradle.kts` and is **not** a dependency of `:app:app`. The Android APK does not ship Compose Desktop, cryptsetup helpers, or Linux icon scanners.

## Build

```bash
./gradlew :desktop-linux:packageUberJarForCurrentOS
./gradlew :desktop-linux:packageDeb
./gradlew :desktop-linux:packageRpm
./gradlew :desktop-linux:packageAppImage
```

Main class: `com.telos.desktop.MainKt`. The window is undecorated fullscreen.

## Vault

The Phantom Vault can sit on a single image or a folder of media carriers (stego shards +
`dmsetup` linear span), grow online with BTRFS zstd, use AES-NI `aes-xts-plain64` plus Argon2id,
and rclone-sync modified carriers on lock. Pre-boot offers host home, unlock, create, and tmpfs ghost modes.

## Vault (legacy image)

`LuksVaultManager` asks for a master password, then unlocks `~/.local/share/telos/telos_vault.img` with `cryptsetup` / `udisksctl` / `pkexec` and mounts it at `~/.local/share/telos/vault_mnt`. Desktop settings, state and databases are written only under that mount. A JVM shutdown hook syncs and locks the vault.

## Desktop shell

- Freedesktop `.desktop` scan and launch (`LinuxAppScanner`, `DesktopFileLauncher`)
- Icon themes with `Inherits=` fallback (`LinuxIconThemeResolver`)
- XDG user dirs and a live `WatchService` desktop icon grid
- Wayland/X11 display modes (`wlr-randr`, `kscreen-doctor`, `xrandr`)
- Klassy / SierraBreeze-inspired Compose window chrome and Sweet cursor presets

## Adaptive shell (proposal, implemented on Linux)

One Telos product, three chrome modes from the same settings store:

- **Phone (Android `:app:app`):** existing launcher; no Linux module in the APK.
- **Tablet:** top status bar + AppGrid (this desktop shell when width is tablet-sized).
- **Linux desktop:** KDE-style top panel, AppGrid with Telos + `.desktop` apps, floating windows.

Shared preferences (theme, icon pack, grid size, vault, display) live in the vault `desktop.properties` on Linux and Datastore on Android. They are not compiled together; names stay aligned so a future sync can copy keys.

## Windowed vs fullscreen

F11 and Settings toggle a normal resizable window vs undecorated fullscreen. When
`followSystemTheme` is on, accent/icon/cursor names are read from `~/.config/kdeglobals`
and GTK `settings.ini`. The left Telos candy/cyberpunk vector logo opens a Plasma-style AppGrid.

## Plasma replacement shell

The desktop shell includes a configurable top bar (default 36 dp; 32–48 dp, tint, alpha, icon size persist in `desktop.properties`). Right-click the bar for style. Work-area struts keep maximized windows below the bar. Telos Quake (`F12` / Ctrl+\`) and System Monitor (`Ctrl+Escape`) read `/proc` and `/sys`.

Window chrome: titlebar-only `awaitEachGesture` drag, 8-edge resize, minimize / maximize / close, double-click shade, edge snap (halves, corners, maximize). Geometry is Compose snapshot state so frames follow the pointer.

## File routing

`TelosFileHandler` opens directories in Telos Files and media in Telos Media (then `xdg-open` / MPRIS). Other types call `xdg-open`. Desktop icons, the dock shortcuts, and the Files list all use this path.

## Locale

Desktop chrome uses the **same string names** as the Android launcher (`:core:i18n`). Gradle copies `core/i18n/src/main/res/values*` XML into the UberJar. `TelosI18n` picks `values`, then `values-<lang>` for `Locale.getDefault()`. No second translation table. Window titles stay English because they are window-manager ids.

## Telos apps on Linux

Floating windows host Files, Photos, Downloads, Trash, Media Player, Notes, Calendar, Calculator, Clock, Network, Store, Settings, System Monitor, and System Optimizer. They are JVM Compose UIs with the same names as the Android virtual apps. They do not import `:app:ui`.

## Settings isolation

`PlatformEnvironment` / `SettingsVisibilityFilter` exist twice:

- Android: `app/ui/.../settings/platform/PlatformEnvironment.kt` — shows Shizuku, work profile, T9/SMS, wallpaper; hides LUKS, XDG, xrandr, Klassy, Sweet, Linux icon packs.
- Linux: `desktop-linux/.../platform/PlatformEnvironment.kt` — the inverse.

The two copies are intentional so `:app:app` never depends on `:desktop-linux`.

## labwc session (P0 / P1, Linux only)

`desktop-linux/session/telos-session` unlocks the vault (`java -jar … --unlock-only`), writes `TELOS_HOST_HOME` / `TELOS_VAULT_MOUNT`, exports **`$HOME` to the vault mount**, then starts labwc (not `exec`). On compositor exit it runs `--lock-only` (umount + nftables clear). Telos runs as `java -jar … --session --already-unlocked` and reattaches using the **host** home for the LUKS image, not the vault `$HOME`. Host apps are normal labwc windows. Optional FIDO2: `fido2-token` / `fido2-assert` mixed into the password. nftables table `inet telos_session` default-drops outbound except loopback, DNS, and IPv4/IPv6 CIDRs in `~/.config/telos/net-allow.txt` (`/0` and other invalid lines are ignored). If nftables cannot be applied, the session still starts and prints a warning. Panic (`Ctrl+Alt+L`) and logout also lock and ask labwc to exit.

Install `labwc`. `./install-telos-de.sh` copies the jar and session files to `/opt/telos-desktop`, installs `/usr/local/bin/telos-session`, and registers `/usr/share/wayland-sessions/telos.desktop`. Arm64 uses the same `packageUberJarForCurrentOS` on an aarch64 machine. `--session --already-unlocked` exits if the vault is not mounted. Empty unlock passwords are rejected. `packageUberJarForCurrentOS` copies `session/` next to the jar. Autostart reads `TELOS_JAR` from the environment (no sed). A second `telos-session` is refused via flock. Nesting labwc on an existing Wayland session needs `TELOS_ALLOW_NESTED=1`. `:desktop-linux:test` covers nftables allowlist parsing and shell quoting.

P2 (in progress): AppGrid **All** tab runs `DesktopSearch`. Store uses Flatpak. Files: XDG shortcut row, new folder, long-press to trash, click in Trash to restore. Media scans Music/Videos/Pictures (bounded) and keeps MPRIS transport. Remaining P2: archive/network Files. **P3, P4** still pending.
