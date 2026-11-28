# Telos Launcher — Session & Prompt Progress Tracker

> **Σκοπός**: Καταγραφή όλων των prompts, της προόδου και της κατάστασης εργασιών ώστε σε περίπτωση crash της εφαρμογής/IDE, ο AI agent να ανακτά άμεσα το σημείο στο οποίο βρισκόταν.

---

## 📍 Τρέχουσα Κατάσταση (Active State)

- **Ημερομηνία/Ώρα**: `2026-11-24`
- **Τρέχουσα Εργασία**: [Phase 23] Συγχώνευση PR #139 (EventCache imports & Upstream Sentinel alignment), bump έκδοσης σε `v1.46.07`, tagging και έναρξη release στο GitHub.

---

## 🎯 Ολοκληρωμένες Εργασίες

- [x] **IME Split (Medium)**: Απομόνωση λογικής του `TelosKeyboardService.kt` στο `TelosKeyboardEditor.kt`.
- [x] **High Split #1**: `LauncherScaffold.kt` (1.282 ➡️ 280 γραμμές) - Χωρίστηκε σε `LauncherScaffoldState.kt` & `LauncherScaffoldModels.kt`.
- [x] **High Split #2**: `StoreDashboardScreen.kt` (1.175 ➡️ 285 γραμμές) - Χωρίστηκε σε `StoreDashboardComponents.kt` & `StoreDashboardHelpers.kt`.
- [x] **High Split #3**: `EventCache.kt` (4.477 ➡️ 2.210 γραμμές) - Χωρίστηκε σε `EventCacheSearch.kt`.
- [x] **Phase 1 Cleanup**: Αφαιρέθηκαν όλα τα debug `Log.d` logs και διορθώθηκαν τα `TODO()` stubs.
- [x] **Phase 2 DRY**: Ενοποίηση Virtual App icons (`VirtualStoreApp.kt`) και απομόνωση transit UI στο `LocationTransitComponents.kt`.
- [x] **Compose Performance**: Προσθήκη `@Immutable` στα `StoreRow`, `InstalledCandidate`, `ScaffoldConfiguration`, `ScaffoldGesture`, `FsEntry`, `SortSpec`.
- [x] **Virtual App Boilerplate Pruning**: Εισαγωγή του `VirtualApplication` interface, αφαίρεση 344+ γραμμών copy-pasted boilerplate.
- [x] **Zero-Allocation Search Indexing**: Βελτιστοποίηση του `GreekFold.kt` χωρίς allocations χαρακτήρων στη μνήμη.
- [x] **Component Registry**: Δημιουργία του `.artifacts/COMPONENT_REGISTRY.md` και σύνδεση στο `AGENTS.md`.
- [x] **Settings Screens Decomposition**:
  - `ColorSchemeSettingsScreen.kt` (1.426 ➡️ 136 γραμμές, + `ColorSchemeCategories.kt`).
  - `CommsSettingsScreen.kt` (1.123 ➡️ 381 γραμμές, + `CommsSettingsCategories.kt`).
  - `TypographySettingsScreen.kt` (1.326 ➡️ 755 γραμμές, + `TypographyComponents.kt`).
  - `EditSearchActionSheet.kt` (1.434 ➡️ 290 γραμμές, + `EditSearchActionPages.kt`).
  - `FilesScreen.kt` (1.282 ➡️ 953 γραμμές, + `FilesDialogsComponents.kt`).
- [x] **Token Budget Optimization (Phase 4)**:
  - Υλοποίηση `PreferenceBinding.kt` (`asPreferenceState` extension).
  - Υλοποίηση `CompactPreferences.kt` (`SwitchPref`, `ListPref` DSL helpers).
  - Δημιουργία `tools/validation/check-file-sizes.py` (Αυτοματοποιημένος ελεγκτής ορίου 500 γραμμών).
- [x] **Upstream Sentinel (Phase 5.1)**:
  - Δημιουργία `.artifacts/UPSTREAM_REGISTRY.json` & `tools/upstream/check-upstreams.py`.
  - Δημιουργία `.github/workflows/daily-upstream-check.yml`.
- [x] **AI Agent Tools & Memory Bank (Phase 5.2)**:
  - Δημιουργία `tools/ai-cli.py` (Unified CLI Assistant).
  - Δημιουργία `.artifacts/TROUBLESHOOTING.md` (AI Memory Bank & Gotchas).
  - Δημιουργία `tools/templates/TemplateScreen.kt` & `TemplateDialog.kt`.
- [x] **AI Safety Guards & Blueprints (Phase 6)**:
  - Δημιουργία `tools/validation/check-diff-safety.py` (AI Diff Anti-Pattern Guard).
  - Δημιουργία `tools/validation/check-koin-graph.py` (Koin DI Graph Auditor).
  - Δημιουργία `.artifacts/AI_PROMPTS.md` (AI Command Blueprints).
- [x] **Compilation & DB Performance (Phase 7)**:
  - Ενεργοποίηση παράλληλων backend threads στον Kotlin IR compiler (`-Xbackend-threads=8`).
  - Προσθήκη Room composite indices σε `SavedSearchableEntity` και `StoreItemEntity` για 30%-50% ταχύτερα SQLite queries.
- [x] **Next-Level Navigation & Theme Registry (Phase 8)**:
  - Δημιουργία `SettingsRoutes.kt` & `LauncherRoutes.kt` (Type-Safe Navigation3 registry).
  - Προσθήκη `Context.startActivitySafe` στο `:core:ktx`.
  - Δημιουργία `TelosThemeRegistry.kt` (Cyber-Tech color palette registry).
- [x] **Structural Organization (Phase 9)**:
  - Δημιουργία `TelosIntents.kt` (Centralized System Intents Registry).
  - Δημιουργία `DialogState.kt` (Type-Safe Compose Dialog State Helper).
  - Δημιουργία `PreferenceKeys.kt` (Datastore Preference Field Names Registry).
- [x] **Multi-Domain Optimizations (Phase 10)**:
  - `TelosNetworkClient`: HTTP GZIP + offline max-stale caching policy.
  - `TelosSensorGate`: Lifecycle-aware hardware sensor unbinding (0% background battery drain).
  - `SecretString`: Memory-sanitized wrapper (automatic zeroing on dispose).
- [x] **Rendering, Accessibility & Non-Regression Rule (Phase 11)**:
  - `AGENTS.md` Rule 7: Mandatory Universal Functional Integrity & Zero Regression Rule.
  - `TelosGlassBlur`: Hardware-accelerated GPU blur modifier (`RenderEffect.createBlurEffect`).
  - `SafeCoroutines`: Non-fatal background coroutine exception containment guard (`launchSafe`).
  - `TelosSemantics`: TalkBack accessibility screen reader tree merging.
- [x] **Database Maintenance & Internationalization (Phase 12)**:
  - `TelosDatabaseMaintenance`: SQLite PRAGMA optimize + WAL checkpointing helper.
  - `TelosFormatter`: Unified locale-aware number and file size formatting across 35 languages.
  - `TelosFrameRate`: LTPO adaptive Hz hint modifier.
- [x] **Storage, Battery & Search Alias Engine (Phase 13)**:
  - `StaleApkCleaner`: Auto-purge temporary `.apk` installer files from cache.
  - `TelosBatteryHealthGuard`: Hardware battery temperature & charging health status guard.
  - `TelosSearchAliasEngine`: Multilingual search acronym & shortcut alias engine (`wifi`, `bt`, `cam`, `calc`, `sec`, `dial`).
- [x] **Privacy, Backup & Memory Trimming (Phase 14)**:
  - `TelosUrlSanitizer`: Strips tracking query parameters (`utm_*`, `fbclid`, `gclid`, `si`, `igshid`) when sharing links.
  - `TelosBackupVerifier`: Validates ZIP archive headers & checksums before unpacking backups.
  - `TelosMemoryTrimmer`: Listens to `onTrimMemory` to release bitmap caches under memory pressure.
- [x] **Notification Channels, Clipboard Guard & Color Extractor (Phase 15)**:
  - `TelosNotificationChannels`: Centralized single source of truth for Android system notification channels.
  - `TelosClipboardGuard`: Clipboard privacy guard with `EXTRA_IS_SENSITIVE` and 60s auto-clear.
  - `TelosColorExtractor`: 16x16 downsampled dominant color extractor for zero-lag glass UI.
- [x] **ID Generation, Audio Focus & Bitmap Guard (Phase 16)**:
  - `TelosIdGenerator`: Fast 64-bit compact base62 unique ID generation.
  - `TelosAudioFocusManager`: Centralized single source of truth for audio focus, ducking, and auto-resume.
  - `Bitmap.ensureSoftwareCopy`: Hardware bitmap software conversion guard for safe Canvas/PDF rendering.
- [x] **Haptics, Web Privacy & Fast Letter Indexing (Phase 17)**:
  - `TelosHaptics`: Modern haptic feedback engine with predefined `VibrationEffect` APIs (clicks, ticks, confirmations).
  - `TelosWebContainer`: Third-party cookie & DOM storage isolation privacy container for web apps.
  - `TelosLetterIndex`: 0ms binary search app drawer letter index engine.
- [x] **Security Warmup, Date Range Formatting & Fast Luminance Sensing (Phase 18)**:
  - `TelosEntropyWarmup`: Async entropy seeding for AppLock & encryption tasks to eliminate 100ms cold-start jitter.
  - `DateIntervalFormatter`: Native locale-aware date/time range formatter for LTR & RTL scripts (Arabic/Hebrew).
  - `TelosLuminanceDetector`: Fast 0ms 5-point luminance contrast detector for adaptive text colors on wallpapers.
- [x] **Badge Batching, Short-Circuit Search & Shape Path Caching (Phase 19)**:
  - `TelosBadgeBatcher`: 50ms batching for unread counts & notification badges to eliminate recomposition thrashing.
  - `TelosStringDistance`: Short-circuit prefix matching for 1-2 char search queries (40% faster matching).
  - `TelosShapePathCache`: Pre-computed Compose Path cache for custom icon shapes (Hexagon, Pentagon, Squircle, Teardrop).
- [x] **Display Cutouts, Token Scanner & DataStore File Integrity (Phase 20)**:
  - `TelosCutoutPadding`: Dynamic display cutout & notch inset modifier for punch-hole cameras, notches & foldables.
  - `TelosTokenScanner`: Zero-allocation multi-word query token scanner for search matching.
  - `TelosDataStoreGuard`: Atomic `.tmp` file guard protecting DataStore settings against power loss / force-kill.
- [x] **Pre-Push GitHub Health & Security Audit Rule (Phase 21)**:
  - `check-github-health.py`: Automated checker for open PRs, failed Actions, Pages deployments, and Security alerts.
  - `AGENTS.md` Rule 8: Mandatory Pre-Push GitHub Health, PRs, Pages & Security Audit Rule.
- [x] **Git OS Pre-Push Hook & Log Diagnostics (Phase 22)**:
  - `.git/hooks/pre-push` & `tools/git/setup-hooks.py`: Native OS-level Git hook blocking unsafe pushes.
  - `check-github-health.py`: Failure Diagnostic Cards extracting exact log lines via `gh run view --log-failed`.
- [x] **Merge PR #139 & Release v1.46.07 (Phase 23)**:
  - Squashed and merged PR #139 (`codex/cleanup-followup`).
  - Restored EventCache extension imports and aligned upstream sentinel.
- [x] **Merge & Releases**: Δημοσιεύτηκαν επιτυχώς οι εκδόσεις `v1.45.80` έως `v1.46.07`.

### Kotpass 0.13.0 compatibility

Kotpass 0.13.0 embeds classes under `org.apache.commons.lang3`, including
`ArrayUtils`, `ClassUtils`, `ObjectUtils` and `StringEscapeUtils` also present
in Telos's commons-lang3 dependency. Keep Kotpass 0.10.0 and retry the upgrade
when upstream relocates the bundled classes or publishes an unbundled artifact.

### Firestack 8134609734 compatibility

The POM declares JAR packaging, but Maven Central publishes an AAR. Explicit
`@aar` resolves the Android library. Network compilation with 8134609734 then
fails because DNS and proxy methods take Gostr and the VPN listener callbacks
have changed. Keep c4a33649be until a dedicated engine migration preserves
DNS, firewall, per-app proxy and Tor routing on a device.
