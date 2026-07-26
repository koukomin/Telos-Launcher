package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FavoritesSettingsData(
    val columns: Int,
    val frequentlyUsed: Boolean,
    val frequentlyUsedRows: Int,
)

class FavoritesSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<FavoritesSettingsData> by (dataStore.data.map {
    FavoritesSettingsData(
        columns = it.grid.gridColumnCount,
        frequentlyUsed = it.favorites.favoritesFrequentlyUsed,
        frequentlyUsedRows = it.favorites.favoritesFrequentlyUsedRows,
    )
}.distinctUntilChanged()) {

    val showEditButton
        get() = dataStore.data.map { it.favorites.favoritesEditButton }.distinctUntilChanged()

    fun setShowEditButton(showEditButton: Boolean) {
        dataStore.update { it.copy(favorites = it.favorites.copy(favoritesEditButton = showEditButton)) }
    }

    val frequentlyUsed: Flow<Boolean>
        get() = dataStore.data.map { it.favorites.favoritesFrequentlyUsed }.distinctUntilChanged()

    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        dataStore.update { it.copy(favorites = it.favorites.copy(favoritesFrequentlyUsed = frequentlyUsed)) }
    }

    val frequentlyUsedRows: Flow<Int>
        get() = dataStore.data.map { it.favorites.favoritesFrequentlyUsedRows }.distinctUntilChanged()

    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        dataStore.update { it.copy(favorites = it.favorites.copy(favoritesFrequentlyUsedRows = frequentlyUsedRows)) }
    }

    val compactTags: Flow<Boolean>
        get() = dataStore.data.map { it.favorites.favoritesCompactTags }.distinctUntilChanged()

    fun setCompactTags(compactTags: Boolean) {
        dataStore.update { it.copy(favorites = it.favorites.copy(favoritesCompactTags = compactTags)) }
    }
}