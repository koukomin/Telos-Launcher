---
sidebar_position: 40
---

# Telos Desktop

**Telos Desktop** is a **separate JVM program** (Gradle module `:desktop-linux`). It is not inside the Android APK. On Linux you can unlock the Phantom Vault and run a full **Helix** session (labwc/niri). On Windows (and later macOS) the same binary family opens as a **windowed portable shell** — **Portable Data** by default (config/cache/temp only under a folder next to the jar or `TELOS_PORTABLE_DATA`), no LUKS / labwc. **Host Home** is an explicit opt-in that may leave traces on the PC.

Linux session chrome: KDE-style top bar with glyphs, Kickoff, icon dock, Overview (Meta+Tab), Control Center, toasts, Klassy window buttons, floating Telos apps, and a host `.desktop` launcher. The display manager offers **Telos Desktop** (labwc) and **Telos Desktop (niri)**. After `./install-telos-de.sh`, session files live in `/usr/share/wayland-sessions/` and appear in **SDDM**, **GDM**, and **LightDM** login menus. Host windows keep a 36 px top and 56 px bottom reserved band. With host `gtk-layer-shell`, the panel and dock are real Wayland layer surfaces (`telos-layer`); otherwise Telos draws them in Compose. Session notifications use `org.freedesktop.Notifications`. File pickers use the Telos portal (Telos Files picker) when `telos-portal` is running, then gtk. Screencast uses wlr. StatusNotifier tray icons show in the panel tray. Idle locks the session (swayidle, default 5 minutes; media playback inhibits idle). Nested sessions can use the GTK vault greeter; a TTY session still uses the console password. Per-output scale is saved. IME (fcitx5/ibus) and AT-SPI start when installed. Lock uses `loginctl` and PAM, then the vault password. Scale 100–200% is in Settings. Screenshots save as owner-only PNGs (set `TELOS_SHOT_CLIPBOARD=1` to also copy).

## Download binaries (GitHub)

GitHub Actions builds **ready UberJars** for:

| Platform | Artifact | Status |
| --- | --- | --- |
| Linux x64 / arm64 | `telos-desktop-linux-*.jar` (+ session tarball) | Shipped on tag / workflow_dispatch / PR desktop paths |
| Windows x64 | `telos-desktop-windows-x64-*.jar` (Exe best-effort) | Shipped with release; play-test Host Home first |
| macOS Intel / ARM | — | **Deferred** until Linux + Windows play correctly |

Workflow: `.github/workflows/build-desktop-binaries.yml`. Releases also attach jars via `build-release.yml`.

```bash
# Linux
java -jar telos-desktop-linux-x64-*.jar
# or full session after install
./desktop-linux/session/telos-session

# Windows (JDK 21+ on PATH)
java -jar telos-desktop-windows-x64-*.jar
```

## What it does

| Feature | Behaviour |
| --- | --- |
| Top bar | Plasma-style Helix panel: workspaces 1–4, clock, audio, network, battery, keyboard/IME layout chip, StatusNotifier tray, clipboard, notifications. Right-click or long-press opens style (height, icon size, tint, transparency). Choices save in vault `desktop.properties`. |
| AppGrid | Telos logo on the bar (or Super+Space) drops the Kickoff drawer **down from under the top bar**, like Yakuake — it does not replace the whole desktop. The All tab searches Telos apps, `.desktop` files, files under Desktop/Downloads/Documents/Music/Pictures/Videos, calculator (`2+2`), and Flatpak (tap to install). |
| Windows | Titlebar drag, 8-edge resize, minimize / maximize / close, shade (double-click titlebar), edge snap. Maximized windows stay below the top bar. |
| Files / Media | Folders open in Telos Files (tabs, optional dual pane, shortcuts, new folder, trash). Zip/jar/apk/7z/tar open as folders; split zip (`.z01` next to `.zip`) is one archive; password zips ask once in the window; rar needs host `7z`; ACE needs host `unar` or `unace`. Add smb/sftp places (gio), a Nextcloud/WebDAV URL with user and password, or Nextcloud Login Flow v2 (Integrations): the browser opens, Telos polls for an app password and does not write it to disk. Dropbox, Google Drive and OneDrive use OAuth with PKCE on `http://localhost:53682/` (Google `127.0.0.1`): paste `dropbox`, `gdrive` or `onedrive` in the network field, your client ID in user, Google client secret in password, then Sign in. Tokens stay in memory until the vault locks. Open a cloud file to download it to a temp folder and open it; long-press saves it to Downloads. Add uploads a file from a picker into the current cloud folder. Media lists Music/Videos/Photos (and Downloads), filter, volume, and playerctl; photos open with `xdg-open`. |
| Telos apps | Files, Photos, Downloads, Trash, Media, Notes, Calendar, Calculator, Clock, **Network** (Status, encrypted DNS preference, DNS block/allow lists + optional NextDNS configuration ID, session firewall allowlist, WireGuard import/start, Tor SOCKS on Linux when `tor`/`wg-quick` are installed), Store (Flatpak list/search/install/uninstall/update, permissions / sandbox tiers when Flatpak is present), Settings, System Monitor, System Optimizer. |
| Icon themes | Settings → Icons lists system freedesktop themes already on the machine and packs downloaded through Telos (Papirus, Tela, Candy Icons). Downloads extract into the vault `icons/` folder on Linux, or into `TelosPortableData/icons/` in Portable Data mode (Windows/macOS). Built-in CandyCyber glyphs cover Telos chrome when no disk theme is present. These are desktop themes, not Android launcher icon packs. |
| Deep links | `telos://app/…`, `telos://search?q=…`, and `telos://files?path=…` open Telos apps or Kickoff search. The Linux installer registers `x-scheme-handler/telos`. |
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

The vault password (and optional FIDO2 key) unlocks an encrypted volume that **becomes `$HOME`** for that session. The LUKS image stays under the **host** home (`TELOS_HOST_HOME`); only session files use the vault mount. Outbound network is denied by default except DNS and IPv4/IPv6 CIDRs in `~/.config/telos/net-allow.txt`. Open the **Network** Telos app to edit that allowlist, enable the **kill-switch** (keeps the session firewall up if WireGuard or Tor stop; not applied on Windows/macOS portable), edit the per-app routing table in `app-routing.txt` (stored only for now), store a DoH/DoT/ plain DNS line in `dns.txt`, edit DNS block/allow lists (`dns-block.txt` / `dns-allow.txt`) and an optional NextDNS configuration ID, import a WireGuard `.conf` under `~/.config/telos/wireguard/`, or start Tor with data under `~/.config/telos/tor/` (host `tor` / `wg-quick` required). On Linux, a NextDNS ID is applied as DoT through `resolvectl` when available; local host lists alone are preference-only unless a DNS stub port is configured for nftables redirect. Private keys are never written to `desktop.properties` or logs. Leaving the session locks the vault and removes the `inet telos_session` nftables table. `Ctrl+Alt+L` panic-unmounts and ends labwc.

`./install-telos-de.sh` installs the jar, `telos-session`, and Wayland session entries (`telos.desktop`, `telos-niri.desktop`). Only one session runs at a time. To try labwc inside another Wayland desktop, set `TELOS_ALLOW_NESTED=1`.

On Windows, optional per-user shell replacement (`tools/windows/install-telos-shell.ps1` or Settings → Portable) launches the UberJar at sign-in; revert restores `explorer.exe`.

**OpSec (VOS-6 partial):** Settings and Network → Status show a session audit log under `~/.config/telos/opsec/audit.log` (owner-only). Sensitive clipboard material is vaulted in memory and cleared from the system clipboard after a timer (or immediately for keys/IBAN/card/OTP patterns). Unlock writes a light host integrity snapshot (OS, compositor, portable flag) — not TPM attestation.

**Session sync:** Network → Status can export/import an AES-encrypted bundle of `desktop.properties` and Telos network prefs. Enter a sync passphrase (or the password from KeePassTL entry **Telos Sync**) and enable auto-import to vault that key for prompt-free boot import. Without a stored sync key, auto-import stays off and boot does not invent credentials. An optional `.kdbx` under `~/.config/telos/sync/keepass.kdbx` can still supply the secret when a master password is provided.

Compose is an xdg-toplevel that labwc pins fullscreen; it is not a native layer-shell client yet.
