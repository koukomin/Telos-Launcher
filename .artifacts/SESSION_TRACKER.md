# Session tracker
2026-11-19: Investigate ColorOS black/empty Recents, repair failed Android release builds, update docs and publish. Keep Gradle 10 migration separate and fetch main before release.

Android release and AAB logs identify a missing Amethyst mipmap. Shortcut fallback now uses the installed host icon. ColorOS compatibility keeps system transition scheduling on OnePlus, OPPO and realme.
The 1.45.74 runs exposed 11 further Kotlin errors in Media. Corrected resource and extension references before 1.45.75.

2026-11-19: Add configurable headset Play routing among live Media sessions, with Video and Stremio excluded by default. User requested merge/release without Actions monitoring.

2026-11-19: ARM64 v1.45.76 failed in Gradle remote artifact resolution. Requested failed-jobs retry on the original tag and removed the JetBrains Space fallback for future builds.

2026-11-20: Reviewed PR #132. Fixed the streaming test fixture to model timed Process.waitFor and assert EOF before cleanup. Five JVM tests passed with a 64 MiB heap. The v1.45.76 release published both signed APK assets after retry.

2026-11-20: PR #132 retry compiled, then hit the stock Pixel Launcher ANR before Aether login. Added a one-time, emulator-only recovery for that exact system dialog.

2026-11-20: PR #132 Aether UI passed all four tests. A parallel APK job still failed to locate KSP 2.3.14. Route the KSP plugin lookup away from Google Maven to its publishing repository.
