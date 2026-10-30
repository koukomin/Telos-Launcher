---
sidebar_position: 9
---

# Telos Linux Desktop roadmap (remember this)

When a later session asks **what is still pending** for the Linux desktop, the answer is **Android `:ui-kmp` migration** (a separate change). P0–P4 scaffolding is in the tree. `:app:app` does not depend on `:ui-kmp` or `:desktop-linux`.

| Phase | Status | Scope |
| --- | --- | --- |
| **P0** | In tree | `telos-session` → vault CLI unlock → **labwc** compositor → Telos Compose shell (`--session`). Host apps are normal labwc windows. x64 UberJar. |
| **P1** | In tree | Session **HOME is the vault mount**. nftables `inet telos_session` default-drop + `~/.config/telos/net-allow.txt`. Optional FIDO2 mix-in via `fido2-token` / `fido2-assert`. Panic unmount unchanged. Arm64 jar = same Gradle task on an aarch64 host. Linux only. |
| **P2** | In tree | Search (apps, files, calculator, Flatpak), Media libraries + MPRIS, Store install/uninstall/update, archives, Files workspaces and cloud. Remaining P2 polish only if gaps appear. |
| **P3** | In tree | `TELOS_COMPOSITOR=niri` (fallback labwc), fractional scale 100–200%, screenshots `0600` and no clipboard unless `TELOS_SHOT_CLIPBOARD=1` (macOS `screencapture`), macOS/BSD settings filter (no LUKS), CI `ubuntu-latest` + `ubuntu-24.04-arm`. |
| **P4** | In tree (desktop only) | `:ui-kmp` common Compose screens (theme, calculator, clock, notes, calendar) used by `:desktop-linux`. **Android still uses `:app:ui`.** Do not add `:ui-kmp` to `:app:app` until a dedicated migration. |

Constraints that stay in force: no `:app:app` dependency on `:desktop-linux`; i18n keys from `:core:i18n` only; labwc is a **host package**, not bundled.
