# Runtime optimization measurements

Measurements use OpenJDK with a 128 MiB Java heap, two warmup runs and the median of five measured runs. They measure synchronous helper execution and JVM thread-allocated bytes, not Android resident memory, startup time or device battery use. Filesystem and CPU conditions affect timing. The before/after transfer runs were executed sequentially.

| Fixture | Before time | After time | Before allocated bytes | After allocated bytes |
| --- | ---: | ---: | ---: | ---: |
| 1,200 identical 1 KiB files, duplicate analysis | 73.24 ms | 31.29 ms | 417,177,080 | 1,189,256 |
| 4,000 files in a depth-16 index | 30.06 ms | 20.40 ms | 19,786,272 | 4,514,368 |
| Move 400 mixed small files | 149.65 ms | 114.53 ms | 264,385,664 | 31,756,168 |
| Search 20,000 mixed Greek/Greeklish/phone/document texts | 142.98 ms | 100.31 ms | 362,592,904 | 252,518,432 |

The analyzer reuses hash/comparison scratch buffers and its digest within each scan. The index tracks hidden ancestry with a bounded depth stack. Transfers reuse scratch buffers and the SHA-256 instance within a sequential operation; publication reuses its ByteBuffer wrapper. Checksums, exclusive destination creation, resume verification and source-retention checks remain in place. No global content cache or buffer shared across concurrent transfers was added.

Standalone fixtures live in `tools/performance/jvm/`. Compile each fixture separately together with its helper source files using the Kotlin JVM compiler, then run its generated `*BenchmarkKt` main class with `-Xmx128m`. Compare the same fixture against the base-revision and candidate helper sources. `ThreadMXBean` allocation reporting is a JVM profiling facility; these fixtures are not Android application code.

Regression coverage includes mixed empty/short/multi-buffer files, pause/resume, changed sources, destination collisions, large duplicate groups, hidden-directory ancestry and cancellation.

Search compiles each query once and reuses its immutable matcher across items, without a global cache. An independent comparison of 800 individual scores across 20 queries and 40 field pairs produced identical results before and after; 45 search tests pass.

For search, compile `SearchBenchmarkBefore.kt` with the base sources and `SearchBenchmarkAfter.kt` with the candidate sources; include both `GreekText.kt` and `TelosSearch.kt`. The fixtures differ only in the batch matcher invocation and assert a stable score checksum.

## Network and playback recovery

TelosVpnService promotes itself immediately in onCreate before resolving controller state. START_STICKY, system VPN intents, package replacement and foreground entry restore persisted active intent; explicit Stop/revoke cancels it. Engine component teardown/startup is serialized; failures get three delayed retries, reset after a stable connection. Tor bootstrap is polled off the main thread and native startup errors are surfaced. Music/video services use network wake mode and WAKE_LOCK. Native torrent download flags must never be null: libtorrent4j ORs them into add_torrent_params unconditionally.
