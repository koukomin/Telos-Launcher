---
sidebar_position: 40
---

# Telos Linux Desktop

Telos Linux Desktop is a **separate JVM program** (`:desktop-linux`). It is not inside the Android APK. Unlock the Phantom Vault, then you get **Helix** chrome: a KDE-style top bar with glyphs, Kickoff (favorites + search), icon dock, Klassy window buttons, floating Telos apps, and a host `.desktop` launcher. The display manager offers **Telos** (labwc) and **Telos (niri)**. Scale 100–200% is in Settings. Screenshots save as owner-only PNGs (set `TELOS_SHOT_CLIPBOARD=1` to also copy).

## What it does

| Feature | Behaviour |
| --- | --- |
| Top bar | Workspaces 1–4, clock, audio, network, battery, tray, clipboard, notifications. Right-click or long-press opens style (height, icon size, tint, transparency). Choices save in vault `desktop.properties`. |
| AppGrid | Telos logo on the bar opens the full-screen grid. The All tab searches Telos apps, `.desktop` files, files under Desktop/Downloads/Documents/Music/Pictures/Videos, calculator (`2+2`), and Flatpak (tap to install). |
| Windows | Titlebar drag, 8-edge resize, minimize / maximize / close, shade (double-click titlebar), edge snap. Maximized windows stay below the top bar. |
| Files / Media | Folders open in Telos Files (tabs, optional dual pane, shortcuts, new folder, trash). Zip/jar/apk/7z/tar open as folders; split zip (`.z01` next to `.zip`) is one archive; password zips ask once in the window; rar needs host `7z`; ACE needs host `unar` or `unace`. Add smb/sftp places (gio), a Nextcloud/WebDAV URL with user and password, or Nextcloud Login Flow v2 (Integrations): the browser opens, Telos polls for an app password and does not write it to disk. Dropbox, Google Drive and OneDrive use OAuth with PKCE on `http://localhost:53682/` (Google `127.0.0.1`): paste `dropbox`, `gdrive` or `onedrive` in the network field, your client ID in user, Google client secret in password, then Sign in. Tokens stay in memory until the vault locks. Open a cloud file to download it to a temp folder and open it; long-press saves it to Downloads. Add uploads a file from a picker into the current cloud folder. Media lists Music/Videos/Photos (and Downloads), filter, volume, and playerctl; photos open with `xdg-open`. |
| Telos apps | Files, Photos, Downloads, Trash, Media, Notes, Calendar, Calculator, Clock, Network, Store (Flatpak list/search/install/uninstall/update), Settings, System Monitor, System Optimizer. |
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

1. Install `labwc` and a JRE 17+.
2. Build the UberJar.
3. Run `desktop-linux/session/telos-session` (or pick **Telos** in the display manager after installing `telos.desktop`). `telos-session --help` lists environment variables and session keys.

The vault password (and optional FIDO2 key) unlocks an encrypted volume that **becomes `$HOME`** for that session. The LUKS image stays under the **host** home (`TELOS_HOST_HOME`); only session files use the vault mount. Outbound network is denied by default except DNS and IPv4/IPv6 CIDRs in `~/.config/telos/net-allow.txt`. Leaving the session locks the vault and removes the `inet telos_session` nftables table. `Ctrl+Alt+L` panic-unmounts and ends labwc.

`./install-telos-de.sh` installs the jar, `telos-session`, and the display-manager entry. Only one session runs at a time. To try labwc inside another Wayland desktop, set `TELOS_ALLOW_NESTED=1`.

Compose is an xdg-toplevel that labwc pins fullscreen; it is not a native layer-shell client yet.
