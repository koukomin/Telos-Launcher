---
sidebar_position: 8
---

# Telos Desktop (`:desktop-linux`)

`:desktop-linux` is a **standalone Compose for Desktop / JVM** module for **Telos Desktop**. It is registered in `settings.gradle.kts` and is **not** a dependency of `:app:app`. The Android APK does not ship Compose Desktop, cryptsetup helpers, or Linux icon scanners. CI publishes Linux and Windows UberJars to GitHub; macOS is deferred until those run cleanly.

## Build

```bash
./gradlew :desktop-linux:packageUberJarForCurrentOS
./gradlew :desktop-linux:packageDeb
./gradlew :desktop-linux:packageRpm
./gradlew :desktop-linux:packageAppImage
```

Main class: `com.telos.desktop.MainKt`. The window is undecorated fullscreen.

## Vault

VOS-5 (partial): `DesktopVaultRestore` confirms AES session-bundle restore into vault `desktop.properties` + `~/.config/telos`; `DesktopSignedUpdate` verifies Ed25519-signed manifests (`file://` or HTTPS preference) and stages jars under portable/vault temp without auto-install.

VOS-6 OpSec (partial): `TelosOpSecGuard` secure clipboard + `TelosSessionAuditLog` (append-only under vault `~/.config/telos/opsec/audit.log`, mode 0600). Attestation light writes os-release / compositor / portable mode into the audit log on unlock, and `TpmAttestation` adds PCR 0/7 digest fields when `tpm2-tools` are on `PATH` (optional quote digest if `tpm/ak.handle` is set). Missing TPM records `tpm=unavailable` without failing unlock. This is local PCR light only — not remote attestation (RAP).

The Phantom Vault can sit on a single image or a folder of media carriers (stego shards +
`dmsetup` linear span), grow online with BTRFS zstd, use AES-NI `aes-xts-plain64` plus Argon2id,
and rclone-sync modified carriers on lock. Pre-boot offers host home, unlock, create, and tmpfs ghost modes.

**VOS-1 profiles (partial):** `DesktopProfiles` keeps named folders under config `profiles/` and the
active name in `profile.active` (vault `desktop.properties` + marker file; never passwords).
`VaultProfileSwitchPanel` lists/creates/switches profiles and portable vault roots in pre-boot and
Helix Settings. On Windows Portable Data, profile config stays under the portable session home so
the host profile is not written. Selecting a portable root rejects the host home / `.config` /
`.cache` / `.local`. Lock-gated remount across hosts is not claimed as complete.

**Stego FS (Mode B, partial):** carrier pools write `telos-carriers.json` (offsets/sizes only).
`assembleAndOpen` prefers Linux `losetup`/`dmsetup`; when that is unavailable it concatenates
payloads into `stego_assembled.tvault`. Portable Data / Portable Vault use
`TelosPortableData/carriers/` via `PortableStegoFs` so media carriers stay outside the host
profile home. This is optional transport obfuscation, not forensic steganography or completed VOS-6 OpSec.

## Vault (legacy image)

`LuksVaultManager` asks for a master password, then unlocks `~/.local/share/telos/telos_vault.img` with `cryptsetup` / `udisksctl` / `pkexec` and mounts it at `~/.local/share/telos/vault_mnt`. Desktop settings, state and databases are written only under that mount. A JVM shutdown hook syncs and locks the vault.

## Desktop shell

- Freedesktop `.desktop` scan and launch (`LinuxAppScanner`, `DesktopFileLauncher`)
- Icon themes with `Inherits=` fallback (`LinuxIconThemeResolver`): scans `~/.local/share/icons`, `/usr/share/icons`, `XDG_DATA_DIRS`, Flatpak exports, plus Telos-managed packs under vault `icons/` or portable `TelosPortableData/icons/` (never host HOME in Portable Data). Settings lists system + downloaded themes and a Telos catalog (Papirus, Tela, candy-icons — GPL-3.0, downloaded on demand). Built-in **CandyCyber** Compose glyphs remain the chrome fallback on Windows/macOS when no XDG theme exists. These are freedesktop themes, not Android ADW icon packs.
- XDG user dirs and a live `WatchService` desktop icon grid
- Wayland/X11 display modes (`wlr-randr`, `kscreen-doctor`, `xrandr`)
- Klassy / SierraBreeze-inspired Compose window chrome and Sweet cursor presets
- KDE-compatible XCursor / Plasma cursor themes: discovery under Freedesktop icon roots (`…/icons/<Theme>/cursors` + `index.theme` `[Cursor Theme]`), host `kcminputrc` / `kdeglobals` / GTK cursor name, vault `desktop.properties` keys `cursor.theme` / `cursor.size` (16/24/32/48). Linux exports `XCURSOR_THEME` / `XCURSOR_SIZE` to child processes and best-effort loads an XCursor frame into an AWT cursor; Compose Sweet/Breeze overlay remains the fallback. Windows/macOS map to stock AWT cursors only. Optional GPL downloads (BreezeX, Bibata) install into the portable/vault icons root — same layout as icon packs, separate from any icon-pack downloader.

## Adaptive shell (proposal, implemented on Linux)

One Telos product, three chrome modes from the same settings store:

- **Phone (Android `:app:app`):** existing launcher; no Linux module in the APK.
- **Tablet:** top status bar + AppGrid (this desktop shell when width is tablet-sized).
- **Linux desktop:** KDE-style top panel, AppGrid with Telos + `.desktop` apps, floating windows.

Shared preferences (theme, icon pack, grid size, vault, display) live in the vault `desktop.properties` on Linux and Datastore on Android. They are not compiled together; names stay aligned so a future sync can copy keys.

## Windowed vs fullscreen

F11 and Settings toggle a normal resizable window vs undecorated fullscreen. When
`followSystemTheme` is on, accent/icon/cursor names are read from Plasma `kcminputrc`,
`~/.config/kdeglobals`, and GTK `settings.ini` (plus `XCURSOR_*` when set). The left Telos candy/cyberpunk vector logo opens a Plasma-style AppGrid.

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

W0 (Wayland shell): labwc `<margin>` and niri `struts` reserve 36 px top / 56 px bottom for host windows. `telos-notifyd` owns `org.freedesktop.Notifications`. Autostart launches xdg-desktop-portal, `-wlr`, and `-gtk` when present; `portals.conf` prefers gtk FileChooser and wlr Screenshot/ScreenCast. Lock calls `loginctl lock-session`; unlock tries `telos-pam-auth` then the vault password. W1: `telos-layer` (GtkLayerShell) draws the panel and dock as layer surfaces; Compose hides its chrome after the Unix socket `ready` event. Missing GIR keeps Compose chrome. W2: `telos-portal` implements FileChooser and Notification; Compose shows `TelosFilePickerModal`. `telos-sni` is the StatusNotifierWatcher; the panel tray activates items. W3: idle lock, media idle-inhibit, GTK greeter when a display exists, per-output scale, fcitx5/ibus, AT-SPI. Compose is still an xdg-toplevel for the desktop. See [telos-wayland-shell.md](./telos-wayland-shell.md).

Install `labwc`. `./install-telos-de.sh` copies the jar and session files to `/opt/telos-desktop`, installs `/usr/local/bin/telos-session`, `/usr/local/bin/start-telos-de`, and `/usr/local/bin/telos-desktop-test`, and registers `/usr/share/wayland-sessions/telos.desktop`. Desktop JVM flags use Generational ZGC, string deduplication, AlwaysPreTouch, and OpenGL Java2D. `.desktop` scanning is parallel with an mtime cache; `/proc/stat` and `/proc/meminfo` use a reused byte buffer. Arm64 uses the same `packageUberJarForCurrentOS` on an aarch64 machine. `--session --already-unlocked` exits if the vault is not mounted. Empty unlock passwords are rejected. `packageUberJarForCurrentOS` copies `session/` next to the jar. Autostart reads `TELOS_JAR` from the environment (no sed). A second `telos-session` is refused via flock. Nesting labwc on an existing Wayland session needs `TELOS_ALLOW_NESTED=1`. `:desktop-linux:test` covers nftables allowlist parsing and shell quoting.

**VOS-2 Network MVP:** `DesktopNetworkController` + multi-tab `TelosNetworkApp` (Status/DNS/session nftables allowlist/Apps routing/WireGuard/Tor). Config under vault `~/.config/telos/` (`net-allow.txt`, `dns.txt`, `dns-block.txt`, `dns-allow.txt`, `nextdns-id.txt`, optional `dns-stub.port`, `kill-switch.txt`, `app-routing.txt`, `wireguard/`, `tor/`). Kill-switch on Linux re-applies `inet telos_session` and blocks clear while enabled; portable platforms show preference-only. Per-app routing table is stored (allow/deny/vpn) without process-level enforcement yet. Linux applies DoT via `resolvectl` when present (including NextDNS config ID → `<id>.dns.nextdns.io`); optional nftables `inet telos_dns` redirects DNS to a local stub when `dns-stub.port` is set. Host block/allow lists without that path stay preference-only. Windows portable stores preferences only. See [desktop-roadmap.md](./desktop-roadmap.md) VOS rows.

**VOS-3 App runtime (partial):** Store can open a Flatpak permissions pane (sandbox tiers + common overrides via `flatpak override --user`). Without Flatpak the UI shows an unavailable state and does not crash. `telos://` links (`app`, `search`, `files`, short aliases) are parsed from `--open` / argv, queued into the Compose shell, and registered as `x-scheme-handler/telos` on Linux session install / boot (Windows HKCU protocol best-effort). See [desktop-roadmap.md](./desktop-roadmap.md) VOS rows.

P2 (in tree): AppGrid **All** tab runs `DesktopSearch`. Store uses Flatpak. Files: XDG shortcuts, trash, zip (split parts resolve to the central `.zip`), jar/apk/7z/tar, AES zip passwords in memory, rar via host `7z`, GTK bookmarks and `$XDG_RUNTIME_DIR/gvfs`. ACE archives browse via host `unar` or `unace` (not bundled; empty list if neither is installed). Split `.c00` maps to the `.ace`. Media libraries + MPRIS. Files workspaces persist tabs/dual pane under the vault config (paths only). Nextcloud/WebDAV uses PROPFIND over HTTPS; the username may be saved, the password stays in memory and is cleared when the vault locks. smb/sftp still use gio. Nextcloud Login Flow v2: POST `/index.php/login/v2`, open the login URL, poll for `appPassword` (memory only). Dropbox/Google Drive/OneDrive: OAuth with PKCE, loopback `http://localhost:53682/` (Google `127.0.0.1`), your own client ID (Google also needs the client secret). Access/refresh tokens stay in memory and are cleared when the vault locks. Files lists the cloud after sign-in; click downloads and opens, long-press saves to Downloads, Add uploads a local file (Dropbox simple ≤100 MB then sessions, OneDrive ≤4 MB then sessions, Drive resumable media). Search All tab also evaluates `2+2` style calculator queries, walks Music/Pictures/Videos under allowed vault/portable roots, and Flatpak search (install on tap). **Vault-local file name index (opt-in, off by default):** `VaultFileNameIndex` + `privacy.fileNameIndex` in Settings → OpSec; AES-GCM blob under `{session}/.cache/telos/file-index` (owner-only); filenames/extensions only; throttled incremental rescan; never indexes the host profile unless `BootMode.HostHome`; no content cloud upload. Media filters libraries (depth 4, Downloads too) and opens audio/video with playerctl. Store lists installed Flatpaks in a lazy list, uninstalls, and runs `flatpak update`. P3: `TELOS_COMPOSITOR=niri` (session `telos-niri.desktop`), fractional scale 100/125/150/175/200% via wlr-randr, grim/screencapture PNGs owner-only (`0600`, clipboard opt-in). macOS/BSD hide LUKS. CI tests and jars on x64 and arm64. P4: `:ui-kmp` shared screens run on JVM (desktop) and Android (`:app:ui` hosts: search calculator keypad, clock local time, empty Notes, Calendar agenda month). Full Telos Calculator/Notes/Clock/Calendar remain the Android apps. `:app:app` does not depend on `:desktop-linux` or `:ui-kmp`.
