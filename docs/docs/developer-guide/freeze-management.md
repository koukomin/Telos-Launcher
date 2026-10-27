# Unified freeze management

Two legacy routes (`FreezeSettingsRoute`, `SmartFreezeDashboardRoute`) now resolve to one manager. Tabs preserve their saveable selection/filter state; app search includes package names, actions are explicit, and the history dashboard remains a subordinate diagnostic screen. The Advanced duplicate entry is removed.

## Implementation and constraints

`ScreenOffFreezePolicy` resolves per-app minutes before the active profile’s screen-off/idle trigger. The master auto-freeze switch and candidate list remain required. The screen-off controller persists elapsed-realtime deadlines with boot identity and uses a non-exported explicit alarm receiver. Screen-on cancels the plan; alarm execution rechecks screen state, master enablement, candidates and exclusions. No exact-alarm permission is requested. Doze may defer execution; reboot discards old plans. Runtime plans/holds and device-specific Expert consents are excluded from portable backups.

Companions use an explicit enabled-master set (default empty) and the existing configured dependency map. There is no recursive inference or automatic activation from install source. A shared companion hold records all masters. Only companions actually thawed or already held are retained, and failed master launch releases its holds. Current-user-only rules avoid thawing a different Android profile by accident.

`BloatDescriptions` lazily loads a pinned gzip snapshot on IO, or a validated atomic cache. Each hub entry checks for updates if the persisted preference is enabled (default true). HTTPS conditional ETag requests, 8 MiB download bounds, schema validation and atomic writes preserve the previous valid cache. Updated entries flow to the open details UI. Failed requests are reported without erasing the cache. Turning updates off prevents later automatic checks; a request already in progress may finish. Native app installation/freezing never depends on a network request.

## Thor / Canta audit, 2026-10-21

Thor head reviewed: `6dbf7ee4f3777b8cb26790facb8846f3706b3699`. Canta head: `9b30d1bc1c8627f9866cdf7939f9fa28400b3605`.

| Freezer behavior | Telos disposition |
| --- | --- |
| Central Unsafe gate; Expert acknowledgement; no-data failure closed for system apps | Added at the service mutation boundary, including bulk/background and Island notification requests |
| App descriptions / removal category | Same upstream UAD-NG data, pinned offline fallback and on-entry conditional updater |
| Bulk selection and clear outcomes | Search/package filters, select-all-shown, explicit Freeze/Unfreeze/Auto-freeze, success count |
| Verify actual Android state, not shell exit alone | Mutation results now read back package enabled/suspended state before counting success or recording statistics |
| Native Unpause action on Android 11+ | Requested on the Shizuku binder path; optional reflection failure retains the standard OEM dialog. Root shell retains its existing dialog, without adding a root daemon |
| Keep app data; never silently escalate disable into uninstall | Existing Root/Shizuku suspend/disable and restoration journal preserved; no uninstall fallback added |
| Per-user operations / Dhizuku owner scope | Existing provider restrictions preserved; new timing/dependency rules are current-user only |
| Scheduled actions | Added screen-off delays requested here; not a claim of full calendar/extension/export parity with Thor |

Thor’s wider installer, export, extension, permissions and App Ops products are outside this freeze UI change. No proprietary assets or Thor trademarks are copied.

## Validation

CI `Validate freeze management` compiles the actual UI and runs freeze/preferences regressions. Policy tests cover global-immediate override, explicit immediate override, disabled inherited triggers, candidate scope, imported bounds and UAD case/failure behavior. Preference tests cover upgrade defaults and round-trip persistence. Device acceptance still requires screen-off/on, process recreation, Doze, Shizuku/root denial, group launch failures, large fonts and narrow/landscape UI. Do not mark those checks passed from JVM results alone.


## Recents removal opt-in

`RecentsFreezeMonitor` is dormant by default. When enabled on API 29+, it registers a real ITaskStackListener through the existing Shizuku binder bridge, or a narrowly scoped app_process helper when the selected freeze backend is Root. The root helper owns a system task listener, streams exact callbacks, answers complete snapshot requests and exits on pipe closure; it is started only while the Recents opt-in is enabled. No Accessibility polling or task-list-difference inference is used. Transaction IDs come from the device’s stub. Only system_server callbacks are accepted. Task creation/stack-change events refresh a bounded identity cache; only onTaskRemoved can request freezing. A fresh complete task snapshot must prove there is no remaining task for that current-user package. Failed/unknown/full-page reads fail closed; permission loss disconnects and retries without prompting.

Removal is not evidence of a user swipe specifically: Android also emits removal for application finishAndRemoveTask. The observer does not infer removal from list differences. Eligible packages are explicit candidates or verified user thaws tracked while the preference is enabled; runtime thaw markers stay out of portable backups. Never-freeze, usage, maintenance, Play-update and companion exclusions remain authoritative. Shizuku or the selected, authorized Root backend is required for observation. Physical-device callback/reconnection behavior is pending validation.


## Shared privileged process transport

All first-party shell-process clients use `core:ktx` ShizukuProcesses. The application stores only its application context during startup; the supported Shizuku UserService is bound lazily from an IO caller, with a bounded connection wait and server UID verification. No reflected/private Shizuku process factory remains. Commands receive Binder-owned pipe descriptors, preserve stdin/stdout/stderr and exit status, and close resources on completion or timeout. PrivilegedShell drains bounded output and falls back to Root when the Shizuku command is unavailable or denied. Package installers keep their streaming session protocol and enabled-state preservation.

System audio and Root component editing determine their actual required privilege independently of the selected freeze provider. UI availability refreshes on resume, Shizuku binder delivery/death and permission results; it never treats a preference as proof of a successful write.
