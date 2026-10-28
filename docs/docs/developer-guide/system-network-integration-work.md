# System, Network, Stremio and camera integration work

This is an implementation checkpoint, not a completed-feature claim.

Authorized scope:
- Replace the external AppOpsNext link with its complete embedded application and per-package buttons: verified operation writes, package/UID scope, templates, batch edits, new-install policies, history/archive, experimental access monitoring/listeners and diagnostics.
- Network logs: app-icon entry into the existing full rule editor, retaining temporary allowance. Match Rethink's home/app-list/detail/statistics layout using Telos semantic colors and all existing Network capabilities. Audit remaining functional gaps separately from visual parity; retain the existing DNS integrations.
- Stremio/Trakt: synchronize library/watchlist, watched movie/episode identity and resume positions after each login. Resolve account isolation and merge conflicts without discarding server progress. Investigate genuine shared, compatible cache/storage with official Stremio, rather than labeling independent HTTP caches shared.
- Camera: QR and barcode recognition, multi-page scan-to-PDF and multilingual OCR, with downloadable language data where needed and bounded off-Main image processing.

Current unverified work: AppOpsNext 1.6.5 source imported under libs/appops-next (upstream commit in UPSTREAM.md), adapting it into a host-owned graph and embedded screen; no external app dependency. Logs link to package rules and pre-expand the package editor. Gradle/manifest wiring, localization, integration tests, visual QA and feature-gap audit remain required. No new release yet.

The user has explicitly authorized merge/release only after the full scope above is complete and verified. Stremio review found missing true player offsets and account-scoped persistence; implement these before considering sync complete.

Implementation follow-up: draft PR #69, branch `codex/system-appops-network`, initial remote source head `ffb6e41a833bf491ce2824445ecf3aa7b42f96a2`. AGP asset source-set incompatibility was corrected via the variant API in remote head `a579dd5e45d26b3fa575643ebca2435bd71291ad`; fresh CI pending. Local uncommitted Stremio work adds account-scoped atomic library storage and a pure millisecond/watch-bitfield state codec with tests; player bridge and full Trakt synchronization still need wiring. Official Stremio core inspected at `029301839c863f2c7e5b7dcb5ca170d85280eda7`: watched uses actual watched time above 70% of duration; credit/resume reconciliation threshold is 90%. Do not mark a launch attempt watched, or synthesize a one-millisecond resume in place of actual playback.

The embedded AppOps workflow passed at remote head `d2e8770ce2d239f40886d2678b3db9efd6a4314e` (run 40587141997): native daemon tests, AppOps unit tests and UI compilation. This does not certify physical Shizuku grants or the pending localization/visual matrix. Stremio player hooks, main-process isolation bridge, session guards, per-account atomic storage and a per-video coalesced pending queue are now wired locally; nine pure progress/bitfield regression tests added. Complete Trakt, shared cache and camera work are still outstanding.

### Trakt synchronization checkpoint

The draft now imports Trakt movie/show watchlists, watched episode IDs and playback percentages, and submits exact catalog IDs from the player through a session-scoped WorkManager queue. Trakt snapshots remain separate from Stremio account files. Playback updates record actual positions and watched time; seeking does not count as time watched. Unit coverage includes identifier validation, runtime conversion, watchlist parsing and account snapshot rejection. Android account-isolation and failed-write tests have been added but have not yet passed CI. Replay ordering, real-account/device validation, the shared cache transport, camera scanning, Network UI completion and AppOps localization remain release blockers.

CI exposed duplicate uses-sdk declarations in the host manifest and a configuration-cache capture in the Go asset build task; both are corrected in the draft.

### Camera scanner checkpoint

The draft now implements CameraX luminance barcode analysis through the existing ZXing dependency and a private multipage document workflow. Tesseract4Android 4.9.0 supplies on-device multilingual recognition and searchable PDF output; up to three tessdata_fast models are selected explicitly and fetched with byte limits, atomic rename and pinned blob verification. Scanner photos are normalized to bounded JPEGs, and only a single full page is processed during PDF export. Rotation/crop/grayscale and page ordering are supported. Perspective correction is not implemented. Initial English/Greek strings exist; remaining locale coverage and CI/device verification are release blockers.

### Shared-cache transport checkpoint

The draft implements the official Stremio streaming-server settings and torrent URL protocols, following stremio-core commit `029301839c863f2c7e5b7dcb5ca170d85280eda7`. Cache mode is explicit; the default local server can be probed through the settings sheet. Reads/writes are bounded off-Main requests with redirects disabled. Applying settings preserves other server torrent controls, then reads back the size/folder. Playback and episode continuation use the same server, while the player bypasses its separate HTTP cache for those launches. Unit tests exercise origin validation, torrent paths/index/trackers/file filters, base32 hashes and a real local HTTP settings exchange. Interoperation with an installed Stremio server is still unverified.

### Network navigation, app editor and traffic checkpoint

The draft now shares Home/Statistics/Logs/Apps/Settings navigation across primary screens. Package firewall pages skip the global search/filter chrome, display a large app identity and adaptive rule tiles, and retain granular rules, notes, IP/domain entries and temporary allowances. Tile mode changes clear conflicting isolate/bypass/exclusion flags. Declared Android app categories and UID search supplement existing bulk actions. Statistics aggregates retained flows off-Main for 1h/24h/7d, with nullable metering stamps for backward-compatible old logs; active/retention-limited log totals are explicitly distinguished from system traffic counters. UI/aggregation tests are added; the scanner PDF-document lifecycle compile error is also corrected.

The official Rethink README was reviewed against the current APIs. Existing Telos DNS transports, per-app/universal/IP/domain rules, blocklists, live logs, multiple WireGuard configurations and Tor are retained. General user-configured SOCKS5/HTTP CONNECT routing and multi-hop controls are still gaps to resolve. RPN is an external provisioned proxy service, not functionality supplied by drawing a tab; no subscription or provider endpoint has been fabricated. The complete integration is still a draft pending these decisions, localization and final QA.

### General proxy checkpoint

An encrypted endpoint registry now integrates the existing firestack AddProxy API for SOCKS5/HTTP CONNECT, with global and per-app routing, endpoint editing/removal, explicit direct bypass, and protocol-aware fail-closed decisions. Credentials stay out of public state flows and are encrypted before atomic persistence. Removed endpoints retain assignments to prevent accidental direct fallback. Native registration is tracked separately from reachability. Unit tests validate origin/credential encoding and unavailable/unsupported-protocol routing. This code has not yet passed compilation/device validation. WireGuard multi-hop and RPN provisioning are still unresolved; no parity-complete claim is made.

### Two-hop checkpoint

WireGuard configurations now support an optional entry-tunnel ID. Policy validation rejects missing/self/triple-hop links and requires full IPv4/IPv6 entry routes. Runtime routing remains blocked until the native router reports the expected hop; updates, removals and re-registration invalidate cached bindings. The editor and error handling expose this capability. Pure tests cover accepted pairs and invalid graphs. The implementation follows the already pinned firestack `Proxies.TestHop/Hop` contract and still needs build and real-tunnel verification. RPN subscription provisioning is an external service capability and has not been implemented.

### Synchronization and embedded-language hardening

The draft now checks delayed Trakt updates against exact remote watched IDs and newer playback timestamps. Delayed completions retain their original watched_at through history upload; duplicates and newer resumes supersede queued older events. Additional parser/replay tests cover ID namespaces and episode identity. Stremio credential hydration and account-file activation now happen on IO, and playback waits for initialization instead of decrypting credentials from UI getters.

AppOps language selection now covers all shipped Telos locales, preserves Greek and Traditional Chinese, and explicitly says it changes every Telos screen. The picker is lazy/scrollable. Imported AppOps sheets, search fields and headers use opaque semantic container roles; no additional wallpaper blur is introduced. Remaining translations and final screen/device verification remain open.
