# System, Network, Stremio and camera integration work

This is an implementation checkpoint, not a completed-feature claim.

Authorized scope:
- Replace the external AppOpsNext link with its complete embedded application and per-package buttons: verified operation writes, package/UID scope, templates, batch edits, new-install policies, history/archive, experimental access monitoring/listeners and diagnostics.
- Network logs: app-icon entry into the existing full rule editor, retaining temporary allowance. Match Rethink's home/app-list/detail/statistics layout using Telos semantic colors and all existing Network capabilities. Audit remaining functional gaps separately from visual parity; retain the existing DNS integrations.
- Stremio/Trakt: synchronize library/watchlist, watched movie/episode identity and resume positions after each login. Resolve account isolation and merge conflicts without discarding server progress. Investigate genuine shared, compatible cache/storage with official Stremio, rather than labeling independent HTTP caches shared.
- Camera: QR and barcode recognition, multi-page scan-to-PDF and multilingual OCR, with downloadable language data where needed and bounded off-Main image processing.

Current unverified work: AppOpsNext 1.6.5 source imported under libs/appops-next (upstream commit in UPSTREAM.md), adapting it into a host-owned graph and embedded screen; no external app dependency. Logs link to package rules and pre-expand the package editor. Gradle/manifest wiring, localization, integration tests, visual QA and feature-gap audit remain required. No new release yet.

The user has explicitly authorized merge/release only after the full scope above is complete and verified. Stremio review found missing true player offsets and account-scoped persistence; implement these before considering sync complete.
