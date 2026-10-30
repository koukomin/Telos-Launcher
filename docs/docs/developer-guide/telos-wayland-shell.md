---
sidebar_position: 11
---

# Telos Wayland Shell (session-crash memory)

**If this session dies, resume here.** W0 is session integration so host apps treat Telos as a 2026 DE. Compose remains an xdg-toplevel pinned fullscreen. Native `zwlr_layer_shell_v1` is **W1**, not W0.

P0–P4, Helix H0–H6, Surface S0–S4 stay in tree. Do **not** depend `:app:app` on `:desktop-linux`. Do **not** merge or release unless asked.

## W0 (in tree)

| Piece | What it does |
| --- | --- |
| Exclusive zone | labwc `<margin>` and niri `layout.struts` reserve 36 px top and 56 px bottom so GTK/Qt windows stay off the panel and dock. |
| Notifications | `telos-notifyd` owns `org.freedesktop.Notifications` (Gio). JSON lines go to `$XDG_RUNTIME_DIR/telos-notify.jsonl`. Compose polls and posts Helix toasts. |
| Portals | `$XDG_CONFIG_HOME/xdg-desktop-portal/portals.conf` prefers gtk + wlr. FileChooser = gtk, Screenshot/ScreenCast = wlr. Autostart launches `xdg-desktop-portal`, `-wlr`, `-gtk` when present. |
| Lock / PAM | `loginctl lock-session` on lock. Unlock tries `telos-pam-auth` (libpam ctypes, stdin password) then the vault password. Password is not logged. |

## Later (do not start unless asked)

| Phase | Scope |
| --- | --- |
| **W1** | Native layer-shell panel/dock (C/GTK or JNI). Compose chrome as overlay only. |
| **W2** | `org.freedesktop.impl.portal.Notification` + FileChooser in Telos Files. SNI tray. |
| **W3** | Idle, inhibit, greeter, mixed-DPI, IME, AT-SPI. |

## Files

- `desktop-linux/session/labwc/rc.xml` — margins
- `desktop-linux/session/niri/config.kdl` — struts
- `desktop-linux/session/telos-notifyd`
- `desktop-linux/session/telos-pam-auth`
- `desktop-linux/session/xdg-desktop-portal/portals.conf`
- `desktop-linux/session/labwc/autostart`
- `FreedesktopNotifyBridge.kt`, `PamAuth.kt`, `WaylandShell.kt`

## Resume prompt

W0 is in tree. Push `origin/main`. Never merge/release. Never `:app:app` → `:desktop-linux`. Next is W1 only if the user asks.
