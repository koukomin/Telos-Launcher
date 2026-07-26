package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SearchFilters
import kotlinx.coroutines.flow.map

class SearchFilterSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val defaultFilter
        get() = launcherDataStore.data.map { it.searchFilterGroup.searchFilter }

    fun setDefaultFilter(filter: SearchFilters) {
        launcherDataStore.update {
            it.copy(searchFilterGroup = it.searchFilterGroup.copy(searchFilter = filter))
        }
    }

    val filterBar
        get() = launcherDataStore.data.map { it.searchFilterGroup.searchFilterBar }

    fun setFilterBar(filterBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchFilterGroup = it.searchFilterGroup.copy(searchFilterBar = filterBar))
        }
    }

    val filterBarItems
        get() = launcherDataStore.data.map { it.searchFilterGroup.searchFilterBarItems.distinct() }

    fun setFilterBarItems(items: List<KeyboardFilterBarItem>) {
        launcherDataStore.update {
            it.copy(searchFilterGroup = it.searchFilterGroup.copy(searchFilterBarItems = items))
        }
    }
}