package de.mm20.launcher2.ui.desktopmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator

internal class DesktopStartMenuVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    val apps = appRepository.findMany()
        .map { apps ->
            val collator = Collator.getInstance()
            apps.sortedWith(compareBy(collator) { it.labelOverride ?: it.label })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getIcon(app: Application, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(app, size)
    }
}
