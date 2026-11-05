# Dependabot alerts

Coding agents must fetch open Dependabot alerts and apply the patched versions
without waiting for a separate user request.

## Priority

1. **CRITICAL**
2. **HIGH**
3. **MODERATE**
4. **LOW**

CRITICAL and HIGH run before unrelated feature work in the same session.

## Where to read them

- GitHub GraphQL: `repository.vulnerabilityAlerts(states: OPEN)`
- REST: `/repos/koukomin/Telos-Launcher/dependabot/alerts`
- `.github/daily-dependency-check.json` only records that the daily job ran. It
  is not the alert list.

## How to fix

**Gradle / Maven.** Many alerts point at `settings.gradle.kts` because GitHub
attributes plugin-classpath transitives (Dokka, AGP, Kotlin) to that file.
Pin those on `gradle.beforeProject { buildscript.configurations }` in
`settings.gradle.kts` (Guava `-jre` on the plugin classpath). Jackson core/databind may be 2.22.4; pin `jackson-annotations` to `2.22`
(there is no 2.22.4 annotations artifact). Do not `useVersion` the core patch
onto annotations. Pin app graphs in
the root `subprojects { configurations.configureEach { resolutionStrategy.eachDependency } }`
block (Guava `-android` there). Keep one Bouncy Castle version
(`libs.versions.bouncycastle`, currently 1.86). App `useVersion` calls must use
that catalog entry. `.github/workflows/dependency-graph.yml` submits the
resolved Gradle graph so Dependabot can close alerts after the pins land.

**Docs (npm).** VitePress pulls vite, esbuild, postcss, nanoid, rollup and Vue.
Bump `docs/package.json` and set `overrides`, then regenerate
`docs/package-lock.json`. `npm audit` in `docs/` must be clean or each leftover
must be explained.

Do not dismiss an alert without a written false-positive. Do not take a
GPL-incompatible upgrade. Do not merge or release unless the user asked.

Dependabot version updates (`.github/dependabot.yml`) cover GitHub Actions,
Gradle and the docs npm lockfile on a daily Europe/Athens schedule.

## Built-in Shizuku pairing crypto

Pairing builds `io.github.vvb2060.ndk:boringssl:20251124` (non-LTO prefab) for the pinned
NDK. The packaged headers and pinned source carry Apache-2.0 notices; the Maven POM's
older OpenSSL/ISC license metadata is stale. Source revision and full license/author notices
are recorded in `THIRD_PARTY_NOTICES.md` and APK assets. Do not replace it with the older
20250114 artifact without checking its different licensing.

Certificate construction uses `bcpkix-jdk18on` at `libs.versions.bouncycastle`, with the
existing root resolution pin keeping Bouncy Castle modules at one version. The existing
Jackson/jsoup/FreeMarker security pins are retained. The pairing regression workflow checks
both ARM native payloads and compiles the actual UI; physical-device evidence is recorded in
the [pairing audit](./shizuku-pairing.md).
