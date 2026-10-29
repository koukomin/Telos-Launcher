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

`LuksVaultManager` asks for a master password, then unlocks `~/.local/share/telos/telos_vault.img` with `cryptsetup` / `udisksctl` / `pkexec` and mounts it at `~/.local/share/telos/vault_mnt`. Desktop settings, state and databases are written only under that mount. A JVM shutdown hook syncs and locks the vault.

## Desktop shell

- Freedesktop `.desktop` scan and launch (`LinuxAppScanner`, `DesktopFileLauncher`)
- Icon themes with `Inherits=` fallback (`LinuxIconThemeResolver`)
- XDG user dirs and a live `WatchService` desktop icon grid
- Wayland/X11 display modes (`wlr-randr`, `kscreen-doctor`, `xrandr`)
- Klassy / SierraBreeze-inspired Compose window chrome and Sweet cursor presets

## Settings isolation

`PlatformEnvironment` / `SettingsVisibilityFilter` exist twice:

- Android: `app/ui/.../settings/platform/PlatformEnvironment.kt` — shows Shizuku, work profile, T9/SMS, wallpaper; hides LUKS, XDG, xrandr, Klassy, Sweet, Linux icon packs.
- Linux: `desktop-linux/.../platform/PlatformEnvironment.kt` — the inverse.

The two copies are intentional so `:app:app` never depends on `:desktop-linux`.
