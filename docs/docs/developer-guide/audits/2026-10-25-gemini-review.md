# Review of changes after Notes/Morse PR #64

Reviewed baseline: main `435ae1acebd88750a3c399dde878eba215f9ee03` (2026-10-25), including the changes since merge `876719bcef`. Commit author metadata is the repository owner; the grouping “Gemini changes” follows the user's description, not an independently verified AI identity. The translation-only changes account for most of the 4,079 changed paths. This is a source/CI audit, not certification of every device, account or UI state.

## Crash and corrections in this change

The supplied 1.42.11 crash identifies `SavedWifiParser`'s static initializer. Android ICU rejects the unescaped final `}` in `network\\s*=\\s*\\{([^}]*)}`; the desktop Java regex engine accepted it, so the existing JVM test missed the crash. The closing brace is now explicitly escaped and a regression test executes on Android. The recent `6e224cff4f` “Wi-Fi crash fix” changed only the preference UI, leaving this parser broken; one direct dashboard reveal path still propagated the initializer error.

| Area | Finding | Correction |
| --- | --- | --- |
| Wi-Fi reveal | Authentication exceptions/no Activity defaulted to success | Fail closed; cancel reveal jobs and clear list/QR on backgrounding |
| Wi-Fi QR | Unescaped delimiters; missing passwords advertised as open/WPA networks | Escape QR delimiters; disable key-dependent actions when the key was not read |
| Wi-Fi JSON backup | `EXTRA_TEXT` with JSON MIME type pretended to be an attached backup file; serialization ran on Main | Actual CreateDocument JSON write on IO, fresh authentication after document picking, cleanup on failed export |
| UAD descriptions | Second HTTP updater/map, no bundled fallback, no shared update opt-out | Adapter to existing validated BloatDescriptions; one offline fallback, cache and update policy |
| Installed apps | Package labels, APK sizes and frozen-state queries ran in composition; row navigation passed a package as a Store item ID | Read immutable row snapshots on IO; tracked rows resolve real Store IDs, others open native app settings |
| Installed apps | Virtual-app filter operated on host package prefixes, not virtual apps | Remove that ineffective filter; real installed-package enumeration retains disabled/system apps |
| Freeze buttons | No operation outcome | Report successful package membership from the actual freeze result, refresh snapshot |
| Firewall / per-app audio focus | System/Store firewall booleans were disconnected from the existing Network engine; audio-focus preferences have no enforcement consumer | Link installed-app and Store details to an exact-package scope of the real Network firewall; keep audio-focus controls unavailable |
| Component blocker | Root probe displayed “Active” without any component mutation UI/engine | Remove root probe and false active claim; provide explicit root-only edits in System package details |
| Tweaks | Seven controls were no-ops, non-portable properties, or a gesture-nav overlay unrelated to hiding the pill | Disable those controls with unavailable summaries, retain implemented settings separately |
| Tweaks executor | “Shizuku” path actually launched the ordinary app shell; unbounded raw threads and default-only states | Real Shizuku process, ViewModel coroutine serialization, timeout/stream cleanup, read existing supported settings |
| YouTube credentials | Plaintext shared preferences; substring URL matching; redirects could forward Cookie to another host | Keystore encryption/migration; HTTPS/domain boundary checks; redirect-hop credential stripping |
| YouTube cache | One file reused between accounts, unbounded disk fallback; ordinary 400/402 failures expired the session | Account-scoped, bounded, atomic cache; clear on logout/expiry; reject results after account changes; auth-specific expiry policy |
| YouTube account UI | No native logout; stale expired banner after re-login | Local logout and scoped YouTube-cookie expiry; reset expired state on a valid reload |
| Login WebViews | Unrestricted navigation, mixed content, missing cleanup, incorrect NextDNS JavaScript JSON decoding | Exact trusted HTTPS navigation, no mixed/file/content access, destruction on disposal, JSONTokener decoding |
| Audio effects | One session stole another's effects; native resources not released on player destruction | Separate native resources per live session, shared settings, per-session cleanup; unsupported effect creation cleans up independently |
| 200% volume | Video slider changed a number without gain; dialog applied +20dB (10x amplitude) instead of 2x | Wire the video rail to actual gain, cap at +6.02dB, retain system-volume control; test conversion |
| Dead media additions | Exported empty audio service, unreferenced mini player and sleep timer duplicated existing working players/timers | Remove uncalled duplicates; existing Music/Radio/YouTube/video sessions remain |
| Presets | New Frost/Obsidian semantic containers had partial alpha | Opaque inner/container roles; preserve explicit root translucency |
| APK inspection | “Antivirus” and “CLEAN” status inferred only from declared permissions | UI identifies permissions rather than claiming malware detection; correct permission protection mask and current signing certificate inspection |
| AI action | Label promised AI, implementation opened ordinary Google search | Honest Web search label; existing `ai ` / `? ` aliases remain compatible |
| Shizuku bootstrap | Reported success with no running binder | A missing daemon is a failure; enabling wireless debugging alone is not a running Shizuku service |

## UI/backend inventory and remaining limitations

| Capability / representative commits | UI and execution status after corrections | Remaining work / validation |
| --- | --- | --- |
| Notes image sharing and multilingual Morse (`876719bcef`) | PR #64 merged; Morse/Notes sources unchanged by subsequent commits | Greek radio code plus visible romanization for other scripts; physical speaker/torch response still requires device verification |
| Wi-Fi manager (`6e224cff4f`) | Reveal, search, copy, QR and real JSON export are reachable | Root/Shizuku and OEM store permissions still required; legacy parsing does not decode every possible supplicant/key format |
| System Apps migration (`96c9214a11`, `64bdfe3a6b`) | Search/system/frozen filters, app actions and UAD notes wired | All packages now use SystemAppDetailsRoute; Store tracking is not required. This is not a complete Inure replacement |
| UAD integration (`e2868fa05e`) | Shared offline descriptions and existing safety gate | Community *removal* categories are not guarantees that freezing is safe |
| System battery hub (`363c055cbc`) | Card/detail sheet present | Snapshot-only sticky-broadcast read, not continuous live battery telemetry; several labels remain hardcoded |
| Tweaks (`d22f9e81de`, `9a4e0a5466`, `e603f1be1c`) | UI exists; only actual setting-backed controls remain active | OEMs may ignore particular settings; pill, volume-step, scheduled-doze, general pocket/call-flash/caffeinate/status-bar-gesture controls are unavailable |
| YouTube personal library (`137d4b8875`, `379e79168b`, `17a0d31da3`) | Session, library chips, liked-list shortcuts, cache and account UI connected | Private Innertube endpoint/version can change; account-based live login is unverified. Playlist extraction loads an initial page, not every continuation; “all tracks” wording elsewhere remains a limitation |
| YouTube/NextDNS Web sign-in (`f37f83a1de`, `8a86ac4890`) | Dialogs connected, resource/security defects fixed | Providers may reject embedded sign-in. NextDNS localStorage structure is undocumented; manual API-key flow remains the dependable alternative |
| AudioFx/200% (`4225a9ca63`, `64c12f80bf`) | Music dialog and video extra/rail controls invoke native effects | Actual vendor effect support, output distortion and concurrent playback need physical-device testing; radio/YouTube have shared backend settings but no dedicated AudioFx dialog |
| Sleep timer, new audio service/mini player (`905b90040a`) | Existing Music/Video timers and session UIs were already implemented | The added unused equivalents did not constitute a new user-facing integration; removed |
| Frame stepping (`a746e1e5c9`) | Video buttons seek by a nominal frame interval | Not decoder-accurate frame-by-frame output for variable-rate/inter-frame codecs |
| Per-app firewall, focus and component blocking (`f569cd1466`, `2094081cee`) | Firewall now opens the existing per-UID Network rules, scoped to the selected package in both standalone/embedded hosts. Audio focus requests and playback now have read/modify/verify AppOps controls; System details now provide explicit root component edits | Firewall enforcement requires Telos Network VPN on, and shared-UID packages share rules. Forcing another app to ignore focus-loss callbacks remains unimplemented; request blocking is labeled explicitly. Component changes are declared-package scoped, protect critical packages and apply the UAD risk gate on disable; success requires reading back the requested state |
| APK installer/security (`a7e2530727`, `60a61ac43c`, `a746e1e5c9`) | Installer UI and manifest/permission/certificate inspection connected | Heuristics are not an antivirus engine or complete tracker/dex analysis. Session backend may require Android confirmation; only actual privileged backends can install silently |
| Store screenshots (`a85d6c0fd3`) | Changelog-image carousel connected | Not a real provider screenshots API; markup parsing misses some plain image links |
| Store parallel updates/downgrades (`7a193045af` and merged updates) | Settings and execution paths exist; parallel batches bounded to three | Actual downgrade authorization is platform/backend-dependent, not universally guaranteed |
| Screen time / AI search (`b57bd76806`) | Search actions exist | Screen time sums app foreground usage, which can overlap; it is not a measured display-on interval. No AI provider engine was added |
| Contacts/SIM fixes (`5117cd22ba`, `1fb9b3e5b5`) | Repository/UI callbacks wired; dual-active-SIM query handles missing permission | Live multi-account provider permissions, SIM selection and deletion outcomes need device validation |
| Frost/Obsidian (`39a52fc7fd`) | Both bundled presets present; role alpha corrected | Full wallpaper/light/dark/font matrix remains unverified |
| Ancient Greek / Latin (`435ae1aceb`) | Only a small strings.xml per language exists | Not complete translations and not included in existing locale filters; do not advertise full language support yet |
| Translations (`339f0d127f`, `4f31462b08`) | Resource additions exist | “100%” is not established: many new UI literals are English or Greek in Kotlin, outside translation resources |
| Network additions / merged ADNS branch | Private DNS, Wi-Fi automation, scanning, speed test and NextDNS routes/services have reachable UIs and tests | Vendor permissions, live network performance and account APIs still need device/service verification |
| Releases on `435ae1aceb` | Two release workflows ran concurrently on the same source | Latest release endpoint returned v1.42.10 while the report identifies v1.42.11. Version selection/publication should be serialized; this audit does not publish or retarget releases |

## Validation

Added Android ICU initialization/network-boundary/QR tests, host/cookie/expiry policy JVM regressions, and millibel gain tests. Existing Notes/Morse tests are retained. Full Gradle cannot download its distribution in the local environment; GitHub CI is the Android build/test gate. Account, root/Shizuku/OEM effects and the complete visual matrix are not claimed device-verified. CI results for the final PR head are recorded in the pull request.

## Unified System package management

System Apps now routes all installed packages, including iconless/disabled entries, to the same serializable SystemAppDetailsRoute in both hosts. No Store row is manufactured. The package policy dialog accepts a package and user directly, retaining method, delay override, companion rules and Expert approval for iconless apps. The list links to the existing bulk freeze/settings hub.

Debloating is **freeze/unfreeze only**, as requested; this change adds no package uninstall, install-existing or data-clearing operation. Community UAD categories remain removal notes rather than safety guarantees. The list observes the shared validated database and checks updates on entry unless the existing default-on setting is disabled.

System details expose version/target SDK/install-update dates, launch, OS settings, shared Network firewall, full certificate fingerprints, manifest permission names and bounded known-prefix manifest tracker matches. Empty matches say unknown, never clean. APK sharing stages a cache copy, retains every split in a ZIP, and rechecks package metadata before granting the file. A missing split fails instead of exporting an incomplete package.

Component switches enumerate declared activities, services, receivers and providers. Mutations require the Root backend, user confirmation, valid package/class names, protected-package and UAD checks, then a successful state readback. Default restores the manifest setting. System app freezing still uses FreezeManager safety gates. The old disconnected audio-focus preferences are replaced with actual Android AppOps policies for focus requests and playback. This is not a hook that forces another app to ignore focus-loss callbacks.

Validation for this follow-up: translation coverage and diff checks; Gradle cannot download its distribution in the local environment, so the pushed CI build is authoritative. Physical root mutations, installed-app sharing, VPN enforcement and the complete visual matrix remain device-unverified.

### Audio AppOps follow-up

System package details read TAKE_AUDIO_FOCUS and PLAY_AUDIO from the selected root/Shizuku backend. No edit runs on entry. Explicit edits touch only the chosen operation and current user; readback determines success, with best-effort rollback on mismatch. Default sets only that operation to Android’s default; it never resets unrelated app-ops. UID overrides, unsupported output and command failures leave editing unavailable. System-critical packages are protected. Commands are validated and time-bounded; Android persists app-op policy across process restarts. UI explains that denying focus requests can stop apps and does not suppress focus-loss callbacks. Existing playback may need restarting. Physical OEM/audio behavior still needs device validation.

Android 16 source was checked against AOSP AudioService/HardeningEnforcer and AppOpsService shell output. HardeningEnforcer can exempt apps targeting below API 35 unless full hardening is enabled; OEM behavior can differ. Therefore the UI advertises actual focus-request policy controls, not universal dual audio or suppression of another app’s focus-loss callbacks.
