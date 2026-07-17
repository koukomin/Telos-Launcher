package de.mm20.launcher2.ui.settings.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.FreezeBackendType
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
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

class FreezeSettingsScreenVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val freezeManager: FreezeManager by inject()
    private val freezeSettings: FreezeSettings by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val usageAccessGranted = permissionsManager.hasPermission(PermissionGroup.UsageAccess)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun requestUsageAccess(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.UsageAccess)
    }

    val activeBackend: StateFlow<FreezeBackendType?> = freezeManager.activeBackend

    val backend = freezeSettings.backend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), FreezeBackendPreference.Auto)

    fun setBackend(backend: FreezeBackendPreference) {
        freezeSettings.setBackend(backend)
        refreshBackendState()
    }

    private val _hasPermission = MutableStateFlow<Boolean?>(null)
    val hasPermission = _hasPermission.asStateFlow()

    private val sortedApps = appRepository.findMany().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps = _showSystemApps.asStateFlow()

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    val allApps = combine(sortedApps, _showSystemApps) { apps, showSystem ->
        if (showSystem) apps
        else withContext(Dispatchers.Default) {
            apps.filterNot { isSystemApp(it.componentName.packageName) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
                .flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    val candidates = freezeSettings.candidates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val neverFreezeApps = freezeSettings.neverFreezeApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val profile = freezeSettings.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setProfile(profile: FreezeProfile) = freezeSettings.setProfile(profile)

    val exclusionStrictness = freezeSettings.exclusionStrictness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setExclusionStrictness(strictness: FreezeExclusionStrictness) =
        freezeSettings.setExclusionStrictness(strictness)

    val autoFreezeEnabled = freezeSettings.autoFreezeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val freezeOnScreenOff = freezeSettings.freezeOnScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val freezeOnIdle = freezeSettings.freezeOnIdle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val idleTimeoutMinutes = freezeSettings.idleTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val freezeOnBatterySaver = freezeSettings.freezeOnBatterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val excludeMusic = freezeSettings.excludeMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val excludeNetwork = freezeSettings.excludeNetwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val networkThresholdKb = freezeSettings.networkThresholdKb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val freezeMethods = freezeSettings.freezeMethods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    val advancedFeaturesEnabled = freezeSettings.advancedFeaturesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun refreshBackendState() {
        viewModelScope.launch {
            freezeManager.refreshBackendState()
            _hasPermission.value = freezeManager.hasPermission()
        }
    }

    fun requestPermission(activity: AppCompatActivity) {
        viewModelScope.launch {
            _hasPermission.value = freezeManager.requestPermission()
        }
    }

    fun setAutoFreezeEnabled(enabled: Boolean) = freezeSettings.setAutoFreezeEnabled(enabled)
    fun setFreezeOnScreenOff(enabled: Boolean) = freezeSettings.setFreezeOnScreenOff(enabled)
    fun setFreezeOnIdle(enabled: Boolean) = freezeSettings.setFreezeOnIdle(enabled)
    fun setIdleTimeoutMinutes(minutes: Int) = freezeSettings.setIdleTimeoutMinutes(minutes)
    fun setFreezeOnBatterySaver(enabled: Boolean) = freezeSettings.setFreezeOnBatterySaver(enabled)
    fun setExcludeMusic(enabled: Boolean) = freezeSettings.setExcludeMusic(enabled)
    fun setExcludeNetwork(enabled: Boolean) = freezeSettings.setExcludeNetwork(enabled)
    fun setNetworkThresholdKb(threshold: Int) = freezeSettings.setNetworkThresholdKb(threshold)
    fun setAdvancedFeaturesEnabled(enabled: Boolean) = freezeSettings.setAdvancedFeaturesEnabled(enabled)

    fun setAppFreezeState(app: Application, state: AppFreezeState, method: FreezeMethod? = null) {
        val packageName = app.componentName.packageName
        freezeManager.setAutoFreezeCandidate(packageName, state == AppFreezeState.Candidate)
        freezeSettings.setNeverFreeze(packageName, state == AppFreezeState.NeverFreeze)
        if (state == AppFreezeState.Candidate) {
            freezeSettings.setFreezeMethod(packageName, method)
        }
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}

enum class AppFreezeState { None, Candidate, NeverFreeze }

fun appFreezeState(packageName: String, candidates: Set<String>, neverFreeze: Set<String>): AppFreezeState {
    return when {
        neverFreeze.contains(packageName) -> AppFreezeState.NeverFreeze
        candidates.contains(packageName) -> AppFreezeState.Candidate
        else -> AppFreezeState.None
    }
}

fun appFreezeMethod(packageName: String, methods: Map<String, FreezeMethod>): FreezeMethod {
    return methods[packageName] ?: FreezeMethod.Suspend
}
