# Android and Linux releases

`version.properties` is the single source for Android versionName/versionCode,
Linux package version and JAR Implementation-Version, and the GitHub release tag.
Release version codes are committed positive integers and must increase with each
release; they are not generated from a workflow counter or date. The initial
unified release retains a code above the previously committed 2026110505.

1. Increase both values in `version.properties` and merge the reviewed PR into main.
2. Push the matching tag (`v<versionName>`) at that main commit, or dispatch
   **Release Android and Linux** on main. Do not run both for the same version.
3. `build-release.yml` validates the version, builds both ARM Android release APKs
   using the repository signing key, tests/builds Linux with JDK 21, and collects
   all artifacts before creating a draft. It publishes and marks Latest only
   after every required asset uploads successfully.

The workflow rejects mismatched/older tags, dispatches from other branches,
and already-published versions. A failed upload leaves a draft for a retry.
Missing signing secrets fail the job instead of generating a new signing key.
PR validation builds both Android ABIs without repository secrets and tests/builds
Linux; it cannot publish. The Android profile uses class patterns and explicit
method descriptors, avoiding the invalid trailing return-type wildcard that
crashed profgen in previous release builds. Device startup performance is not
certified by this build check.

`release.yml` (the independent `v1.0.<run_number>` publisher) has been removed.
Debug and F-Droid workflows upload Actions artifacts only; Linux validation uses
JDK 21 and also uploads artifacts. Only `build-release.yml` publishes stable
application releases. The separate `sip-libs` prerelease is a native build input,
not an application release. Historical tags/releases are retained.

Download `telos-arm64-v8a.apk` for modern ARM phones or `telos-armeabi-v7a.apk`
for 32-bit ARM. Linux users need the JAR, launcher script, and Java 21 or newer;
Linux session scripts are supplied in `telos-linux-session.tar.gz`.
