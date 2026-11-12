---
sidebar_position: 9
---

# Telos Desktop roadmap (remember this)

When a later session asks **what is still pending** for the **Linux session stack**, the answer is **none of P0–P4, Helix, Surface, or Wayland W0–W3**. Spec: [telos-wayland-shell.md](./telos-wayland-shell.md). `:app:app` still must not depend on `:desktop-linux`. `:app:ui` hosts `:ui-kmp` screens. Spec: [Telos Helix](./telos-helix.md).

**Cross-platform binaries (GitHub):** Linux + Windows UberJars are the current ship targets (see `build-desktop-binaries.yml`). macOS Intel/ARM ships only after those play correctly. Branding is **Telos Desktop** (module path remains `:desktop-linux`).

| Phase | Status | Scope |
| --- | --- | --- |
| **P0** | In tree | `telos-session` → vault CLI unlock → **labwc** compositor → Telos Compose shell (`--session`). Host apps are normal labwc windows. x64 UberJar. |
| **P1** | In tree | Session **HOME is the vault mount**. nftables `inet telos_session` default-drop + `~/.config/telos/net-allow.txt`. Optional FIDO2 mix-in via `fido2-token` / `fido2-assert`. Panic unmount unchanged. Arm64 jar = same Gradle task on an aarch64 host. Linux only. |
| **P2** | In tree | Search (apps, files, calculator, Flatpak), Media libraries + MPRIS, Store install/uninstall/update, archives, Files workspaces and cloud. Remaining P2 polish only if gaps appear. |
| **P3** | In tree | `TELOS_COMPOSITOR=niri` (fallback labwc), fractional scale 100–200%, screenshots `0600` and no clipboard unless `TELOS_SHOT_CLIPBOARD=1` (macOS `screencapture`), macOS/BSD settings filter (no LUKS), CI `ubuntu-latest` + `ubuntu-24.04-arm`. |
| **P4** | In tree | `:ui-kmp` JVM+Android screens. `:desktop-linux` uses JVM. `:app:ui` hosts them (search calculator pad, clock local face, empty notes, agenda month). **Do not add `:ui-kmp` to `:app:app`.** Full Calculator/Notes/Clock/Calendar apps stay in `:app:ui`. |
| **Helix H0–H3** | In tree | Plasma-class chrome: shared Helix tokens, panel glyphs, Kickoff favorites+search. See [telos-helix.md](./telos-helix.md). |
| **Helix H4** | In tree | Settings grouped Helix cards (`LinuxDesktopSettingsPanel`). |
| **Helix H5** | In tree | Icon dock + Kickoff from dock + opaque Alt-Tab. |
| **Helix H6** | In tree | Context menu, notifications, clipboard, opaque window chrome + titles. |
| **Surface S0–S4** | In tree | Icon cards, 160 ms overlays, OSD/toast/clock month, Overview, Control Center. |
| **Wayland W0** | In tree | labwc/niri exclusive-zone struts, `telos-notifyd`, portal prefer gtk+wlr, PAM lock helper. Compose is still xdg-toplevel. |
| **Wayland W1** | In tree | GtkLayerShell panel/dock (`telos-layer`) + Unix socket. Compose chrome hides when the helper is ready. |
| **Wayland W2** | In tree | `telos-portal` FileChooser + Notification, `telos-sni` StatusNotifierWatcher, tray Activate. |
| **Wayland W3** | In tree | Idle lock, media idle-inhibit, GTK greeter when a display exists, per-output scale, IME, AT-SPI. |

## Virtual OS phases (VOS-1 … VOS-6)

These sit **on top of** P0–W3 (still in tree). They describe the Telos Desktop **Virtual OS** roadmap, not Android launcher work.

| Phase | Status | Scope |
| --- | --- | --- |
| **VOS-1** | Scaffolding | Vault profile folders under `~/.config/telos/profiles/`; active name from `profile.active` in vault `desktop.properties` (no secrets in properties). Multi-vault switch and export remain future work. |
| **VOS-2** | **Partial (in tree)** | **Network MVP:** `DesktopNetworkController` + multi-tab Telos Network app (Status, DNS, session nftables allowlist, WireGuard via host `wg-quick`/`wg`, Tor SOCKS 9050, encrypted DNS preference + Linux `resolvectl` when available). Windows/macOS portable: preference + connectivity snapshot only (no crash). Kill-switch / per-app routing / NextDNS-class filters: not yet. |
| **VOS-3** | Planned | App runtime: Flatpak permissions, sandbox tiers, `telos://` deep links. |
| **VOS-4** | Planned | Continuity with Android Telos (encrypted sync, LAN pairing). |
| **VOS-5** | Planned | Signed desktop updates, backup/restore of vault + network configs. |
| **VOS-6** | Planned | OpSec: attestation light, secure clipboard, audit log. |

Constraints that stay in force: no `:app:app` dependency on `:desktop-linux`; i18n keys from `:core:i18n` only; labwc is a **host package**, not bundled.
