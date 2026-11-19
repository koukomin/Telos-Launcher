# Embedded Amethyst in Aether

Aether composes the real Amethyst Android client inside the Telos APK. It does
not install, launch or require an external Amethyst APK. The integration is included
in version 1.45.71. Earlier published versions contain the local preview.

## Source and distribution

`libs/amethyst` preserves the MIT-licensed Android client and its `commons`,
`commonsUI`, `nestsClient`, `nappletHost` and test relay modules at revision
`75bb07fa3adf4ef5d590bb5379d87f01c139a430`. The application module is an Android
library selected through a composite build. Telos selects the F-Droid distribution;
Google Play-specific Firebase, Cast and Google AI extensions are not included.
The Play source set is retained for source provenance, with no Firebase key shipped.

## Launcher cold-start regression

Real-APK instrumentation found that asynchronous Koin module registration raced
with startup consumers: resolving `DesktopModeManager` crashed the process before
the Aether screen appeared. All definitions are now registered synchronously;
eager instances remain deferred to a background coroutine. Startup hooks that
need Koin begin only after registration. The same real-APK login/restart test is
the regression gate. This does not initialize the Nostr graph at launcher startup.

## Host boundaries

- `LauncherApplication` defers client initialization until Aether or one of its
  background entry points needs it. Initialization is serialized on IO. The
  keyless `:napplet` sandbox cannot initialize an account or start launcher
  Koin, preferences and privileged transports. The standalone client's
  process-wide thread-priority polling governor is not started in the host: it
  would also demote launcher, call-lookup and messaging workers.
- `EmbeddedAether` renders the upstream app, account manager, feeds, settings,
  relays, messaging, media and signer workflows. Multiple accounts use the
  original session manager; adding, switching and removing accounts stay available.
- `AetherStorageContext` places files and databases under `noBackupFilesDir/aether`.
  Encrypted account preferences retain the original encryption and separate
  namespaces in `aether_private.xml`, excluded from both Android backup formats.
  Clearing one account namespace cannot clear another account or launcher settings.
- Native entry activities redirect into the existing Telos document-task host.
  Nostr links, shares, attachment ClipData and URI grants pass through the bridge.
  Both logged-in and logged-out navigation filter internal Telos task identity
  URIs through that bridge, without consuming the host's Recents identity.
  Telos cannot offer itself as an external Nostr Connect signer.
- The default appearance inherits launcher colors, typography, shapes and font
  scaling. Explicit client accent colors remain selectable. Settings cards,
  headings and section labels use the host Cybertech components; drawers and
  floating login cards use opaque tonal containers over the shared root backing.
  `VirtualAppWindow` supplies the same shared wallpaper/glass backing as other
  Telos apps. The client surface role is opaque for nested dialogs; only page
  background inherits transparency. Inherited dark/light state follows the host palette.
  Login uses the Aether name and existing Rondo glyph; the imported decorative
  background glow is disabled in the host to preserve the single root backing.
- Workers and push callbacks await initialization. Aether notifications use their
  own tag; clearing them does not cancel Telos alarms or downloads. Media playback
  uses notification ID 7407, distinct from the other Telos players. Client
  reminder, signer, call, room, upload and Tor notifications also use the host
  Aether foreground glyph; account avatars and media artwork remain intact.

## Included-build dependency security

The 1.45.70 tag is an unpublished validation candidate. Its publication was held
when [dependency review](https://github.com/koukomin/Telos-Launcher/actions/runs/44351458402)
identified vulnerable transitives in the included build, despite the launcher's
existing constraints. Version 1.45.71 applies matching security constraints to
both plugin and project classpaths in Amethyst's own settings. The pins cover
Bouncy Castle 1.87, Guava 33.8.0, JDOM 2.0.6.1, jose4j 0.9.7, HttpClient 4.5.14,
Commons Lang 3.22.0 and OpenTelemetry API/context 1.68.0. Guava keeps its Android
artifact on Android compile/runtime classpaths and its JRE artifact for tools.

`verifyBuildSecurityDependencies` resolves real buildscript and Android runtime
graphs in both builds and compares affected components against advisory minima.
[Dependency review](https://github.com/koukomin/Telos-Launcher/actions/runs/44355647038)
reports zero added vulnerable components against v1.45.69 for `6837a4661`.
The integrated candidate at `224a9b991`, including the latest main-branch changes,
was also reviewed in [run 44407609261](https://github.com/koukomin/Telos-Launcher/actions/runs/44407609261):
635 dependency changes, zero added vulnerable components against v1.45.69.
The follow-up at `3853e1a9f` changes only synchronous shell-status handling;
its actual plugin/runtime security graphs and compilation passed the validation
run below. This is dependency-review evidence, not a claim that the private
Dependabot alert list is empty.

## Validation

[Run 44408135991](https://github.com/koukomin/Telos-Launcher/actions/runs/44408135991)
validated application revision `3853e1a9f`, after incorporating main at `4912fb056`:

- Actual ARM64 Telos debug and minified/resource-shrunk release APK builds passed.
- The library suite executed 1,526 tests: no failures/errors, nine upstream skips.
- All four Android encrypted-storage, namespace/editor, database-path and
  notification-isolation regressions passed without skips.
- The real-Telos external UI driver passed public-key login, adding two accounts,
  switching to the first, settings navigation and restoring both accounts after
  force-stop/relaunch. It also captured settings at system font scale 2.0 in
  portrait and landscape. No real private keys or publication were used.
- Shell, communications, app-management and freeze unit-test tasks passed. A
  suspend-call compilation error introduced on main was corrected with a
  non-blocking cached root-status query; real root probing stays asynchronous.
- Both builds' resolved plugin/runtime security checks and the Dokka graph passed.
  Release helper tests and the static Pages build also passed locally; see the Dependabot guide for the dated
  docs-tool audit history and the scoped review of upstream scanner fixtures.

An earlier revision's first device attempt was blocked by a Pixel Launcher ANR
dialog; retrying that unchanged revision on a fresh emulator passed. The final
run above passed its device checks on the first attempt.
The earlier administrative PR-creation job was removed: this repository does not
permit GitHub Actions to create or approve pull requests.

### Reviewed captures

These captures come from the tested revision, using public read-only accounts,
English and the light host theme. Login no longer reports the internal Telos task
URI as an invalid Nostr link. Settings cards and account sheets remain readable,
including font scale 2.0 and landscape; both accounts survive process restart.
They establish this rendering scope, not the full wallpaper/theme/locale matrix.

| Screen | Evidence |
| --- | --- |
| Aether login | [Capture](../../screenshots/development/aether/aether-login.png) |
| Feed with the read-only fixture (no live relay data) | [Capture](../../screenshots/development/aether/aether-feed.png) |
| Account settings | [Capture](../../screenshots/development/aether/aether-settings.png) |
| Settings at font scale 2.0 | [Capture](../../screenshots/development/aether/aether-settings-large-font.png) |
| Font scale 2.0 in landscape | [Capture](../../screenshots/development/aether/aether-settings-large-font-landscape.png) |
| Two accounts | [Capture](../../screenshots/development/aether/aether-multiple-accounts.png) |
| Accounts after process restart | [Capture](../../screenshots/development/aether/aether-restored-accounts.png) |

APK jobs retain separate Gradle task-output caches so library cache writes do
not discard launcher compilation outputs. The device APK is x86_64 solely for
validation. The release pipeline additionally builds ARM64, ARMv7 and x86_64
APKs; these captures establish the x86_64 debug rendering scope. Captures go
through MediaStore and survive test-APK removal. The evidence branch records the tested revision.

Live relay interoperability, signing/publication, external signers, physical
OnePlus/Android 17 behavior and the complete theme/font/wallpaper matrix are not
established by these automated checks. The shipped distribution is the full
F-Droid client; proprietary Play-only integrations are not included.

## Native and code-scanning follow-up

[Run 44421180096](https://github.com/koukomin/Telos-Launcher/actions/runs/44421180096)
rebuilt Arti for all four Android ABIs and the Linux x86_64 JNI test fixture
with Rustls 0.23.46. The dependency lock is resolved against the same patched
Arti source used by the build scripts, rather than registry replacements for
local workspace crates. `TELOS_NATIVE_BUILD.json` records source, compiler,
NDK, input and binary hashes. `tools/validation/aether-native-check.py` rejects
stale lockfiles, wrapper sources, toolchains or binaries before validation and
release packaging; positive checks and deliberately mismatched input/binary
hashes were exercised locally.

Inherited code-scanning findings 26–28 are addressed by binding biometric
approval to the returned, request-specific Keystore cipher, checking credential
fallback with an authentication-required key, constructing reply intents with
an explicit receiver, and using only literal archive output suffixes. Proof
keys are separate from account encryption keys; upstream behavior without a
configured device screen lock is retained. Android JNI, intent-routing and
emulator-only Keystore boundary regressions accompany the changes. Their final
Actions validation is required before publication; live Tor bootstrap and
physical biometric prompts are not certified by these tests.

The broader CodeQL SARIF review also reports an implicit intent in Island's
freeze provider. The runtime intent already had a resolved component and package, but constructing a new blank intent had discarded the FREEZE or
UNFREEZE action. The provider now shares an explicit, package-checked intent
contract that retains the documented action and data. Android contract tests
exercise freeze/unfreeze and foreign-target rejection without installing Island
or freezing applications; they do not certify an actual managed-profile freeze.
