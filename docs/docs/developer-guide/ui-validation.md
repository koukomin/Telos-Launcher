# UI validation record

This record distinguishes source-level implementation, automated checks and observed rendering.
It accompanies the adaptive UI branch based on merged PR #46 (main merge `feaff22`, head `211d1fac`).

## Implemented patterns

See [Adaptive UI contract](./adaptive-ui) for the in-repo scope, component contracts, completion
criteria and test matrix. Shared Material theme tokens remain the palette/type/shape source;
no new production library or replacement design framework is introduced.

## Execution evidence

The recovered cloud checkout contains 17 JUnit result XMLs: **86 tests, zero failures/errors/skips**,
including eight adaptive layout tests and ten contrast tests. Those results predate reconciliation
with newer main `8e3110c`; they are historical evidence, not validation of the reconciled commit.
The initial Calendar dp-expression compile failure was fixed before those tests.

The final PR CI runs the complete `:app:ui:testDefaultDebugUnitTest` suite, compiles the
instrumented tests, builds a debug APK and runs focused `:app:ui:lintDefaultDebug` with
`tools/validation/ui-lint.init.gradle`. The focused IDs are NewApi, InlinedApi,
ContentDescription and RtlHardcoded; dependency checking is off. This is not full app lint.
The authoritative reconciled outcomes, final head and artifact links are recorded in
[PR #47](https://github.com/koukomin/Telos-Launcher/pull/47); its checks must pass before merge.

On `f1714ec`, [validation run 39430166171](https://github.com/koukomin/Telos-Launcher/actions/runs/39430166171)
passed all UI unit tests, instrumentation compilation and debug APK build. Focused lint then
reported nine NewApi errors. The follow-up fixes remove an unused API-28 class reference,
type the contact grouping as Map to avoid a JDK-21 SequencedMap cast, use PackageInfoCompat and
the older URI ContentResolver.call overload, and guard API-29 recovery, API-31 wallpaper hints
and API-28 profile controls. No check was disabled or suppressed.

[Instrumented run 39430166272](https://github.com/koukomin/Telos-Launcher/actions/runs/39430166272)
executed **three tests, zero failures/errors/skips** on Android 35. The job failed afterwards
because Gradle uninstalled the test APK before adb could retrieve its cache PNGs. Screenshot
output now uses shared MediaStore images on API 29+ so evidence survives test-app cleanup;
the final check/artifact links in PR #47 establish collection and visual-review outcomes.

Reconciliation preserves all main commits through `8e3110c`, including freeze/work-profile rules,
Terminal runtime, Files FTP/APK extraction and direct TV links. PR #45 is not included.

## Visual closure

Do not mark this phase visually complete from a successful compile or JVM policy suite.
Hardware-backed emulator acceleration is unavailable in this cloud: `emulator -accel-check`
returned exit 3, `KVM requires a CPU that supports vmx or svm`; `/dev/kvm` and CPU vmx/svm flags
are absent. The Android 30 x86_64 software emulator booted with `-accel off` / SwiftShader, but the
captured system home screenshot showed **“System UI isn't responding” before Telos was installed**.
This is a host/emulator limitation, not a Telos crash. That local attempt completed no Telos screenshot matrix or instrumented
execution; the later CI component tests described above are separate evidence. The runtime interruption also discarded temporary SDK/log/screenshot files;
only repository build reports survived. No light/dark/wallpaper UI visual pass is claimed. Hardware camera, calls/SIP,
recording, account mail/sync and third-party live/widget internals still require suitable devices,
permissions/accounts and real content.
