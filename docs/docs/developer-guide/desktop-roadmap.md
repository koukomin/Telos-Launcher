---
sidebar_position: 9
---

# Telos Linux Desktop roadmap (remember this)

When a later session asks **what is still pending** for the Linux desktop, the answer is **P2, P3, and P4**. P0 and P1 are the current work (labwc + vault as full session HOME, Linux only).

| Phase | Status | Scope |
| --- | --- | --- |
| **P0** | In tree | `telos-session` → vault CLI unlock → **labwc** compositor → Telos Compose shell (`--session`). Host apps are normal labwc windows. x64 UberJar. |
| **P1** | In tree | Session **HOME is the vault mount**. nftables `inet telos_session` default-drop + `~/.config/telos/net-allow.txt`. Optional FIDO2 mix-in via `fido2-token` / `fido2-assert`. Panic unmount unchanged. Arm64 jar = same Gradle task on an aarch64 host. Linux only. |
| **P2** | In progress | Search + Flatpak store + settings aliases. Files: XDG shortcuts row, new folder, trash/restore (XDG Trash spec). Media: Music/Videos/Photos library scan + MPRIS. Remaining: deeper archive/network Files parity. |
| **P3** | **Pending** | Unix/macOS backends, fractional scaling, niri option, secure screenshots, CI two-arch (x64 + arm64). |
| **P4** | **Pending** | `:ui-kmp` shared Compose screens. Android migration is a **separate PR** and must not land unless the user asks. `:app:app` stays +0 until then. |

Constraints that stay in force: no `:app:app` dependency on `:desktop-linux`; i18n keys from `:core:i18n` only; labwc is a **host package**, not bundled.
