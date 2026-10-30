---
sidebar_position: 11
---

# Telos Wayland Shell (session-crash memory)

**If this session dies, resume here.** Compose remains an xdg-toplevel pinned fullscreen. Native panel/dock use **GtkLayerShell** (`zwlr_layer_shell_v1`) when the host has `gir1.2-gtk-layer-shell`. Without that package, Compose chrome stays visible (W0).

P0–P4, Helix H0–H6, Surface S0–S4 stay in tree. Do **not** depend `:app:app` on `:desktop-linux`. Do **not** merge or release unless asked.

## W0 (in tree)

| Piece | What it does |
| --- | --- |
| Exclusive zone | labwc `<margin>` and niri `layout.struts` reserve 36 px top and 56 px bottom so GTK/Qt windows stay off the panel and dock. |
| Notifications | `telos-notifyd` owns `org.freedesktop.Notifications` (Gio). JSON lines go to `$XDG_RUNTIME_DIR/telos-notify.jsonl`. Compose polls and posts Helix toasts. |
| Portals | `$XDG_CONFIG_HOME/xdg-desktop-portal/portals.conf` prefers gtk + wlr. FileChooser = gtk, Screenshot/ScreenCast = wlr. Autostart launches `xdg-desktop-portal`, `-wlr`, `-gtk` when present. |
| Lock / PAM | `loginctl lock-session` on lock. Unlock tries `telos-pam-auth` (libpam ctypes, stdin password) then the vault password. Password is not logged. |

## W1 (in tree)

| Piece | What it does |
| --- | --- |
| `telos-layer` | Gtk 3 + GtkLayerShell: 36 px top panel and 56 px dock, Helix colors. Exclusive zone **0** so it does not stack on W0 margins. |
| IPC | `$XDG_RUNTIME_DIR/telos-layer.sock` JSON lines `{"event":"ready"}` / `{"event":"click","id":"kickoff"}`. |
| Compose | `LayerShellBridge` connects; on `ready` it hides the Compose panel and dock. Clicks toggle Kickoff, Overview, Control Center, notify, clipboard, Files, Settings, lock. |

Host package: `gtk-layer-shell` / `gir1.2-gtk-layer-shell-0`. Missing GIR → `telos-layer` exits 0, Compose chrome remains.

## W2 (in tree)

| Piece | What it does |
| --- | --- |
| `telos-portal` | D-Bus `org.freedesktop.impl.portal.desktop.telos`. FileChooser Open/Save writes `telos-portal-in.jsonl`; Compose `TelosFilePickerModal` replies on `telos-portal-out.jsonl` with `file://` URIs. Notification impl appends to the notify fifo. |
| `telos.portal` | `UseIn=Telos`. `portals.conf` prefers `telos;gtk` for FileChooser and Notification. |
| `telos-sni` | Owns `org.kde.StatusNotifierWatcher` with `IsStatusNotifierHostRegistered=true`. Snapshot `telos-sni.jsonl`. Panel tray lists SNI items; click writes `telos-sni-cmd.jsonl` Activate. |

Gio missing → helpers exit 0. gtk FileChooser remains the fallback in `portals.conf`.

## W3 (in tree)

| Piece | What it does |
| --- | --- |
| Idle | `telos-idle` uses swayidle (or a sleep loop). Writes `telos-lock.jsonl`; Compose `IdleLockBridge` calls `SessionPowerActions.lock`. Timeout from `idleSeconds` / `telos-idle.sec` (default 300). |
| Inhibit | `IdleInhibit` runs `systemd-inhibit --what=idle:sleep` while playerctl is Playing. |
| Greeter | `telos-greeter` GTK password when `WAYLAND_DISPLAY`/`DISPLAY` is set, pipes `--unlock-stdin`. TTY session keeps console unlock. |
| Mixed-DPI | `desktop.properties` `output.<name>=WxH@Hz@scale` applied to every output on startup. |
| IME | Session exports fcitx5 or ibus modules; autostart launches the daemon if present. |
| AT-SPI | `GTK_A11Y=1`, `at-spi-bus-launcher --launch-immediately` when installed. |

## Later

Numbered Wayland shell phases W0–W3 are in tree. Do not start extra DEs unless asked.

## Files

- `desktop-linux/session/telos-layer`
- `LayerShellBridge.kt`, `WaylandShell.kt`
- W0 files unchanged (margins, notifyd, PAM, portals)

## Resume prompt

W0–W3 are in tree. Push `origin/main`. Never merge/release. Never `:app:app` → `:desktop-linux`.
