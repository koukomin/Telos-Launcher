package de.mm20.launcher2.ui.settings.art

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appmanagement.art.ArtOptimizationWorker
import de.mm20.launcher2.appmanagement.art.AuditedAppEntry
import de.mm20.launcher2.appmanagement.art.BenchmarkOutcome
import de.mm20.launcher2.appmanagement.art.OptimizationProgress
import de.mm20.launcher2.appmanagement.art.PackageDexoptState
import de.mm20.launcher2.appmanagement.art.ProfileDiagnosticResult
import de.mm20.launcher2.appmanagement.art.SqliteOptimizationState
import de.mm20.launcher2.appmanagement.art.SqliteVacuumEngine
import de.mm20.launcher2.appmanagement.art.StartupAuditReport
import de.mm20.launcher2.appmanagement.art.StartupOptimizationAudit
import de.mm20.launcher2.appmanagement.art.TelosArtOptimizer
import de.mm20.launcher2.preferences.ArtBenchmarkRecord
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class ArtAppFilter {
    ALL,
    NEEDS_OPTIMIZATION,
    UP_TO_DATE,
    DEGRADED,
    FROZEN,
}

enum class BenchmarkUiError {
    FROZEN,
    NO_ACTIVITY,
    FAILED,
}

class ArtOptimizerViewModel : ViewModel(), KoinComponent {

    private val uiSettings: UiSettings by inject()

    val masterEnabled = uiSettings.artOptimizerMasterEnabled
    val profile = uiSettings.artOptimizationProfile
    val scheduled = uiSettings.artOptimizationScheduled
    val frequencyDays = uiSettings.artOptimizationFrequencyDays
    val requireCharging = uiSettings.artOptimizationRequireCharging
    val requireIdle = uiSettings.artOptimizationRequireIdle
    val requireBatteryNotLow = uiSettings.artOptimizationRequireBatteryNotLow
    val skipPowerSave = uiSettings.artOptimizationSkipPowerSave
    val storePostInstall = uiSettings.artOptimizationStorePostInstall
    val skipFrozenApps = uiSettings.artSkipFrozenApps
    val frozenAppsMode = uiSettings.artFrozenAppsMode
    val sqliteOptimizeEnabled = uiSettings.artSqliteOptimizeEnabled
    val fallbackEmptyProfileToSpeed = uiSettings.artFallbackEmptyProfileToSpeed
    val benchmarkRecords = uiSettings.artBenchmarkRecords

    val progress: StateFlow<OptimizationProgress> = TelosArtOptimizer.progress
    val sqliteState: StateFlow<SqliteOptimizationState> = SqliteVacuumEngine.state

    private val _auditReport = MutableStateFlow<StartupAuditReport?>(null)
    val auditReport: StateFlow<StartupAuditReport?> = _auditReport.asStateFlow()

    private val _isScanningAudit = MutableStateFlow(false)
    val isScanningAudit: StateFlow<Boolean> = _isScanningAudit.asStateFlow()

    private val _privilegedAvailable = MutableStateFlow(true)
    val privilegedAvailable: StateFlow<Boolean> = _privilegedAvailable.asStateFlow()

    private val _selectedApp = MutableStateFlow<AuditedAppEntry?>(null)
    val selectedApp: StateFlow<AuditedAppEntry?> = _selectedApp.asStateFlow()

    private val _selectedAppDiagnostic = MutableStateFlow<ProfileDiagnosticResult?>(null)
    val selectedAppDiagnostic: StateFlow<ProfileDiagnosticResult?> = _selectedAppDiagnostic.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkError = MutableStateFlow<BenchmarkUiError?>(null)
    val benchmarkError: StateFlow<BenchmarkUiError?> = _benchmarkError.asStateFlow()

    private val _isSingleAppBusy = MutableStateFlow(false)
    val isSingleAppBusy: StateFlow<Boolean> = _isSingleAppBusy.asStateFlow()

    private var scanJob: Job? = null

    fun refreshAudit(context: Context, targetProfileOverride: String? = null, skipFrozenOverride: Boolean? = null) {
        val appContext = context.applicationContext
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanningAudit.value = true
            try {
                val priv = TelosArtOptimizer.isPrivilegedAvailable(appContext)
                _privilegedAvailable.value = priv
                val targetProfile = targetProfileOverride ?: uiSettings.artOptimizationProfile.first()
                val skipFrozen = skipFrozenOverride ?: uiSettings.artSkipFrozenApps.first()
                val report = StartupOptimizationAudit.scan(
                    context = appContext,
                    targetProfile = targetProfile,
                    skipFrozenApps = skipFrozen,
                )
                _auditReport.value = report
                // Keep open bottom-sheet entry synchronized if still open
                val currentSel = _selectedApp.value
                if (currentSel != null) {
                    val refreshed = report.allEntries.firstOrNull { it.packageName == currentSel.packageName }
                    if (refreshed != null) {
                        _selectedApp.value = refreshed
                    }
                }
            } finally {
                _isScanningAudit.value = false
            }
        }
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        uiSettings.setArtOptimizerMasterEnabled(enabled)
        if (!enabled) {
            cancelOptimization()
            ArtOptimizationWorker.cancel(appContext)
            uiSettings.setArtOptimizationScheduled(false)
        } else {
            refreshAudit(appContext)
        }
    }

    fun setProfile(context: Context, profileName: String) {
        uiSettings.setArtOptimizationProfile(profileName)
        refreshAudit(context, targetProfileOverride = profileName)
    }

    fun setFallbackEmptyProfileToSpeed(enabled: Boolean) {
        uiSettings.setArtFallbackEmptyProfileToSpeed(enabled)
    }

    fun setStorePostInstall(enabled: Boolean) {
        uiSettings.setArtOptimizationStorePostInstall(enabled)
    }

    fun setSkipFrozenApps(context: Context, enabled: Boolean) {
        uiSettings.setArtSkipFrozenApps(enabled)
        refreshAudit(context, skipFrozenOverride = enabled)
    }

    fun setFrozenAppsMode(mode: String) {
        uiSettings.setArtFrozenAppsMode(mode)
    }

    fun setSqliteOptimizeEnabled(enabled: Boolean) {
        uiSettings.setArtSqliteOptimizeEnabled(enabled)
    }

    fun setScheduled(
        context: Context,
        enabled: Boolean,
        days: Long,
        charging: Boolean,
        idle: Boolean,
        batteryNotLow: Boolean
    ) {
        val appContext = context.applicationContext
        uiSettings.setArtOptimizationScheduled(enabled)
        if (enabled) {
            ArtOptimizationWorker.schedule(appContext, days, charging, idle, batteryNotLow)
        } else {
            ArtOptimizationWorker.cancel(appContext)
        }
    }

    fun setFrequencyDays(
        context: Context,
        days: Long,
        charging: Boolean,
        idle: Boolean,
        batteryNotLow: Boolean
    ) {
        uiSettings.setArtOptimizationFrequencyDays(days)
        ArtOptimizationWorker.schedule(context.applicationContext, days, charging, idle, batteryNotLow)
    }

    fun setRequireCharging(
        context: Context,
        enabled: Boolean,
        days: Long,
        idle: Boolean,
        batteryNotLow: Boolean
    ) {
        uiSettings.setArtOptimizationRequireCharging(enabled)
        ArtOptimizationWorker.schedule(context.applicationContext, days, enabled, idle, batteryNotLow)
    }

    fun setRequireIdle(
        context: Context,
        enabled: Boolean,
        days: Long,
        charging: Boolean,
        batteryNotLow: Boolean
    ) {
        uiSettings.setArtOptimizationRequireIdle(enabled)
        ArtOptimizationWorker.schedule(context.applicationContext, days, charging, enabled, batteryNotLow)
    }

    fun setRequireBatteryNotLow(
        context: Context,
        enabled: Boolean,
        days: Long,
        charging: Boolean,
        idle: Boolean
    ) {
        uiSettings.setArtOptimizationRequireBatteryNotLow(enabled)
        ArtOptimizationWorker.schedule(context.applicationContext, days, charging, idle, enabled)
    }

    fun setSkipPowerSave(enabled: Boolean) {
        uiSettings.setArtOptimizationSkipPowerSave(enabled)
    }

    fun startSmartOptimization(
        context: Context,
        selectedProfile: String,
        skipFrozen: Boolean,
        frozenMode: String,
        fallbackEmpty: Boolean,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val updated = TelosArtOptimizer.optimizeSmartPackages(
                context = appContext,
                profile = selectedProfile,
                skipFrozenApps = skipFrozen,
                frozenAppsMode = frozenMode,
                fallbackEmptyProfileToSpeed = fallbackEmpty,
                existingAudit = _auditReport.value,
            )
            if (updated != null) {
                _auditReport.value = updated
            } else {
                refreshAudit(appContext)
            }
        }
    }

    fun startForceRecompileAll(
        context: Context,
        selectedProfile: String,
        skipFrozen: Boolean,
        frozenMode: String,
        fallbackEmpty: Boolean,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            TelosArtOptimizer.optimizeAllPackages(
                context = appContext,
                profile = selectedProfile,
                skipFrozenApps = skipFrozen,
                frozenAppsMode = frozenMode,
                fallbackEmptyProfileToSpeed = fallbackEmpty,
            )
            refreshAudit(appContext)
        }
    }

    fun resetAllOptimizations(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            TelosArtOptimizer.optimizeAllPackages(
                context = appContext,
                profile = TelosArtOptimizer.PROFILE_RESET,
                skipFrozenApps = false,
                fallbackEmptyProfileToSpeed = false,
            )
            refreshAudit(appContext)
        }
    }

    fun stripFrozenAppsNow(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            TelosArtOptimizer.stripFrozenPackages(appContext)
            refreshAudit(appContext)
        }
    }

    fun trimBgDexopt(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            TelosArtOptimizer.trimBgDexopt(appContext)
            refreshAudit(appContext)
        }
    }

    fun runSqliteVacuumNow(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            TelosArtOptimizer.optimizeAppDatabases(appContext)
        }
    }

    fun cancelOptimization() {
        TelosArtOptimizer.cancelBulkOptimization()
        SqliteVacuumEngine.cancel()
    }

    fun selectApp(context: Context, entry: AuditedAppEntry?) {
        _selectedApp.value = entry
        _selectedAppDiagnostic.value = null
        _benchmarkError.value = null
        if (entry == null) return

        val appContext = context.applicationContext
        viewModelScope.launch {
            val cached = PackageDexoptState(
                packageName = entry.packageName,
                currentFilter = entry.currentFilter,
                reason = entry.compilationReason,
                odexModifiedMs = entry.odexModifiedMs,
            )
            _selectedAppDiagnostic.value = TelosArtOptimizer.inspectPackageProfile(
                context = appContext,
                packageName = entry.packageName,
                cachedDexoptState = cached,
            )
        }
    }

    fun compileSingleApp(
        context: Context,
        packageName: String,
        targetProfile: String,
        fallbackEmpty: Boolean,
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isSingleAppBusy.value = true
            try {
                TelosArtOptimizer.optimizePackageDetailed(
                    context = appContext,
                    packageName = packageName,
                    profile = targetProfile,
                    fallbackEmptyProfileToSpeed = fallbackEmpty,
                )
                refreshAudit(appContext)
                _selectedAppDiagnostic.value = TelosArtOptimizer.inspectPackageProfile(
                    context = appContext,
                    packageName = packageName,
                )
            } finally {
                _isSingleAppBusy.value = false
            }
        }
    }

    fun runBenchmarkForApp(context: Context, packageName: String, currentFilter: String) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBenchmarking.value = true
            _benchmarkError.value = null
            try {
                when (val outcome = TelosArtOptimizer.benchmarkColdStart(appContext, packageName)) {
                    is BenchmarkOutcome.Success -> {
                        val m = outcome.measurement
                        val now = System.currentTimeMillis()
                        uiSettings.updateArtBenchmarkRecord(packageName) { existing ->
                            if (existing?.beforeMs == null) {
                                ArtBenchmarkRecord(
                                    beforeMs = m.totalTimeMs,
                                    waitBeforeMs = m.waitTimeMs,
                                    updatedAt = now,
                                )
                            } else {
                                existing.copy(
                                    afterMs = m.totalTimeMs,
                                    waitAfterMs = m.waitTimeMs,
                                    profileAfter = currentFilter,
                                    updatedAt = now,
                                )
                            }
                        }
                    }
                    is BenchmarkOutcome.FrozenAppError -> _benchmarkError.value = BenchmarkUiError.FROZEN
                    is BenchmarkOutcome.NoLaunchActivityError -> _benchmarkError.value = BenchmarkUiError.NO_ACTIVITY
                    BenchmarkOutcome.PrivilegedUnavailable,
                    is BenchmarkOutcome.ExecutionError -> _benchmarkError.value = BenchmarkUiError.FAILED
                }
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun clearBenchmarkScore(packageName: String) {
        _benchmarkError.value = null
        uiSettings.updateArtBenchmarkRecord(packageName) { null }
    }
}
