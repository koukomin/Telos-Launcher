---
sidebar_position: 40
---

# Telos Linux Desktop

Telos Linux Desktop is a **separate JVM program** (`:desktop-linux`). It is not inside the Android APK. Unlock the Phantom Vault, then you get a KDE-style top bar, AppGrid, floating Telos apps, and a host `.desktop` launcher.

## What it does

| Feature | Behaviour |
| --- | --- |
| Top bar | Workspaces 1–4, clock, audio, network, battery, tray, clipboard, notifications. Right-click or long-press opens style (height, icon size, tint, transparency). Choices save in vault `desktop.properties`. |
| AppGrid | Telos logo on the bar opens the full-screen grid: Telos apps and installed Linux apps. |
| Windows | Titlebar drag, 8-edge resize, minimize / maximize / close, shade (double-click titlebar), edge snap. Maximized windows stay below the top bar. |
| Files / Media | Folders and media open in Telos Files / Telos Media first. Other types use `xdg-open`. |
| Telos apps | Files, Photos, Downloads, Trash, Media, Notes, Calendar, Calculator, Clock, Network, Store, Settings, System Monitor, System Optimizer. |
| Quake | `F12` / Ctrl+\` drop-down terminal. |
| System Monitor | `Ctrl+Escape`, reads `/proc` and `/sys`. |

Language follows the JVM locale. Labels are the same keys as the Android launcher (`core/i18n`): Files, Settings, Search, Play, and so on already translated there. The desktop module does not add a second set of translations.

## Build and run

```bash
./gradlew :desktop-linux:packageUberJarForCurrentOS
java -jar desktop-linux/build/compose/jars/telos-desktop-linux-x64-1.0.0.jar
```

Install to `/opt/telos-desktop/telos-desktop.jar` with `sudo` when you want the session launcher to pick it up.

This module never ships in `:app:app`.

## Encrypted session (labwc)

On Linux you can start a full session instead of a window on another desktop:

1. Install `labwc` and a JRE 21.
2. Build the UberJar.
3. Run `desktop-linux/session/telos-session` (or pick **Telos** in the display manager after installing `telos.desktop`).

The vault password (and optional FIDO2 key) unlocks an encrypted volume that **becomes `$HOME`** for that session. The LUKS image stays under the **host** home (`TELOS_HOST_HOME`); only session files use the vault mount. Outbound network is denied by default except DNS and IPv4/IPv6 CIDRs in `~/.config/telos/net-allow.txt`. Leaving the session locks the vault and removes the `inet telos_session` nftables table. `Ctrl+Alt+L` panic-unmounts and ends labwc.

`./install-telos-de.sh` installs the jar, `telos-session`, and the display-manager entry.

Compose is an xdg-toplevel that labwc pins fullscreen; it is not a native layer-shell client yet.
