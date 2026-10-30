---
sidebar_position: 10
---

# Telos Helix (session-crash memory)

**If this session dies, resume here.** Helix is the Plasma-class Linux desktop chrome for `:desktop-linux`. P0–P4 stay in tree. Do **not** depend `:app:app` on `:desktop-linux`. Do **not** merge or release unless the user asks. Auto-approve was given for this work.

## Goal

Beat GNOME / Cosmic / Hyprland-default chrome on density and search, using KDE-class panel + Kickoff + Klassy titlebars, Telos magenta/cyan, vault session, and Kvaesitso-style unified search. Do not copy Breeze assets.

## Constraints (always)

- Opaque `surface` / `surfaceContainer` for menus and popups. Blur only on wallpaper.
- Panel ~36 dp, icons ~22 dp, 48 dp touch where actions sit.
- i18n via `:core:i18n` / `TelosI18n` only.
- labwc is a host package.
- Continue existing Kickoff (`PlasmaAppGrid`), Klassy (`WindowDecorationTheme`), panel (`TelosTopPanel`).

## Phases

| Phase | Status | Scope |
| --- | --- | --- |
| **H0** | In tree (tokens) | `TelosSharedColors` Helix surfaces: `surfaceHigh`, `outline`, `panel`, `menu`. Dark Breeze-like tones + Telos primary `#FF007F` and secondary `#00F0FF`. |
| **H1** | In tree (panel glyphs) | `TelosTopPanel` system tray uses `CandyCyberIcon` (volume, network, battery, tray, clip, bell, shot) instead of English word chips. Popups stay opaque `TelosColors.surface`. |
| **H2** | In tree (Kickoff) | `PlasmaAppGrid` Kickoff layout: left favorites + power, right search (`DesktopSearch`) + Telos/Linux grids. Super / logo still `DesktopChrome.toggleAppGrid()`. |
| **H3** | Already in tree | Klassy titlebars, workspace dots, dock row, Alt-Tab strip. Polish only if gaps. |
| **H4** | Pending | Settings window visual pass (same tokens, grouped cards). Do not start extra DEs. |

When a later session asks **what is still pending**: **Helix H4** (settings visual pass). H0–H3 are in tree.

## Files

- Tokens: `ui-kmp/.../TelosSharedTheme.kt`
- Panel: `desktop-linux/.../panel/TelosTopPanel.kt`
- Kickoff: `desktop-linux/.../ui/AppGrid.kt`
- Icons: `desktop-linux/.../icons/CandyCyberIcons.kt`
- Status comment: `desktop-linux/.../ui/Helix.kt`

## Resume prompt

Continue Helix H4 if H0–H3 are already committed. Push `origin/main`. Never merge/release. Never `:app:app` → `:desktop-linux`.
