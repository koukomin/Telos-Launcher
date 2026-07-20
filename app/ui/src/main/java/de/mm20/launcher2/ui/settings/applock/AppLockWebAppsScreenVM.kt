package de.mm20.launcher2.ui.settings.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppLockWebAppsScreenVM : ViewModel(), KoinComponent {
    private val appLockSettings: AppLockSettings by inject()
    private val webAppShortcutRepository: WebAppShortcutRepository by inject()
    private val iconService: IconService by inject()

    val shortcuts = webAppShortcutRepository.search("", false)
        .map { shortcuts -> withContext(Dispatchers.Default) { shortcuts.sortedBy { it.label.lowercase() } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val lockedShortcuts = appLockSettings.lockedWebAppShortcuts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    fun setLocked(key: String, locked: Boolean) =
        appLockSettings.setWebAppShortcutLocked(key, locked)

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}
