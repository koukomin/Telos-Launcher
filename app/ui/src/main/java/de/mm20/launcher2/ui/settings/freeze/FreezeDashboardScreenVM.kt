package de.mm20.launcher2.ui.settings.freeze

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.AppFreezeState
import de.mm20.launcher2.freeze.AppUsageStatsProvider
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class FreezeStatsRow(
    val app: Application,
    val isFrozen: Boolean,
    val freezeCount: Int,
    val unfreezeCount: Int,
    val lastFrozenAt: Long?,
    val lastUnfrozenAt: Long?,
)

data class AppRuntimeRow(
    val app: Application,
    val state: AppFreezeState,
    val totalTimeMs: Long,
    val isRunning: Boolean,
)

class FreezeDashboardScreenVM : ViewModel(), KoinComponent {
    private val freezeManager: FreezeManager by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()
    private val usageStatsProvider: AppUsageStatsProvider by inject()
    private val permissionsManager: PermissionsManager by inject()

    // This dashboard is read-only (just reporting state, not changing what gets frozen), so
    // unlike FreezeSettingsScreen's app list there's no risk in always including iconless
    // framework/system components here - no extra toggle needed to see them.
    private val iconlessApps = MutableStateFlow<List<Application>>(emptyList())

    init {
        viewModelScope.launch {
            iconlessApps.value = appRepository.findIconlessApps()
        }
    }

    private val allApps = combine(
        appRepository.findMany(), iconlessApps,
    ) { apps, iconless -> apps + iconless }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), persistentListOf())

    val rows = combine(freezeManager.stats, allApps) { stats, apps ->
        withContext(Dispatchers.Default) {
            stats.mapNotNull { (packageName, appStats) ->
                val app = apps.firstOrNull { it.componentName.packageName == packageName }
                    ?: return@mapNotNull null
                FreezeStatsRow(
                    app = app,
                    isFrozen = freezeManager.isFrozen(packageName),
                    freezeCount = appStats.freezeCount,
                    unfreezeCount = appStats.unfreezeCount,
                    lastFrozenAt = appStats.lastFrozenAt,
                    lastUnfrozenAt = appStats.lastUnfrozenAt,
                )
            }.sortedByDescending { maxOf(it.lastFrozenAt ?: 0L, it.lastUnfrozenAt ?: 0L) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val totalFreezes = rows.map { it.sumOf { row -> row.freezeCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val totalUnfreezes = rows.map { it.sumOf { row -> row.unfreezeCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    // --- App status (normal/suspended/disabled, runtime, currently running) ---

    val hasUsageAccess = permissionsManager.hasPermission(PermissionGroup.UsageAccess)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun requestUsageAccess(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.UsageAccess)
    }

    private val _selectedState = MutableStateFlow(AppFreezeState.Suspended)
    val selectedState: StateFlow<AppFreezeState> = _selectedState.asStateFlow()

    fun selectState(state: AppFreezeState) {
        _selectedState.value = state
    }

    // Usage stats aren't observed reactively (there's no Flow for "app runtime changed") - this
    // bumps to force a recompute instead, called on screen resume and via a manual refresh
    // action. No continuous polling: that would work against the point of a battery-focused
    // feature, for a number that only matters while this screen is actually open.
    private val refreshTrigger = MutableStateFlow(0)
    fun refresh() {
        refreshTrigger.value++
    }

    val runtimeRows: StateFlow<List<AppRuntimeRow>> = combine(allApps, refreshTrigger) { apps, _ -> apps }
        .map { apps ->
            withContext(Dispatchers.Default) {
                val totalTimes = usageStatsProvider.totalTimeInForeground()
                val foregroundPackage = usageStatsProvider.currentForegroundPackage()
                apps.map { app ->
                    val packageName = app.componentName.packageName
                    AppRuntimeRow(
                        app = app,
                        state = freezeManager.freezeState(packageName),
                        totalTimeMs = totalTimes[packageName] ?: 0L,
                        isRunning = packageName == foregroundPackage,
                    )
                }.sortedByDescending { it.totalTimeMs }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
}
