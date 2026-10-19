# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project

Telos — a search-focused, free and open source Android launcher (package `de.mm20.launcher2`). Kotlin/Gradle multi-module Android project. Licensed under GPL-3.0, except `plugins/sdk` and `core/shared` which are Apache-2.0.

## Build & common commands

- Build environment: JDK 21 toolchain (see `gradle/gradle-daemon-jvm.properties`), Gradle 9.6.0 via wrapper.
- Build variants are a combination of a build type (`debug`, `release`, `nightly`) and a product flavor (`default`, `fdroid`), e.g. `assembleDefaultDebug`, `assembleFdroidRelease`.
- Build a debug APK: `./gradlew assembleDefaultDebug`
- Install to a connected device/emulator: `./gradlew installDefaultDebug`
- Run all unit tests: `./gradlew test`
- Run tests for a single module: `./gradlew :core:base:test` (module paths mirror the directory structure, e.g. `:data:locations:test`)
- Run a single test class: `./gradlew :core:base:test --tests "de.mm20.launcher2.OpeningScheduleTest"`
- Lint is non-fatal (`lint.abortOnError = false` in `app/app/build.gradle.kts`).
- Unit tests exist in `core/ktx`, `core/base`, `data/locations`, `services/comms`, `services/store` and `data/store`; most other modules have no test suite.
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

## Documentation

Docs live in `docs/` (a VitePress site, published as GitHub Pages at https://koukomin.github.io/Telos-Launcher/ and historically at https://telos.mm20.de). Every user-visible or architectural change must update, **in the same change**:

- `readme.md` (feature lists, based-on table, status text);
- the matching pages under `docs/docs/` (user guide and developer guide);
- `THIRD_PARTY_NOTICES.md` when a library or license changes.

Check `docs/docs/developer-guide/` before structural changes. Do not describe anything that is not implemented.

## Contributing conventions

- For bug fixes and smaller enhancements, PRs can be sent directly.
- For bigger new features, an issue should be opened first to discuss design before implementation.
- All contributed code must be GPL-3.0-or-later licensed (except contributions to `plugins/sdk` / `core/shared`, which are Apache-2.0).

## Keeping README, docs and Pages up to date

`readme.md` is the public face of the project. The VitePress tree in `docs/` is the GitHub Pages site. Whenever you add or change a user-visible feature, or you port, adapt or take design from another project, update **readme, docs and Pages in the same commit**:

- add the feature to the matching list under "Features" (move it out of "Planned" if it was there);
- update the matching user-guide page under `docs/docs/user-guide/` and, for internals, `docs/docs/developer-guide/`;
- add or update the project in the "Projects Telos is based on" table with its link and license
  (check the license is GPL-3.0 compatible before copying code) and keep its copyright notice in
  `THIRD_PARTY_NOTICES.md`;
- do not describe anything that is not implemented.

## Translations

Every new or changed user-visible string must be translated into **all** supported languages **in the same change**. English goes in `core/i18n/src/main/res/values/` first. Greek (`values-el`) is the first locale after English. Then fill every other `values-*` folder. Never leave a language with an English fallback for a string you added.

Supported: Greek, German, Spanish, French, Italian, Portuguese (pt, pt-BR), Russian, Turkish, Dutch, Polish, Czech, Slovak, Slovenian, Croatian, Bulgarian, Romanian, Hungarian, Danish, Swedish, Finnish, Estonian, Latvian, Lithuanian, Irish, Maltese, Ukrainian, Norwegian, Chinese (zh-CN, zh-TW), Japanese, Korean, Arabic, Hindi, Bengali.

Use `tools/i18n/` (see its README): `extract.py` for missing keys, `build.py` to write `strings_telos_translations.xml`. Keep placeholders (`%1$s`, `%d`, XML tags) unchanged.

## User workflow preferences

- Add necessary UI translations for every change, for all supported languages, with Greek as the priority alongside English.
- Update README, docs and Pages source content when the behavior changes.
- Push development changes to the working PR branch. Do not merge or create a release without the user explicitly asking.

## Mandatory Telos UI and icon design contract (all coding agents)

These instructions are binding for any AI coding assistant or contributor working in this repository. Read `AGENTS.md` and `docs/ICON_DESIGN.md` before editing any Telos virtual app, Dialer, launcher icon, UI resource or icon pipeline. Treat these as acceptance criteria, not suggestions. Do not claim implementation from documentation alone.

### Dialer: large contact photograph

- Incoming, outgoing and in-call screens must show a prominently large contact photograph, inspired by the visual prominence in Google Phone, without copying proprietary assets.
- Use a responsive edge-to-edge photo region where appropriate, preserving clear caller identity, number, call status, controls, accessibility and safe areas. Account for compact screens, tablets, landscape, large font sizes, cutouts and gesture/navigation insets.
- Support contact photo fallback to a polished initials/avatar design; never stretch low-resolution images or obscure critical actions.
- Keep the existing call-state logic, telecom permissions, accessibility labels and functional call controls intact. Add UI previews and tests for photo/no-photo, light/dark, incoming/outgoing/active and large text.

### Flat adaptive icon system

- The default launcher icon is the quality baseline. All Telos virtual-app icons must form a cohesive **Telos Flat Adaptive** family.
- Follow the detailed mandatory specification in `docs/ICON_DESIGN.md`. Do not use glossy, skeuomorphic, faux-3D, complex-gradient, shadow-heavy or inconsistent icons.
- Implement foreground/background layers with Android adaptive icon masks, keeping recognizable content within the safe zone across circle, square, squircle, rounded-square and OEM masks. Never bake the final mask shape into the artwork.
- Use a shared vector-based geometry and color-token system; icons must be crisp at small sizes, distinguishable from official third-party brand icons, and coherent in light/dark/monochrome modes.
- Verify all Telos virtual apps, app drawer, dock, search results, sidebar and system surfaces use the correct icons. Where the user chooses an icon pack or custom icon, honor that selection in launcher-controlled surfaces unless a documented exception applies.
- Before completion, produce a contact sheet showing every affected icon under multiple masks and on light/dark backgrounds; validate visually and update documentation, localization and tests where applicable.

### Delivery gates

- Implement and verify behavior in code, not only in screenshots or prose. Do not mark tasks complete without a passing build and relevant tests or an explicit explanation of what could not be tested.
- Preserve existing launcher functionality and avoid unrelated UI changes.
- Submit changes in a PR; do not merge or release unless explicitly instructed.

## Mandatory UI design for all AI coding agents

Before modifying virtual apps, Dialer, icons or app surfaces, read `docs/ICON_DESIGN.md` and comply with its acceptance criteria. Dialer must show a large contact photo (Google Phone-inspired prominence, no copied proprietary assets) on incoming, outgoing and active calls; provide initials fallback, responsive layout, safe controls, large-text accessibility and tests. Keep user-selected icon packs and custom icons on launcher-controlled surfaces. Update translations, README and docs with implemented changes. Never claim completion without build and visual QA.
