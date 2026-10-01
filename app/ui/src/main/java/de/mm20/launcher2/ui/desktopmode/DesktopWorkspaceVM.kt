// === TELOS_PENDING_REVIEW_START: desktop_grid_and_context_menu ===
package de.mm20.launcher2.ui.desktopmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class DesktopWorkspaceVM : ViewModel(), KoinComponent {
    private val favoritesService: FavoritesService by inject()
    private val iconService: IconService by inject()

    // Using favorites list as the primary desktop pinned apps source for now
    val pinnedApps = favoritesService.getFavorites()
        .map { list -> list.filterIsInstance<Application>() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getIcon(app: Application, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(app, size)
    }

    fun unpin(app: Application) {
        favoritesService.unpinItem(app)
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_grid_and_context_menu ===
