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

## Later (do not start unless asked)

| Phase | Scope |
| --- | --- |
| **W2** | `org.freedesktop.impl.portal.Notification` + FileChooser in Telos Files. SNI tray. |
| **W3** | Idle, inhibit, greeter, mixed-DPI, IME, AT-SPI. |

## Files

- `desktop-linux/session/telos-layer`
- `LayerShellBridge.kt`, `WaylandShell.kt`
- W0 files unchanged (margins, notifyd, PAM, portals)

## Resume prompt

W0 and W1 are in tree. Push `origin/main`. Never merge/release. Never `:app:app` → `:desktop-linux`. Next is **W2** only if the user asks.
