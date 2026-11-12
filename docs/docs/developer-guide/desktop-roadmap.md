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
| **Helix H4** | In tree | Settings grouped Helix cards (`LinuxDesktopSettingsPanel`), including freedesktop icon theme selector + Telos downloadable packs. |
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
| **VOS-1** | **Partial (in tree)** | Vault profile folders under config `profiles/` (portable session layout when Portable Data is active); active name from `profile.active` in vault `desktop.properties` plus a plain marker (no secrets in properties). **PortableData / PortableVault** boot paths on Windows/macOS with `PortableEnvironmentIsolator` + `PortableTraceGuard` (TMPDIR, XDG_*, no host profile writes). **Multi-vault / profile switch UI** in pre-boot and Helix Settings (`VaultProfileSwitchPanel`: list / create / switch named profiles and portable roots). Full multi-host productization and lock-gated remount remain future work. |
| **VOS-2** | **Partial (in tree)** | **Network MVP:** `DesktopNetworkController` + multi-tab Telos Network app (Status, DNS, session nftables allowlist, Apps routing table, WireGuard via host `wg-quick`/`wg`, Tor SOCKS 9050, encrypted DNS preference + Linux `resolvectl` when available). **Kill-switch:** preference in `~/.config/telos/kill-switch.txt`; on Linux keeps `inet telos_session` default-drop enforced (refuse clear, re-apply after tunnel stop) with Enforced/Failed/PreferenceOnly UI status; Windows/macOS store preference only (honest not-applied). **Per-app routing:** `~/.config/telos/app-routing.txt` (`desktop-id`/`package` → allow/deny/vpn) persisted + editable; process-level nftables owner/cgroup enforcement not yet. **DNS filters (foundations):** vault `dns-block.txt` / `dns-allow.txt` + optional NextDNS config ID (`nextdns-id.txt`); Linux enforces NextDNS via `resolvectl` DoT when available, and optional nftables DNS redirect to a local stub when `dns-stub.port` is set — host lists alone stay PreferenceOnly (no fake filtering); Windows/macOS PreferenceOnly. |
| **VOS-3** | **Partial (in tree)** | **App runtime MVP:** Flatpak sandbox tiers (Host / Vault FS / Net-isolated / Standard) + common permission overrides via `flatpak override --user` when Flatpak is present (Store permissions pane); degrades gracefully without Flatpak. `telos://` deep-link parser/dispatch into Telos virtual apps, Files, Media, and Kickoff search (`--open` / URI argv); Linux `x-scheme-handler/telos` registration (+ Windows HKCU URL protocol best-effort). Winget/scoop adapters, portal Camera/Mic UX, and per-app netns routing: not yet. |
| **VOS-4** | **Partial (in tree)** | `DesktopSessionSync` encrypted zip of `desktop.properties` + `~/.config/telos` (no WG private keys in logs); optional KeePassTL entry **Telos Sync** via kotpass; vaulted `DesktopStoredSyncKey` enables boot auto-import without a KeePass master-password prompt when the user has stored a sync key. LAN pairing with Android: not yet. |
| **VOS-5** | **Partial (in tree)** | Session backup/export in Network + Settings; signed desktop updates and full vault restore remain future work. **Windows HKCU shell** (`WindowsShellIntegration`, `tools/windows/install-telos-shell.ps1`) optional. |
| **VOS-6** | **Partial (in tree)** | Secure clipboard (session vault + auto-clear timer; Linux clears system clipboard so untrusted apps cannot paste sensitive material; portable best-effort clear + UI). Append-only vault audit log (`~/.config/telos/opsec/audit.log`, 0600) for unlock, network policy, profile switch, panic. Attestation light: os-release / compositor / portable flag snapshot into the audit log. Viewer in Settings OpSec Helix card and Network → Status. Not shipped: TPM attestation, kill-switch nftables core, winget/scoop Store adapters. Stego FS Mode-B packaging remains separate transport obfuscation — not claimed as OpSec complete. |

Constraints that stay in force: no `:app:app` dependency on `:desktop-linux`; i18n keys from `:core:i18n` only; labwc is a **host package**, not bundled.

## Beyond the in-tree phases

Portable multi-host packaging, hardened hybrid PQ key wrapping, steganographic carrier transport productization, and a Windows shell/companion path are **not** additional unfinished P/H/S/W phases above. They are separate productization work. Do not describe them as shipped in user-facing docs until implemented.

**Stego FS (partial):** `StegoShardArrayManager.assembleAndOpen` / `assemblePrepared` write `telos-carriers.json`, attach dm-linear when available, otherwise assemble `stego_assembled.tvault`. Portable Data keeps carriers under `TelosPortableData/carriers/` (no host HOME traces). Full OpSec / deniability claims remain out of scope.
