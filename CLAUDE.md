# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Kvaesitso — a search-focused, free and open source Android launcher (package `de.mm20.launcher2`). Kotlin/Gradle multi-module Android project. Licensed under GPL-3.0, except `plugins/sdk` and `core/shared` which are Apache-2.0.

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

## Documentation

Docs live in `docs/` (a VitePress site, published at https://kvaesitso.mm20.de) — check `docs/docs/developer-guide/` before making structural changes, and update it if module structure, plugin contracts, or external API integrations change.

## Contributing conventions

- For bug fixes and smaller enhancements, PRs can be sent directly.
- For bigger new features, an issue should be opened first to discuss design before implementation.
- All contributed code must be GPL-3.0-or-later licensed (except contributions to `plugins/sdk` / `core/shared`, which are Apache-2.0).
