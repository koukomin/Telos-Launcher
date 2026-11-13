---
sidebar_position: 8
---

# Telos Desktop cold-start costs

`:desktop-linux` Main / Application startup must stay light. Heavy disk and
process work is deferred until first use and cached afterward. Features are not
removed — only lazy-initialized.

## What stays off the Main path

| Work | Eager behavior | First use |
| --- | --- | --- |
| Freedesktop **icon theme** scan | Resolver constructed; no `mkdir`, no directory walk | `listInstalledThemes()` / `resolve()` — result cached until `invalidate()` |
| **XCursor** theme scan + frame decode | `CursorThemeApplier.apply` publishes `XCURSOR_*` only | First `ensureDecoded()` / `cursorForState` (IO in DesktopWorkspace) |
| **Network** controller | No `wg`/`tor`/`nft`/`dns` probes in `remember {}` | Telos Network `LaunchedEffect` on `Dispatchers.IO`; `configHome(create=false)` for cold probes |
| **Stego** carriers / shard manager | Pre-boot shows path via `carriersRoot()` without mkdir | `ensureCarriersRoot()` / `openStegoCarriers` on unlock; `StegoShardArrayManager` via `lazy` |
| HW crypto **probe** (`/proc/cpuinfo`) | Not run when constructing `PhantomVaultEngine` | First `probe()` / `engine.hw` — cached |

## Rules

- Prefer `Dispatchers.IO` for disk and process status. Do not add `runBlocking` on
  the Main / Compose path.
- Downloads and installs may `mkdir`; discovery helpers must accept a non-create
  path (`iconsRoot(create = false)`, `configHome(create = false)`).
- Keep Settings / Network UI functional: load lists asynchronously when the
  screen opens, not when the desktop window first appears.

## Tests

`com.telos.desktop.startup.DesktopColdStartLazyInitTest` asserts the caches start
cold and populate on first use (behavioral check, not a wall-clock benchmark).
