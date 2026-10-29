package de.mm20.launcher2.appmanagement.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtOptimizerTest {

    @Test
    fun parseDumpsysDexopt_capturesPrimaryBaseApkAndOdexPath() {
        val sampleDumpsys = """
Dexopt state:
  [com.example.speedapp]
    path: /data/app/~~abc/com.example.speedapp/base.apk
      arm64: [status=speed] [reason=cmdline] [primary-abi]
        [location is /data/app/~~abc/com.example.speedapp/oat/arm64/base.odex]
  [com.example.verifiedapp]
    path: /data/app/~~def/com.example.verifiedapp/base.apk
      arm64: [status=verify] [reason=install] [primary-abi]
  [com.example.multiabi]
    path: /data/app/~~ghi/com.example.multiabi/split_config.arm64_v8a.apk
      arm64: [status=speed] [reason=cmdline]
    path: /data/app/~~ghi/com.example.multiabi/base.apk
      arm64: [status=speed-profile] [reason=install-bulk] [primary-abi]
        [location is /data/app/~~ghi/com.example.multiabi/oat/arm64/base.odex]
Compiler stats:
  [com.example.other]
        """.trimIndent()

        val parsed = StartupOptimizationAudit.parseDumpsysDexopt(sampleDumpsys.lineSequence())

        assertEquals(3, parsed.size)

        val speed = parsed["com.example.speedapp"]
        assertNotNull(speed)
        assertEquals("speed", speed?.currentFilter)
        assertEquals("cmdline", speed?.reason)
        assertEquals("/data/app/~~abc/com.example.speedapp/oat/arm64/base.odex", speed?.odexPath)

        val verified = parsed["com.example.verifiedapp"]
        assertNotNull(verified)
        assertEquals("verify", verified?.currentFilter)
        assertEquals("install", verified?.reason)
        assertNull(verified?.odexPath)

        // Verifies split vs base precedence: primary-abi base.apk wins
        val multi = parsed["com.example.multiabi"]
        assertNotNull(multi)
        assertEquals("speed-profile", multi?.currentFilter)
        assertEquals("install-bulk", multi?.reason)
        assertEquals("/data/app/~~ghi/com.example.multiabi/oat/arm64/base.odex", multi?.odexPath)
    }

    @Test
    fun categorizePackage_correctlyIdentifiesBuckets() {
        // Up to date: current filter matches or exceeds target and odex is fresh
        val upToDateBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "speed",
            reason = "cmdline",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 120_000L,
            odexModifiedMs = 125_000L,
            isFrozen = false,
            skipFrozenApps = false,
        )
        assertEquals(OptimizationBucket.UP_TO_DATE, upToDateBucket)

        // Degraded: odex timestamp is older than last update time
        val staleOdexBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "speed",
            reason = "cmdline",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 150_000L,
            odexModifiedMs = 120_000L,
            isFrozen = false,
            skipFrozenApps = false,
        )
        assertEquals(OptimizationBucket.DEGRADED, staleOdexBucket)

        // Degraded: post-update reason on an updated app
        val postUpdateBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "verify",
            reason = "install-fast",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 140_000L,
            odexModifiedMs = 0L,
            isFrozen = false,
            skipFrozenApps = false,
        )
        assertEquals(OptimizationBucket.DEGRADED, postUpdateBucket)

        // Needs optimization: sitting at lower filter
        val needsOptBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "quicken",
            reason = "bg-dexopt",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 100_000L,
            odexModifiedMs = 101_000L,
            isFrozen = false,
            skipFrozenApps = false,
        )
        assertEquals(OptimizationBucket.NEEDS_OPTIMIZATION, needsOptBucket)

        // Frozen app included by default when skipFrozenApps is false
        val frozenIncludedBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "verify",
            reason = "install",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 100_000L,
            odexModifiedMs = 0L,
            isFrozen = true,
            skipFrozenApps = false,
        )
        assertEquals(OptimizationBucket.NEEDS_OPTIMIZATION, frozenIncludedBucket)

        // Frozen app skipped when skipFrozenApps is true
        val frozenSkippedBucket = StartupOptimizationAudit.categorizePackage(
            currentFilter = "speed",
            reason = "cmdline",
            targetProfile = "speed",
            firstInstallTime = 100_000L,
            lastUpdateTime = 100_000L,
            odexModifiedMs = 110_000L,
            isFrozen = true,
            skipFrozenApps = true,
        )
        assertEquals(OptimizationBucket.FROZEN_SKIPPED, frozenSkippedBucket)
    }

    @Test
    fun parseAmStartOutput_parsesColdStartMetrics() {
        val amOutput = """
Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=org.mozilla.firefox/.App }
Status: ok
LaunchState: COLD
Activity: org.mozilla.firefox/.App
TotalTime: 642
WaitTime: 658
Complete
        """.trimIndent()

        val parsed = TelosArtOptimizer.parseAmStartOutput(
            packageName = "org.mozilla.firefox",
            componentName = "org.mozilla.firefox/.App",
            output = amOutput,
        )

        assertNotNull(parsed)
        assertEquals("org.mozilla.firefox", parsed?.packageName)
        assertEquals("org.mozilla.firefox/.App", parsed?.componentName)
        assertEquals(642L, parsed?.totalTimeMs)
        assertEquals(658L, parsed?.waitTimeMs)
        assertEquals("COLD", parsed?.launchState)
    }

    @Test
    fun parseAmStartOutput_returnsNullOnInvalidOutput() {
        val badOutput = "Error: Activity not found"
        val parsed = TelosArtOptimizer.parseAmStartOutput("com.bad", "com.bad/.Main", badOutput)
        assertNull(parsed)
    }

    @Test
    fun profileDiagnosticResult_checksUsability() {
        val emptyDiag = ProfileDiagnosticResult(
            packageName = "com.test.empty",
            hasEmbeddedBaselineProf = false,
            hasDexMetadataDm = false,
            refProfileBytes = 32L, // <= 64 bytes is considered empty/stub header
            curProfileBytes = 0L,
            currentFilter = "verify",
            compilationReason = "install",
        )
        assertFalse(emptyDiag.hasUsableProfile)
        assertTrue(emptyDiag.recommendsFullSpeedFallback)

        val validDiag = ProfileDiagnosticResult(
            packageName = "com.test.populated",
            hasEmbeddedBaselineProf = true,
            hasDexMetadataDm = false,
            refProfileBytes = 128L,
            curProfileBytes = 512L,
            currentFilter = "speed-profile",
            compilationReason = "bg-dexopt",
        )
        assertTrue(validDiag.hasUsableProfile)
        assertFalse(validDiag.recommendsFullSpeedFallback)
    }
}
