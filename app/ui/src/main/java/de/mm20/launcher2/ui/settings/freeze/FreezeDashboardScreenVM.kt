package de.mm20.launcher2.ui.settings.freeze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

class FreezeDashboardScreenVM : ViewModel(), KoinComponent {
    private val freezeManager: FreezeManager by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    private val allApps = appRepository.findMany()
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

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}
