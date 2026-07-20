package de.mm20.launcher2.ui.settings.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppLockAppsScreenVM : ViewModel(), KoinComponent {
    private val appLockSettings: AppLockSettings by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    val apps = appRepository.findMany()
        .map { apps -> withContext(Dispatchers.Default) { apps.sortedBy { it.label.lowercase() } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val lockedPackages = appLockSettings.lockedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    fun setLocked(packageName: String, locked: Boolean) =
        appLockSettings.setLocked(packageName, locked)

    val gracePeriodOverrides = appLockSettings.gracePeriodOverrides
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    fun setGracePeriodOverride(packageName: String, ms: Long?) =
        appLockSettings.setGracePeriodOverride(packageName, ms)

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}
