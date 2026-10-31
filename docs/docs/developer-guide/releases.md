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

Full Linux validation also checks archive paths: absolute paths and traversal
entries are rejected. Split-archive fixtures use stored data so the test actually
creates multiple volumes; ACE-root tests create the required archive fixture.

Both PR and production builds verify that the APK contains `.so` files for
exactly the selected ABI. Packaging exclusions follow `telos.abi`, including
`armeabi-v7a`; a Java-only APK cannot pass the release gate.

CodeQL uses a manual, uncached compilation with incremental compilation disabled.
Restoring compiled Gradle outputs would leave CodeQL without compiler activity
and fail analysis with "No source code was seen during the build".

## GitHub Actions runtime maintenance

All JavaScript Actions use Node.js 24 runtimes. Current supported majors are
checkout v7, setup-java v6, setup-node/setup-python/setup-go v7, Gradle setup v6,
cache v6, upload-artifact v7, download-artifact v8, CodeQL v4, stale v11,
upload-pages-artifact v5 and deploy-pages v5. The emulator runner's v2 major
already uses Node.js 24. Action major versions are independent of the installed
JDK: Android and Linux continue to build with the project's JDK 21 toolchain.

Documentation builds select `lts/*` with `check-latest: true` for the installed
Node.js version. Actions use their maintained major tags to receive compatible
patch/minor updates. `.github/dependabot.yml` checks for new Action versions daily
at 06:00 Europe/Athens, including major updates, and groups them into a PR for CI.
On forks, enable **Settings > Advanced Security > Dependabot version updates**
once; committing a config alone does not activate version updates on a fork.

The runtime compatibility workflow exercises Java, Node, Python, Go, Gradle,
cache, artifact upload/download and Pages artifact packaging, and builds the docs.
It never deploys Pages or publishes a release. Gradle v6 explicitly selects
`cache-provider: basic`, its open-source cache provider; it does not select the
commercial enhanced caching component introduced in v6.

Upstream references: [setup-java](https://github.com/actions/setup-java),
[Gradle Actions](https://github.com/gradle/actions), and
[Dependabot Actions updates](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/auto-update-actions).
