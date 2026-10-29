package de.mm20.launcher2.appmanagement.art

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.packages.TelosPackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class OptimizationBucket {
    UP_TO_DATE,
    NEEDS_OPTIMIZATION,
    DEGRADED,
    FROZEN_SKIPPED,
}

@Immutable
data class PackageDexoptState(
    val packageName: String,
    val currentFilter: String,
    val reason: String,
    val odexPath: String? = null,
    val odexModifiedMs: Long = 0L,
)

@Immutable
data class AuditedAppEntry(
    val packageName: String,
    val label: String,
    val versionCode: Long = 0L,
    val apkBytes: Long = 0L,
    val isSystemApp: Boolean,
    val isFrozen: Boolean,
    val currentFilter: String,
    val compilationReason: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val odexModifiedMs: Long,
    val bucket: OptimizationBucket,
)

@Immutable
data class StartupAuditReport(
    val targetProfile: String,
    val upToDate: List<AuditedAppEntry>,
    val needsOptimization: List<AuditedAppEntry>,
    val degraded: List<AuditedAppEntry>,
    val frozenSkipped: List<AuditedAppEntry>,
    val allEntries: List<AuditedAppEntry>,
    val estimatedMinutesSaved: Int,
    val scanDurationMs: Long,
    val privilegedAvailable: Boolean,
) {
    val actionableEntries: List<AuditedAppEntry>
        get() = degraded + needsOptimization

    val totalActionableCount: Int
        get() = degraded.size + needsOptimization.size
}

object StartupOptimizationAudit {

    private val PACKAGE_HEADER_REGEX = Regex("""^\s*\[([a-zA-Z0-9._]+)]\s*$""")
    private val STATUS_REGEX = Regex("""\[status=([^\]]+)]""")
    private val REASON_REGEX = Regex("""\[reason=([^\]]+)]""")
    private val LOCATION_REGEX = Regex("""\[location is ([^\]]+)]""")
    private val SAFE_PATH_REGEX = Regex("""^/[a-zA-Z0-9._/+~=-]+$""")

    private val POST_UPDATE_OR_RESET_REASONS = setOf(
        "install",
        "install-fast",
        "install-bulk",
        "install-bulk-secondary",
        "install-bulk-downgraded",
        "inactive",
        "boot-after-ota",
        "boot-after-mainline-update",
        "error",
    )

    @Volatile
    private var lastCachedReport: StartupAuditReport? = null

    fun clearCachedAudit() {
        lastCachedReport = null
    }

    fun getCachedAudit(): StartupAuditReport? = lastCachedReport

    fun filterRank(filter: String): Int = when (filter.lowercase()) {
        "everything", "everything-profile" -> 4
        "speed" -> 3
        "speed-profile" -> 2
        "quicken", "space", "space-profile" -> 1
        else -> 0 // verify, extract, assume-verified, run-from-apk, run-from-apk-fallback, unknown, error
    }

    fun estimateMinutesSaved(upToDateCount: Int, targetProfile: String): Int {
        if (upToDateCount <= 0) return 0
        val avgSecondsPerApp = when (targetProfile.lowercase()) {
            TelosArtOptimizer.PROFILE_EVERYTHING -> 11.0
            TelosArtOptimizer.PROFILE_SPEED -> 7.5
            else -> 3.5
        }
        val totalMinutes = (upToDateCount * avgSecondsPerApp) / 60.0
        return totalMinutes.roundToInt().coerceAtLeast(1)
    }

    /**
     * Pure streaming parser for `dumpsys package dexopt` (or the `Dexopt state:` section of `dumpsys package`).
     * Captures primary/base APK compilation status, reason, and `.odex` path without retaining full dump strings.
     */
    fun parseDumpsysDexopt(lines: Sequence<String>): Map<String, PackageDexoptState> {
        val result = HashMap<String, PackageDexoptState>(256)
        var inDexoptSection = false
        var sawDexoptHeader = false

        var currentPkg: String? = null
        var inBaseApk = false
        var bestStatus: String? = null
        var bestReason: String? = null
        var bestOdexPath: String? = null
        var lockedPrimaryAbi = false
        var captureLocationForBest = false

        fun flushCurrent() {
            val pkg = currentPkg ?: return
            if (bestStatus != null) {
                result[pkg] = PackageDexoptState(
                    packageName = pkg,
                    currentFilter = bestStatus!!,
                    reason = bestReason ?: "unknown",
                    odexPath = bestOdexPath,
                )
            }
            currentPkg = null
            inBaseApk = false
            bestStatus = null
            bestReason = null
            bestOdexPath = null
            lockedPrimaryAbi = false
            captureLocationForBest = false
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("Dexopt state:")) {
                inDexoptSection = true
                sawDexoptHeader = true
                continue
            }

            if (sawDexoptHeader && inDexoptSection) {
                if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t") && trimmed.endsWith(":")) {
                    flushCurrent()
                    break
                }
            }

            val pkgMatch = PACKAGE_HEADER_REGEX.matchEntire(rawLine)
            if (pkgMatch != null) {
                flushCurrent()
                currentPkg = pkgMatch.groupValues[1]
                inBaseApk = true
                continue
            }

            if (currentPkg == null) continue

            if (trimmed.startsWith("path:")) {
                inBaseApk = trimmed.endsWith("/base.apk") || !trimmed.contains("split_")
                captureLocationForBest = false
                continue
            }

            val statusMatch = STATUS_REGEX.find(trimmed)
            if (statusMatch != null) {
                val statusVal = statusMatch.groupValues[1].trim()
                val reasonVal = REASON_REGEX.find(trimmed)?.groupValues?.get(1)?.trim() ?: "unknown"
                val isPrimaryAbi = trimmed.contains("[primary-abi]")

                val shouldTake = when {
                    bestStatus == null -> true
                    inBaseApk && isPrimaryAbi && !lockedPrimaryAbi -> true
                    inBaseApk && !lockedPrimaryAbi && bestStatus == "unknown" -> true
                    else -> false
                }
                if (shouldTake) {
                    bestStatus = statusVal
                    bestReason = reasonVal
                    if (inBaseApk && isPrimaryAbi) {
                        lockedPrimaryAbi = true
                    }
                    captureLocationForBest = true
                } else {
                    captureLocationForBest = false
                }
                continue
            }

            if (captureLocationForBest) {
                val locMatch = LOCATION_REGEX.find(trimmed)
                if (locMatch != null) {
                    val loc = locMatch.groupValues[1].trim()
                    if (loc.endsWith(".odex") || loc.endsWith(".oat")) {
                        bestOdexPath = loc
                    }
                    captureLocationForBest = false
                }
            }
        }

        flushCurrent()
        return result
    }

    fun categorizePackage(
        currentFilter: String,
        reason: String,
        targetProfile: String,
        firstInstallTime: Long,
        lastUpdateTime: Long,
        odexModifiedMs: Long,
        isFrozen: Boolean,
        skipFrozenApps: Boolean,
    ): OptimizationBucket {
        if (skipFrozenApps && isFrozen) {
            return OptimizationBucket.FROZEN_SKIPPED
        }

        val currentRank = filterRank(currentFilter)
        val targetRank = filterRank(targetProfile)
        val staleOdex = odexModifiedMs > 0L && lastUpdateTime > 0L && (odexModifiedMs + 5_000L) < lastUpdateTime

        if (currentRank >= targetRank && !staleOdex) {
            return OptimizationBucket.UP_TO_DATE
        }

        val wasUpdated = lastUpdateTime > (firstInstallTime + 5_000L)
        val resetByCmdline = reason == "cmdline" && currentRank == 0
        if (staleOdex || resetByCmdline || reason in POST_UPDATE_OR_RESET_REASONS && (wasUpdated || reason == "inactive" || reason.startsWith("boot-after"))) {
            return OptimizationBucket.DEGRADED
        }

        return OptimizationBucket.NEEDS_OPTIMIZATION
    }

    fun isPackageFrozenOrDisabled(pm: PackageManager, appInfo: ApplicationInfo): Boolean {
        return TelosPackageRepository.isPackageFrozenOrDisabled(pm, appInfo)
    }

    suspend fun scan(
        context: Context,
        targetProfile: String,
        skipFrozenApps: Boolean = false,
    ): StartupAuditReport = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val appContext = context.applicationContext
        TelosShellEngine.init(appContext)

        val installedApps = TelosPackageRepository.getInstance(appContext)
            .awaitPackages()
            .filter { it.packageName != appContext.packageName }

        val privilegedOk = TelosShellEngine.isPrivilegedAvailable(appContext)
        val rawDexoptMap = if (privilegedOk) {
            val primaryDump = dumpDexoptStates("dumpsys package dexopt")
            if (primaryDump.isNotEmpty()) primaryDump else dumpDexoptStates("dumpsys package")
        } else {
            emptyMap()
        }

        val odexPaths = rawDexoptMap.values
            .mapNotNull { it.odexPath }
            .filter { SAFE_PATH_REGEX.matches(it) }
            .distinct()
        val odexTimestamps = if (privilegedOk && odexPaths.isNotEmpty()) {
            batchStatOdexModificationMs(odexPaths)
        } else {
            emptyMap()
        }

        val upToDate = ArrayList<AuditedAppEntry>()
        val needsOptimization = ArrayList<AuditedAppEntry>()
        val degraded = ArrayList<AuditedAppEntry>()
        val frozenSkipped = ArrayList<AuditedAppEntry>()
        val allEntries = ArrayList<AuditedAppEntry>(installedApps.size)

        for (pkgEntry in installedApps) {
            val pkg = pkgEntry.packageName
            val dexopt = rawDexoptMap[pkg]
            val currentFilter = dexopt?.currentFilter ?: "unknown"
            val reason = dexopt?.reason ?: "unknown"
            val odexMtime = dexopt?.odexPath?.let { path ->
                odexTimestamps[path] ?: File(path).takeIf { it.exists() }?.lastModified() ?: 0L
            } ?: 0L

            val bucket = if (!privilegedOk) {
                if (skipFrozenApps && pkgEntry.isFrozen) OptimizationBucket.FROZEN_SKIPPED
                else OptimizationBucket.NEEDS_OPTIMIZATION
            } else {
                categorizePackage(
                    currentFilter = currentFilter,
                    reason = reason,
                    targetProfile = targetProfile,
                    firstInstallTime = pkgEntry.firstInstallTime,
                    lastUpdateTime = pkgEntry.lastUpdateTime,
                    odexModifiedMs = odexMtime,
                    isFrozen = pkgEntry.isFrozen,
                    skipFrozenApps = skipFrozenApps,
                )
            }

            val entry = AuditedAppEntry(
                packageName = pkg,
                label = pkgEntry.label,
                versionCode = pkgEntry.versionCode,
                apkBytes = pkgEntry.apkBytes,
                isSystemApp = pkgEntry.isSystemApp,
                isFrozen = pkgEntry.isFrozen,
                currentFilter = currentFilter,
                compilationReason = reason,
                firstInstallTime = pkgEntry.firstInstallTime,
                lastUpdateTime = pkgEntry.lastUpdateTime,
                odexModifiedMs = odexMtime,
                bucket = bucket,
            )
            allEntries += entry
            when (bucket) {
                OptimizationBucket.UP_TO_DATE -> upToDate += entry
                OptimizationBucket.NEEDS_OPTIMIZATION -> needsOptimization += entry
                OptimizationBucket.DEGRADED -> degraded += entry
                OptimizationBucket.FROZEN_SKIPPED -> frozenSkipped += entry
            }
        }

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        StartupAuditReport(
            targetProfile = targetProfile,
            upToDate = upToDate,
            needsOptimization = needsOptimization,
            degraded = degraded,
            frozenSkipped = frozenSkipped,
            allEntries = allEntries,
            estimatedMinutesSaved = estimateMinutesSaved(upToDate.size, targetProfile),
            scanDurationMs = elapsed,
            privilegedAvailable = privilegedOk,
        ).also { report ->
            lastCachedReport = report
        }
    }

    private suspend fun dumpDexoptStates(command: String): Map<String, PackageDexoptState> {
        return TelosShellEngine.execLineSequence(command, timeoutMs = 20_000L) { lines ->
            parseDumpsysDexopt(lines)
        } ?: emptyMap()
    }

    private suspend fun batchStatOdexModificationMs(paths: List<String>): Map<String, Long> {
        val out = HashMap<String, Long>(paths.size)
        for (chunk in paths.chunked(100)) {
            val quoted = chunk.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val cmd = "stat -c '%Y|%n' $quoted 2>/dev/null"
            TelosShellEngine.execLineSequence(cmd, timeoutMs = 10_000L) { lines ->
                for (line in lines) {
                    val sep = line.indexOf('|')
                    if (sep <= 0) continue
                    val epochSec = line.substring(0, sep).trim().toLongOrNull() ?: continue
                    val filePath = line.substring(sep + 1).trim()
                    if (epochSec > 0L && filePath.isNotEmpty()) {
                        out[filePath] = epochSec * 1000L
                    }
                }
            }
        }
        return out
    }
}
