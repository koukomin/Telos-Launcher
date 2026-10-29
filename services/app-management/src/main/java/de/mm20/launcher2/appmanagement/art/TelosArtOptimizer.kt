package de.mm20.launcher2.appmanagement.art

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.compose.runtime.Immutable
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.packages.TelosPackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@Immutable
data class OptimizationProgress(
    val currentPackage: String = "",
    val currentIndex: Int = 0,
    val totalPackages: Int = 0,
    val isRunning: Boolean = false,
    val succeededCount: Int = 0,
    val failedCount: Int = 0,
    val strippedFrozenCount: Int = 0,
    val upgradedEmptyProfileCount: Int = 0,
    val lastResult: String? = null,
)

@Immutable
data class CompilePackageOutcome(
    val success: Boolean,
    val effectiveProfile: String,
    val upgradedFromEmptyProfile: Boolean = false,
)

@Immutable
data class ColdStartMeasurement(
    val packageName: String,
    val componentName: String,
    val totalTimeMs: Long,
    val waitTimeMs: Long,
    val launchState: String,
)

sealed interface BenchmarkOutcome {
    data class Success(val measurement: ColdStartMeasurement) : BenchmarkOutcome
    data class FrozenAppError(val packageName: String) : BenchmarkOutcome
    data class NoLaunchActivityError(val packageName: String) : BenchmarkOutcome
    data object PrivilegedUnavailable : BenchmarkOutcome
    data class ExecutionError(val reason: String) : BenchmarkOutcome
}

@Immutable
data class ProfileDiagnosticResult(
    val packageName: String,
    val hasEmbeddedBaselineProf: Boolean,
    val hasDexMetadataDm: Boolean,
    val refProfileBytes: Long,
    val curProfileBytes: Long,
    val currentFilter: String,
    val compilationReason: String,
) {
    val hasUsableProfile: Boolean
        get() = hasEmbeddedBaselineProf ||
            hasDexMetadataDm ||
            refProfileBytes > TelosArtOptimizer.MIN_VALID_PROF_BYTES ||
            curProfileBytes > TelosArtOptimizer.MIN_VALID_PROF_BYTES

    val recommendsFullSpeedFallback: Boolean
        get() = !hasUsableProfile
}

object TelosArtOptimizer {
    private const val TAG = "TelosArtOptimizer"

    const val PROFILE_SPEED_PROFILE = "speed-profile"
    const val PROFILE_SPEED = "speed"
    const val PROFILE_EVERYTHING = "everything"
    const val PROFILE_RESET = "reset"

    const val FROZEN_MODE_SKIP = "skip"
    const val FROZEN_MODE_STRIP_RESET = "strip_reset"

    /** Initialized empty ART profile headers occupy <= 64 bytes with 0 hot methods. */
    const val MIN_VALID_PROF_BYTES = 64L

    private val VALID_PACKAGE_REGEX = Regex("""^[a-zA-Z0-9._]+$""")
    private val VALID_COMPONENT_CLASS_REGEX = Regex("""^[a-zA-Z0-9._$]+$""")
    private val AM_TOTAL_TIME_REGEX = Regex("""TotalTime:\s*(\d+)""")
    private val AM_WAIT_TIME_REGEX = Regex("""WaitTime:\s*(\d+)""")
    private val AM_LAUNCH_STATE_REGEX = Regex("""LaunchState:\s*([A-Z_]+)""")

    private val _progress = MutableStateFlow(OptimizationProgress())
    val progress: StateFlow<OptimizationProgress> = _progress.asStateFlow()

    suspend fun isPrivilegedAvailable(context: Context): Boolean {
        return TelosShellEngine.isPrivilegedAvailable(context.applicationContext)
    }

    /**
     * Inspects whether [packageName] has a bundled Baseline Profile (`assets/dexopt/baseline.prof`),
     * Cloud Profile DexMetadata (`.dm`), or populated ART `primary.prof` (`ref` / `cur`).
     */
    suspend fun inspectPackageProfile(
        context: Context,
        packageName: String,
        cachedDexoptState: PackageDexoptState? = null,
    ): ProfileDiagnosticResult = withContext(Dispatchers.IO) {
        if (!VALID_PACKAGE_REGEX.matches(packageName)) {
            return@withContext ProfileDiagnosticResult(
                packageName = packageName,
                hasEmbeddedBaselineProf = false,
                hasDexMetadataDm = false,
                refProfileBytes = 0L,
                curProfileBytes = 0L,
                currentFilter = "unknown",
                compilationReason = "unknown",
            )
        }

        val appContext = context.applicationContext
        val repoEntry = TelosPackageRepository.getInstance(appContext).getSnapshotEntry(packageName)
        val sourceDir = repoEntry?.sourceDir ?: runCatching {
            appContext.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS).sourceDir
        }.getOrNull()

        var hasEmbeddedBaseline = false
        var hasDmFile = false

        if (!sourceDir.isNullOrEmpty()) {
            val apkFile = File(sourceDir)
            if (apkFile.exists()) {
                val parent = apkFile.parentFile
                if (parent != null) {
                    val baseDm = File(parent, apkFile.nameWithoutExtension + ".dm")
                    hasDmFile = baseDm.exists() && baseDm.length() > 0L
                }
                hasEmbeddedBaseline = runCatching {
                    ZipFile(apkFile).use { zip ->
                        val entry = zip.getEntry("assets/dexopt/baseline.prof")
                        entry != null && entry.size > 0L
                    }
                }.getOrDefault(false)
            }
        }

        val userId = Process.myUid() / 100000
        val refPath = "/data/misc/profiles/ref/$packageName/primary.prof"
        val curPath = "/data/misc/profiles/cur/$userId/$packageName/primary.prof"

        var refBytes = 0L
        var curBytes = 0L
        var filter = cachedDexoptState?.currentFilter ?: "unknown"
        var reason = cachedDexoptState?.reason ?: "unknown"

        if (isPrivilegedAvailable(appContext)) {
            val statCmd = buildString {
                append("REF=\$(stat -c '%s' '$refPath' 2>/dev/null || echo 0); ")
                append("CUR=\$(stat -c '%s' '$curPath' 2>/dev/null || echo 0); ")
                append("echo \"PROF|\$REF|\$CUR\"")
            }
            val statRes = TelosShellEngine.execResult(statCmd, timeoutMs = 8_000L)
            if (statRes.isSuccess) {
                val profLine = statRes.stdout.lineSequence().firstOrNull { it.startsWith("PROF|") }
                if (profLine != null) {
                    val parts = profLine.split('|')
                    if (parts.size >= 3) {
                        refBytes = parts[1].trim().toLongOrNull() ?: 0L
                        curBytes = parts[2].trim().toLongOrNull() ?: 0L
                    }
                }
            }

            if (cachedDexoptState == null) {
                val dumpRes = TelosShellEngine.execResult(
                    command = "dumpsys package dexopt | grep -A 6 '\\[$packageName\\]'",
                    timeoutMs = 10_000L,
                )
                if (dumpRes.isSuccess && dumpRes.stdout.isNotBlank()) {
                    val parsed = StartupOptimizationAudit.parseDumpsysDexopt(dumpRes.stdout.lineSequence())[packageName]
                    if (parsed != null) {
                        filter = parsed.currentFilter
                        reason = parsed.reason
                    }
                }
            }
        }

        ProfileDiagnosticResult(
            packageName = packageName,
            hasEmbeddedBaselineProf = hasEmbeddedBaseline,
            hasDexMetadataDm = hasDmFile,
            refProfileBytes = refBytes,
            curProfileBytes = curBytes,
            currentFilter = filter,
            compilationReason = reason,
        )
    }

    /**
     * Compiles a single package via [TelosShellEngine]. When [profile] is [PROFILE_SPEED_PROFILE] and
     * [fallbackEmptyProfileToSpeed] is enabled, automatically falls back to [PROFILE_SPEED]
     * if [packageName] has no valid Baseline/Cloud/JIT `primary.prof`.
     */
    suspend fun optimizePackageDetailed(
        context: Context,
        packageName: String,
        profile: String = PROFILE_SPEED_PROFILE,
        fallbackEmptyProfileToSpeed: Boolean = true,
    ): CompilePackageOutcome = withContext(Dispatchers.IO) {
        if (!VALID_PACKAGE_REGEX.matches(packageName)) {
            return@withContext CompilePackageOutcome(false, profile)
        }
        val appContext = context.applicationContext
        if (!isPrivilegedAvailable(appContext)) {
            return@withContext CompilePackageOutcome(false, profile)
        }

        var effectiveProfile = profile
        var upgraded = false
        if (profile == PROFILE_SPEED_PROFILE && fallbackEmptyProfileToSpeed) {
            val diag = inspectPackageProfile(appContext, packageName)
            if (diag.recommendsFullSpeedFallback) {
                effectiveProfile = PROFILE_SPEED
                upgraded = true
                Log.i(TAG, "Empty primary.prof for $packageName; auto-upgrading speed-profile -> speed")
            }
        }

        val cmd = when (effectiveProfile) {
            PROFILE_RESET -> "cmd package compile --reset $packageName"
            PROFILE_SPEED_PROFILE, PROFILE_SPEED, PROFILE_EVERYTHING ->
                "cmd package compile -m $effectiveProfile -f $packageName"
            else -> return@withContext CompilePackageOutcome(false, effectiveProfile)
        }

        val result = TelosShellEngine.execResult(cmd, timeoutMs = 180_000L)
        Log.d(TAG, "Package $packageName compiled with profile $effectiveProfile, success=${result.isSuccess}")
        CompilePackageOutcome(
            success = result.isSuccess,
            effectiveProfile = effectiveProfile,
            upgradedFromEmptyProfile = upgraded,
        )
    }

    suspend fun optimizePackage(
        context: Context,
        packageName: String,
        profile: String = PROFILE_SPEED_PROFILE,
        fallbackEmptyProfileToSpeed: Boolean = true,
    ): Boolean {
        return optimizePackageDetailed(
            context = context,
            packageName = packageName,
            profile = profile,
            fallbackEmptyProfileToSpeed = fallbackEmptyProfileToSpeed,
        ).success
    }

    suspend fun resetPackage(context: Context, packageName: String): Boolean {
        return optimizePackage(context, packageName, PROFILE_RESET, fallbackEmptyProfileToSpeed = false)
    }

    suspend fun trimBgDexopt(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isPrivilegedAvailable(context)) return@withContext false
        TelosShellEngine.execResult("cmd package bg-dexopt-job", timeoutMs = 300_000L).isSuccess
    }

    /**
     * Smart Differential Optimization:
     * Only compiles packages in `needsOptimization` + `degraded` buckets from [StartupOptimizationAudit].
     */
    suspend fun optimizeSmartPackages(
        context: Context,
        profile: String = PROFILE_SPEED_PROFILE,
        skipFrozenApps: Boolean = false,
        frozenAppsMode: String = FROZEN_MODE_SKIP,
        fallbackEmptyProfileToSpeed: Boolean = true,
        existingAudit: StartupAuditReport? = null,
        onProgress: ((OptimizationProgress) -> Unit)? = null,
    ): StartupAuditReport? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!isPrivilegedAvailable(appContext)) return@withContext null

        val audit = existingAudit?.takeIf {
            it.targetProfile == profile && it.privilegedAvailable
        } ?: StartupOptimizationAudit.scan(
            context = appContext,
            targetProfile = profile,
            skipFrozenApps = skipFrozenApps,
        )

        val compileTargets = audit.actionableEntries.map { it.packageName }
        val stripTargets = if (skipFrozenApps && frozenAppsMode == FROZEN_MODE_STRIP_RESET) {
            audit.frozenSkipped
                .filter { StartupOptimizationAudit.filterRank(it.currentFilter) > 0 }
                .map { it.packageName }
        } else {
            emptyList()
        }

        val totalWork = compileTargets.size + stripTargets.size
        if (totalWork == 0) {
            _progress.value = OptimizationProgress(
                currentPackage = "",
                currentIndex = 0,
                totalPackages = 0,
                isRunning = false,
                lastResult = "UpToDate",
            )
            onProgress?.invoke(_progress.value)
            return@withContext audit
        }

        var succeeded = 0
        var failed = 0
        var stripped = 0
        var upgradedEmpty = 0
        var currentStep = 0

        _progress.value = OptimizationProgress(
            currentPackage = "",
            currentIndex = 0,
            totalPackages = totalWork,
            isRunning = true,
        )
        onProgress?.invoke(_progress.value)

        for (pkg in compileTargets) {
            if (!_progress.value.isRunning) break
            currentStep++
            _progress.value = _progress.value.copy(
                currentPackage = pkg,
                currentIndex = currentStep,
                totalPackages = totalWork,
            )
            onProgress?.invoke(_progress.value)

            val outcome = optimizePackageDetailed(
                context = appContext,
                packageName = pkg,
                profile = profile,
                fallbackEmptyProfileToSpeed = fallbackEmptyProfileToSpeed,
            )
            if (outcome.success) {
                succeeded++
                if (outcome.upgradedFromEmptyProfile) upgradedEmpty++
            } else {
                failed++
            }
            val statusLabel = if (outcome.success) "Success (${outcome.effectiveProfile})" else "Failed"
            _progress.value = _progress.value.copy(
                succeededCount = succeeded,
                failedCount = failed,
                strippedFrozenCount = stripped,
                upgradedEmptyProfileCount = upgradedEmpty,
                lastResult = "$pkg: $statusLabel",
            )
            onProgress?.invoke(_progress.value)
        }

        for (pkg in stripTargets) {
            if (!_progress.value.isRunning) break
            currentStep++
            _progress.value = _progress.value.copy(
                currentPackage = pkg,
                currentIndex = currentStep,
                totalPackages = totalWork,
            )
            onProgress?.invoke(_progress.value)

            val ok = resetPackage(appContext, pkg)
            if (ok) stripped++ else failed++
            _progress.value = _progress.value.copy(
                succeededCount = succeeded,
                failedCount = failed,
                strippedFrozenCount = stripped,
                upgradedEmptyProfileCount = upgradedEmpty,
                lastResult = "$pkg: ${if (ok) "Stripped (--reset)" else "Strip Failed"}",
            )
            onProgress?.invoke(_progress.value)
        }

        _progress.value = _progress.value.copy(isRunning = false)
        onProgress?.invoke(_progress.value)

        StartupOptimizationAudit.scan(
            context = appContext,
            targetProfile = profile,
            skipFrozenApps = skipFrozenApps,
        )
    }

    /**
     * Force Recompile All (or Reset All when [profile] == [PROFILE_RESET]).
     * Consumes [TelosPackageRepository] to avoid redundant PackageManager IPC scans.
     */
    suspend fun optimizeAllPackages(
        context: Context,
        profile: String = PROFILE_SPEED_PROFILE,
        skipFrozenApps: Boolean = false,
        frozenAppsMode: String = FROZEN_MODE_SKIP,
        fallbackEmptyProfileToSpeed: Boolean = true,
        onProgress: ((OptimizationProgress) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!isPrivilegedAvailable(appContext)) return@withContext

        val installed = TelosPackageRepository.getInstance(appContext)
            .awaitPackages()
            .filter { it.packageName != appContext.packageName }

        val compileList = ArrayList<String>()
        val stripList = ArrayList<String>()
        for (entry in installed) {
            if (profile == PROFILE_RESET) {
                compileList += entry.packageName
                continue
            }
            if (skipFrozenApps && entry.isFrozen) {
                if (frozenAppsMode == FROZEN_MODE_STRIP_RESET) {
                    stripList += entry.packageName
                }
            } else {
                compileList += entry.packageName
            }
        }

        val total = compileList.size + stripList.size
        var succeeded = 0
        var failed = 0
        var stripped = 0
        var upgradedEmpty = 0
        var step = 0

        _progress.value = OptimizationProgress(
            currentPackage = "",
            currentIndex = 0,
            totalPackages = total,
            isRunning = true,
        )
        onProgress?.invoke(_progress.value)

        for (pkg in compileList) {
            if (!_progress.value.isRunning) break
            step++
            _progress.value = _progress.value.copy(
                currentPackage = pkg,
                currentIndex = step,
                totalPackages = total,
            )
            onProgress?.invoke(_progress.value)

            val outcome = optimizePackageDetailed(
                context = appContext,
                packageName = pkg,
                profile = profile,
                fallbackEmptyProfileToSpeed = fallbackEmptyProfileToSpeed && profile == PROFILE_SPEED_PROFILE,
            )
            if (outcome.success) {
                succeeded++
                if (outcome.upgradedFromEmptyProfile) upgradedEmpty++
            } else {
                failed++
            }
            _progress.value = _progress.value.copy(
                succeededCount = succeeded,
                failedCount = failed,
                strippedFrozenCount = stripped,
                upgradedEmptyProfileCount = upgradedEmpty,
                lastResult = "$pkg: ${if (outcome.success) "Success" else "Failed"}",
            )
            onProgress?.invoke(_progress.value)
        }

        for (pkg in stripList) {
            if (!_progress.value.isRunning) break
            step++
            _progress.value = _progress.value.copy(
                currentPackage = pkg,
                currentIndex = step,
                totalPackages = total,
            )
            onProgress?.invoke(_progress.value)

            val ok = resetPackage(appContext, pkg)
            if (ok) stripped++ else failed++
            _progress.value = _progress.value.copy(
                succeededCount = succeeded,
                failedCount = failed,
                strippedFrozenCount = stripped,
                upgradedEmptyProfileCount = upgradedEmpty,
                lastResult = "$pkg: ${if (ok) "Stripped" else "Failed"}",
            )
            onProgress?.invoke(_progress.value)
        }

        _progress.value = _progress.value.copy(isRunning = false)
        onProgress?.invoke(_progress.value)
    }

    /**
     * Strips compiled `.odex` artifacts (`cmd package compile --reset`) strictly on currently
     * frozen/disabled applications to reclaim internal storage.
     */
    suspend fun stripFrozenPackages(
        context: Context,
        onProgress: ((OptimizationProgress) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!isPrivilegedAvailable(appContext)) return@withContext 0

        val frozenApps = TelosPackageRepository.getInstance(appContext)
            .awaitPackages()
            .filter { it.packageName != appContext.packageName && it.isFrozen }
            .map { it.packageName }

        val total = frozenApps.size
        if (total == 0) return@withContext 0

        var stripped = 0
        var failed = 0
        _progress.value = OptimizationProgress(
            currentPackage = "",
            currentIndex = 0,
            totalPackages = total,
            isRunning = true,
        )
        onProgress?.invoke(_progress.value)

        for ((index, pkg) in frozenApps.withIndex()) {
            if (!_progress.value.isRunning) break
            _progress.value = _progress.value.copy(
                currentPackage = pkg,
                currentIndex = index + 1,
                totalPackages = total,
            )
            onProgress?.invoke(_progress.value)

            val ok = resetPackage(appContext, pkg)
            if (ok) stripped++ else failed++
            _progress.value = _progress.value.copy(
                strippedFrozenCount = stripped,
                failedCount = failed,
                lastResult = "$pkg: ${if (ok) "Stripped" else "Failed"}",
            )
            onProgress?.invoke(_progress.value)
        }

        _progress.value = _progress.value.copy(isRunning = false)
        onProgress?.invoke(_progress.value)
        stripped
    }

    fun cancelBulkOptimization() {
        _progress.value = _progress.value.copy(isRunning = false)
    }

    /**
     * Pure parser for `am start -W` terminal output (`TotalTime`, `WaitTime`, `LaunchState`).
     */
    fun parseAmStartOutput(
        packageName: String,
        componentName: String,
        output: String,
    ): ColdStartMeasurement? {
        val totalTime = AM_TOTAL_TIME_REGEX.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val waitTime = AM_WAIT_TIME_REGEX.find(output)?.groupValues?.get(1)?.toLongOrNull()
        val effectiveTotal = totalTime ?: waitTime ?: return null
        val effectiveWait = waitTime ?: effectiveTotal
        val state = AM_LAUNCH_STATE_REGEX.find(output)?.groupValues?.get(1) ?: "COLD"
        return ColdStartMeasurement(
            packageName = packageName,
            componentName = componentName,
            totalTimeMs = effectiveTotal,
            waitTimeMs = effectiveWait,
            launchState = state,
        )
    }

    /**
     * Real Cold-Start App Benchmark (`AppLaunchBenchmark`):
     * 1. `am force-stop <packageName>` (ensures a true cold start)
     * 2. `am start -W -n <packageName>/<main_launch_activity>`
     * 3. Parses `TotalTime` & `WaitTime` in milliseconds.
     */
    suspend fun benchmarkColdStart(
        context: Context,
        packageName: String,
    ): BenchmarkOutcome = withContext(Dispatchers.IO) {
        if (!VALID_PACKAGE_REGEX.matches(packageName)) {
            return@withContext BenchmarkOutcome.ExecutionError("Invalid package name")
        }
        val appContext = context.applicationContext
        if (!isPrivilegedAvailable(appContext)) {
            return@withContext BenchmarkOutcome.PrivilegedUnavailable
        }

        val pm = appContext.packageManager
        val appInfo = runCatching {
            pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrNull() ?: return@withContext BenchmarkOutcome.NoLaunchActivityError(packageName)

        if (StartupOptimizationAudit.isPackageFrozenOrDisabled(pm, appInfo)) {
            return@withContext BenchmarkOutcome.FrozenAppError(packageName)
        }

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val resolvedActivity = pm.resolveActivity(launchIntent, 0)?.activityInfo
            ?: return@withContext BenchmarkOutcome.NoLaunchActivityError(packageName)

        val activityClass = resolvedActivity.name
        if (!VALID_COMPONENT_CLASS_REGEX.matches(activityClass)) {
            return@withContext BenchmarkOutcome.ExecutionError("Invalid launch component")
        }

        val flattened = ComponentName(packageName, activityClass).flattenToShortString()

        TelosShellEngine.execResult("am force-stop $packageName", timeoutMs = 10_000L)
        delay(350L)

        val amResult = TelosShellEngine.execResult(
            command = "am start -W -n '$flattened'",
            timeoutMs = 30_000L,
        )
        if (!amResult.isSuccess || amResult.stdout.isBlank()) {
            return@withContext BenchmarkOutcome.ExecutionError("am start timed out or failed")
        }

        val measurement = parseAmStartOutput(
            packageName = packageName,
            componentName = flattened,
            output = amResult.stdout,
        ) ?: return@withContext BenchmarkOutcome.ExecutionError("Could not parse TotalTime from am start output")

        BenchmarkOutcome.Success(measurement)
    }

    suspend fun optimizeAppDatabases(context: Context): SqliteOptimizationState {
        return SqliteVacuumEngine.optimizeDatabases(context)
    }
}
