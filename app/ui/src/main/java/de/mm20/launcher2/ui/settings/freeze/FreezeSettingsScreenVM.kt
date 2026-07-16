package de.mm20.launcher2.ui.settings.freeze

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
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FreezeSettingsScreenVM : ViewModel(), KoinComponent {
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

    private val _hasPermission = MutableStateFlow<Boolean?>(null)
    val hasPermission = _hasPermission.asStateFlow()

    val allApps = appRepository.findMany().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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

    fun setAppFreezeState(app: Application, state: AppFreezeState) {
        val packageName = app.componentName.packageName
        freezeManager.setAutoFreezeCandidate(packageName, state == AppFreezeState.Candidate)
        freezeSettings.setNeverFreeze(packageName, state == AppFreezeState.NeverFreeze)
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
