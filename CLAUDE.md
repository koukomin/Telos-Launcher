# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Telos — a search-focused, free and open source Android launcher (package `de.mm20.launcher2`). Kotlin/Gradle multi-module Android project. Licensed under GPL-3.0, except `plugins/sdk` and `core/shared` which are Apache-2.0.

## Build & common commands

- Build environment: JDK 21 toolchain (see `gradle/gradle-daemon-jvm.properties`), Gradle 9.4.1 via wrapper.
- Build variants are a combination of a build type (`debug`, `release`, `nightly`) and a product flavor (`default`, `fdroid`), e.g. `assembleDefaultDebug`, `assembleFdroidRelease`.
- Build a debug APK: `./gradlew assembleDefaultDebug`
- Install to a connected device/emulator: `./gradlew installDefaultDebug`
- Run all unit tests: `./gradlew test`
- Run tests for a single module: `./gradlew :core:base:test` (module paths mirror the directory structure, e.g. `:data:locations:test`)
- Run a single test class: `./gradlew :core:base:test --tests "de.mm20.launcher2.OpeningScheduleTest"`
- Lint is non-fatal (`lint.abortOnError = false` in `app/app/build.gradle.kts`).
- Unit tests currently only exist in `core/ktx`, `core/base`, and `data/locations` — most modules have no test suite.
- CI (`.github/workflows/build-nightly.yml`) builds `assembleDefaultNightly` nightly.

## Architecture

The codebase is split into ~4 dozen Gradle modules under four top-level groups, from highest to lowest level:

- **`:app`** — `:app:app` is almost empty, just the `Application` class (`LauncherApplication`, in `app/app/src/main/java/de/mm20/launcher2/LauncherApplication.kt`). `:app:ui` contains almost the entire UI and is the only module using Jetpack Compose.
- **`:services`** — higher-level business-logic APIs, one module per feature area (`:search`, `:icons`, `:widgets`, `:favorites`, `:tags`, `:badges`, `:music`, `:accounts`, `:backup`, `:plugins`, `:global-actions`, `:feed`).
- **`:data`** — lower-level implementations, usually implementing interfaces defined in `:core:base` so that `:services` don't need to depend on `:data` directly (e.g. `:data:applications`, `:data:files`, `:data:calendar`, `:data:contacts`, `:data:websites`, `:data:wikipedia`, `:data:widgets`, `:data:database` (Room), `:data:plugins`, `:data:locations`).
- **`:core`** — foundational, mostly UI-agnostic building blocks: `:core:base` (core interfaces like `Searchable`/`SavableSearchable`, common data classes, utilities), `:core:preferences` (AndroidX Datastore), `:core:permissions`, `:core:i18n`, `:core:ktx`, `:core:compat`, `:core:profiles`, `:core:devicepose`, `:core:crashreporter`.
- **`:libs`** — standalone modules/forked third-party code with no dependency on `:core:base` (`:material-color-utilities`, `:nextcloud`, `:owncloud`, `:webdav`, `:address-formatter`).
- **`:plugins:sdk`** — public SDK (published to Maven Central as `de.mm20.launcher2:plugin-sdk`) that lets third-party apps implement search/weather/calendar plugins via content providers, without depending on internal launcher modules.

Note module naming is not perfectly consistent between directory layout and Gradle path — check `settings.gradle.kts` for the authoritative module list.

Dependency injection: **Koin**. Nearly every module has a `Module.kt` at its root defining a Koin module exposing that module's APIs to others. All per-module Koin modules are aggregated and started in `LauncherApplication.onCreate()`.

Other key libraries: Jetpack Compose + Accompanist (UI), Coil (images), KotlinX coroutines/serialization, AndroidX Room (database) and Datastore (preferences), Ktor (HTTP).

### Search architecture

Search is the core feature. `core/base` defines the common `Searchable`/`SavableSearchable` abstractions and `SearchableRepository` interface; each `:data:*` module (applications, files, contacts, calendar, calculator, websites, wikipedia, locations, unit converter, search-actions, etc.) implements search for its domain, and `:services:search` composes these into the unified search experience surfaced by `:app:ui`.

### Plugin system

External apps can extend Kvaesitso (weather, file/contact/places search, calendar) by implementing content providers using `plugins/sdk`, without needing the launcher's internal modules. `:data:plugins` holds low-level plugin APIs/access control; `:services:plugins` is the higher-level plugin management service (enable/disable/list). See `docs/docs/developer-guide/plugins/` for the plugin type contracts and `docs/docs/developer-guide/plugins/access-control.md` for the security model.

## Telos Linux Desktop — pending phases (do not forget)

`:desktop-linux` P0/P1 (labwc session, vault as full `$HOME`, Linux only) is in the tree. See `docs/docs/developer-guide/desktop-roadmap.md`.

If the user asks what is still pending for the Linux desktop: **none of P0–P4**. Never depend `:app:app` on `:desktop-linux`. `:app:ui` may use `:ui-kmp`.

## Documentation

Docs live in `docs/` (a VitePress site, published as GitHub Pages at https://koukomin.github.io/Telos-Launcher/). Every user-visible or architectural change must update, **in the same change**, `readme.md`, the matching `docs/docs/` pages, and `THIRD_PARTY_NOTICES.md` when a library or license changes. Check `docs/docs/developer-guide/` before structural changes. Do not describe anything that is not implemented.

## Contributing conventions

- For bug fixes and smaller enhancements, PRs can be sent directly.
- For bigger new features, an issue should be opened first to discuss design before implementation.
- All contributed code must be GPL-3.0-or-later licensed (except contributions to `plugins/sdk` / `core/shared`, which are Apache-2.0).

## Performance, memory and APK size rules

Telos runs as the home screen, so every agent (Devin, Copilot, ChatGPT, Claude) working on this repo follows these rules. Fix real problems; do not refactor working code only to match a checklist.

**Main thread**
- No disk, network, database, `ContentResolver` or call-log work on the main thread. Use `suspend` functions with `withContext(Dispatchers.IO)` (or `Default` for CPU work), and collect `Flow`s instead of reading them once.
- No new `runBlocking` in app code. Inside a coroutine, call `first()` directly. Outside one, launch in a scope (`containedScope(...)` from `core:base`, `lifecycleScope`, `viewModelScope`) and come back to `Dispatchers.Main` to touch the UI.
- `SharedPreferences`: use `apply()`. `commit()` is only for writes that must be on disk before the process can die (crash-recovery state such as `VirtualAppGuard`), and never on the main thread.
- Do not start heavy work in `Application.onCreate`. Create engines, decoders and services on first use.

**Compose**
- Wrap derived values in `remember(keys)` or `derivedStateOf`. No sorting, filtering, parsing, `Regex(...)`, `SimpleDateFormat(...)` or object allocation directly in a composable body or in `drawBehind`/`onDraw`.
- Long or unbounded lists use `LazyColumn`/`LazyRow`/`LazyVerticalGrid` with stable `key`s.
- Pass lambdas or `State` down instead of rapidly changing values (scroll offsets, animations) so only the reader recomposes.

**Memory**
- Never keep an `Activity`, `View` or composable-scoped `Context` in a static field, `object`, singleton or long-lived scope. Use `context.applicationContext` there.
- Close `Cursor`s, streams and `ParcelFileDescriptor`s with `use { }`. Recycle or cache bitmaps through the existing caches (`FileThumbs`, Coil); decode them at the size shown.
- Compile `Regex` once (top-level or class-level `val`), not per call or per loop iteration.
- Prefer primitive types (`Int`, `Long`, `IntArray`) in hot loops; avoid boxing collections there.

**APK size**
- Before adding a dependency, check if an existing one already does the job. Pin shared libraries (Bouncy Castle, OkHttp, Kotlin) to one version.
- Keep rules must be narrow: keep only what is loaded by reflection or JNI, never `-keep class some.library.** { *; }` for a whole library without a reason written next to it.
- Large assets (fonts, models, lookup tables) need a reason. Exclude unused library data files in `packaging.resources.excludes`.
- A new language needs its code added to `localeFilters` in `app/app/build.gradle.kts`, or its translations will not be packaged.
- Native libraries stay split per ABI (`-Ptelos.abi`). Do not add x86 or universal builds.
- Release and nightly builds keep R8 minification and resource shrinking on.

## Keeping README, docs and Pages up to date

`readme.md` is the public face of the project. The VitePress tree in `docs/` is the GitHub Pages site. Whenever you add or change a user-visible feature, or you port, adapt or take design from another project, update **readme, docs and Pages in the same commit**:

- add the feature to the matching list under "Features" (move it out of "Planned" if it was there);
- update the matching user-guide page under `docs/docs/user-guide/` and, for internals, `docs/docs/developer-guide/`;
- add or update the project in the "Projects Telos is based on" table with its link and license
  (check the license is GPL-3.0 compatible before copying code) and keep its copyright notice in
  `THIRD_PARTY_NOTICES.md`;
- do not describe anything that is not implemented.

## User workflow preferences

- Add necessary UI translations for every change, for all supported languages, with Greek as the priority alongside English.
- Update README, docs and Pages source content when the behavior changes.
- Push development changes to the working PR branch. Do not merge or create a release without the user explicitly asking.

## Translations

Every new or changed user-visible string must be translated into **all** supported languages **in the same change**. English goes in `core/i18n/src/main/res/values/` first. Greek (`values-el`) is the first locale after English. Then fill every other `values-*` folder. Never leave a language with an English fallback for a string you added.

Supported: Greek, German, Spanish, French, Italian, Portuguese (pt, pt-BR), Russian, Turkish, Dutch, Polish, Czech, Slovak, Slovenian, Croatian, Bulgarian, Romanian, Hungarian, Danish, Swedish, Finnish, Estonian, Latvian, Lithuanian, Irish, Maltese, Ukrainian, Norwegian, Chinese (zh-CN, zh-TW), Japanese, Korean, Arabic, Hindi, Bengali.

Use `tools/i18n/` (see its README): `extract.py` for missing keys, `build.py` to write `strings_telos_translations.xml`. Keep placeholders (`%1$s`, `%d`, XML tags) unchanged.

## User workflow preferences

- Add necessary UI translations for every change, for all supported languages, with Greek as the priority alongside English.
- Update README, docs and Pages source content when the behavior changes.
- Push development changes to the working PR branch. Do not merge or create a release without the user explicitly asking.
