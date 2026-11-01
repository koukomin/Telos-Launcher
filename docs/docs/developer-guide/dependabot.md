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

**Gradle / Maven.** Many alerts point at `settings.gradle.kts` while the real
version lives in `gradle/libs.versions.toml` or in a transitive graph. If the
direct catalog version is already patched, pin every remaining copy in the root
`subprojects { configurations.configureEach { resolutionStrategy.eachDependency } }`
block. Keep one Bouncy Castle version (`libs.versions.bouncycastle`, currently
1.86). App `useVersion` calls must use that catalog entry.

**Docs (npm).** VitePress pulls vite, esbuild, postcss, nanoid, rollup and Vue.
Bump `docs/package.json` and set `overrides`, then regenerate
`docs/package-lock.json`. `npm audit` in `docs/` must be clean or each leftover
must be explained.

Do not dismiss an alert without a written false-positive. Do not take a
GPL-incompatible upgrade. Do not merge or release unless the user asked.

Dependabot version updates (`.github/dependabot.yml`) cover GitHub Actions,
Gradle and the docs npm lockfile on a daily Europe/Athens schedule.
