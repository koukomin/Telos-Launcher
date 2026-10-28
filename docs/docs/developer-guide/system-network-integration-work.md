# System, Network, Stremio and camera integration

PR #69 embeds AppOpsNext, connects Network log icons to package rules, extends Network routing and statistics, and adds account-isolated playback synchronization and camera scanning. The user excluded RPN subscription/provisioning on 2026-10-28. No RPN functionality is claimed.

## Implementation

- **System:** AppOpsNext 1.6.5 source at `libs/appops-next`, upstream `b3500a49813c1181f49f1f86036bb5cad9c06a22`, with host-owned lifecycle and private storage, per-package navigation and the shared supported Shizuku UserService transport. Android 15+ is required for this component. Templates, batches, new-app policies, history/archive, experimental listeners/monitoring and diagnostics are retained. The ARM64 Go daemon is built from source; other ABIs retain the UserService backend. Locale selection changes the host language. Sheet/header/search surfaces are opaque semantic containers; bottom navigation has natural text height.
- **Network:** shared Home/Statistics/Logs/Apps/Settings navigation, responsive package rule grid, category/UID search and retained-log traffic summaries with nullable metering stamps. Log icons open the same full package editor and retain temporary access. SOCKS5/HTTP CONNECT endpoints are encrypted before atomic persistence; native registration is distinguished from reachability, and unavailable assigned routes fail closed. Two-hop WireGuard uses the pinned firestack `TestHop/Hop` contract, validates full-route entry tunnels and checks the router's actual hop before routing. RPN is excluded; existing DNS integrations remain.
- **Stremio:** actual player offsets/duration/watched time cross the isolated-player bridge into account-scoped AtomicFiles. Session tokens reject stale player callbacks. A bounded durable pending queue coalesces progress. Watched bitfields and thresholds follow official stremio-core `029301839c863f2c7e5b7dcb5ca170d85280eda7`; seeking alone is not watched time. Credentials and account initialization run off Main.
- **Trakt:** exact-ID watchlists, watched movies/episodes and playback snapshots are separate from Stremio state. Session-scoped WorkManager updates preserve original watched times and reject delayed updates superseded by newer remote history/progress. Login refreshes the library. Live account interoperability is not emulator-verified.
- **Shared cache:** an explicit compatible streaming-server mode reads the server's actual cache root and applies size/folder changes with readback. Torrent playback and continuation use that server and bypass duplicate Telos HTTP caching. Origins and responses are bounded; redirects are disabled. Private storage of another Android app is not directly accessible. The server owns its network/cache behavior.
- **Camera:** bounded ZXing luminance analysis recognizes QR and retail/linear codes. Result opening is explicit. A private, recoverable, bounded 30-page session supports capture/import/order/remove/rotate/edge trim/grayscale. Tesseract4Android 4.9.0 renders local searchable PDFs using up to three pinned tessdata_fast models. Model downloads verify byte counts and Git blob hashes; documents are not uploaded. Perspective correction is not implemented. Compact capture/import/save icons preserve access in short windows and at large font sizes.

## Verified evidence

GitHub Actions native run **40641563459**, source `09cbdf3f693dcb1900ee88cfca93f8dcdd914be5`, passed compilation, unit tests and **30 Android tests** with zero failures/errors/skips. The retrieved artifact contains 116 PNGs. Tests include Stremio account isolation/failed writes, multipage image PDF, actual Greek/English OCR PDF text extraction and the Network large-font rule grid. The Network font-2 dark screenshot was inspected for readable labels and opaque surfaces.

Native run **40644472336** at `ae9d150c5585c3c793a18d77db2f8885f0699b36` and native run **40645880656** at `cd7af3ba84e919c5e30f91e0ddd149cde627fc9c` passed, including replay and off-Main initialization hardening. Embedded AppOps run **40645880445** passed.

The final AppOps/scanner layout changes require their new instrumented surface tests and screenshot inspection. Translation generation run **40645880622** covers both host and embedded AppOps resources across all 49 base locales, Greek first. Translation validation must pass after artifacts are merged; generation alone is not a semantic review. Signed ARM64 and ARM32 APK preflight is requested separately before merge.

## Validation limits

No physical OnePlus/Android 16 call, Shizuku/root grant, native proxy/two-server WireGuard, live Stremio/Trakt account, installed Stremio server or camera-hardware interoperability has been verified by these emulator tests. Do not infer those outcomes from compilation or screenshots. The PR remains unreleased until final resource, UI and signed-build checks pass.

### Signed-build producer dependency

The first ARM64 signed preflight (`40652059373`) exposed a Gradle implicit dependency: release lint reads generated daemon assets independently of the asset merge task. Lint model writer tasks now explicitly depend on the same daemon producer. This keeps release lint enabled and preserves ARM32's existing UserService fallback. The corrected signed builds must pass before APK delivery. An initial set of 112 Greek AppOps resources has also been manually reviewed, including shared-UID warnings, allow/ignore/deny/foreground modes and write/restore confirmations; later machine artifacts must preserve these reviewed entries.

### Visual review correction

Run `40652059321` passed 32 Android tests. Retrieved AppOps/scanner screenshots nevertheless exposed broken narrow tab/navigation words and a scanner font-density fixture gap. Responsive navigation, scrolling tabs, inherited Dialog density and a font-scale assertion address those findings; replacement screenshots must be inspected before visual completion is claimed.

### Greek review and CI queue control

All 557 translatable embedded AppOps resources now have reviewed Greek wording, including privacy, history deletion, shared UID effects, batches and monitoring limitations. Technical names and formatting-only literals are retained intentionally. Final generation artifacts must not overwrite these reviewed resources.

PR preflight release builds now have a separate concurrency group from serialized main/tag publication, and superseded checks cancel within each PR workflow. Inventory-only translation runs use one partition rather than reserving sixteen runners; actual explicitly activated generation still uses sixteen. The already-running final generation is not canceled by these changes. These adjustments reduce repeated CI backlog without bypassing tests or release signing.

### Complete generated-asset wiring

Signed preflight `40656397962` exposed another independent consumer, `lintVitalAnalyzeRelease`. The initial lint-model dependency alone was insufficient. The daemon task now declares its output as a `DirectoryProperty` annotated `OutputDirectory`, and AGP registers it through `addGeneratedSourceDirectory` rather than a static source folder. This carries the producer to all asset consumers and removes the task-name workaround. ARM32 still does not register the ARM64 asset. The corrected signed preflight remains required; no APK from the failed run is a deliverable.
