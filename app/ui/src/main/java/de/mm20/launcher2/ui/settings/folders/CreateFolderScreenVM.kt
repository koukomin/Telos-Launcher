package de.mm20.launcher2.ui.settings.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.applications.FolderImpl
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.settings.homescreen.dock.DockSettingsScreenVM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class CreateFolderScreenVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()
    private val uiSettings: UiSettings by inject()

    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    val apps: Flow<List<Application>> = appRepository.findMany().map { appList ->
        appList.sortedBy { it.labelOverride ?: it.label }
    }
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===

    fun getIcon(app: Application, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(app, size)
    }

    /**
     * Creates a folder pre-populated with [selectedKeys] in one step - unlike the Dock's "Add
     * folder" menu, which always starts empty. Drops it into the first empty dock slot,
     * extending the dock with a new page if every existing slot is already taken.
     */
    fun createFolder(name: String, selectedKeys: Set<String>) {
        viewModelScope.launch {
            val folder = FolderImpl(
                id = UUID.randomUUID().toString(),
                label = name,
                itemKeys = selectedKeys.toList(),
                isCover = true,
            )
            // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
            searchableRepository.upsert(
                folder,
                visibility = VisibilityLevel.Default,
                pinned = true
            )
            // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===

            val dockPages = uiSettings.dockPages.first().toMutableList()
            // Auto dock (no custom pages): the folder is pinned, so it already shows up among the
            // favorites the auto dock displays. Do not turn the auto dock into a custom one here.
            if (dockPages.isEmpty()) return@launch
            val capacity = uiSettings.dockRows.first() * uiSettings.dockColumns.first()
            var placed = false
            for (page in dockPages.indices) {
                val items = dockPages[page].toMutableList()
                val emptyIndex = items.indexOfFirst {
                    it is DockItem.Searchable && it.key.isEmpty()
                }
                if (emptyIndex >= 0) {
                    items[emptyIndex] = DockItem.Searchable(folder.key)
                    dockPages[page] = items
                    placed = true
                    break
                } else if (items.size < capacity) {
                    // the stored page is sparse: slots past its end are empty
                    items.add(DockItem.Searchable(folder.key))
                    dockPages[page] = items
                    placed = true
                    break
                }
            }
            if (!placed) {
                // every slot is taken: extend the dock with a new page (up to the maximum);
                // otherwise leave the dock as it is, the folder stays reachable as a favorite
                if (dockPages.size >= DockSettingsScreenVM.MAX_DOCKS) return@launch
                dockPages.add(listOf(DockItem.Searchable(folder.key)))
            }
            uiSettings.setDockPages(dockPages)
        }
    }
}
