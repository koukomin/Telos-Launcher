package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SearchUiSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val launchOnEnter
        get() = launcherDataStore.data.map { it.searchBar.searchLaunchOnEnter }.distinctUntilChanged()

    fun setLaunchOnEnter(launchOnEnter: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchLaunchOnEnter = launchOnEnter))
        }
    }

    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
    val moveFrozenAppsToEnd
        get() = launcherDataStore.data.map { it.appSearch.moveFrozenAppsToEnd }.distinctUntilChanged()

    fun setMoveFrozenAppsToEnd(move: Boolean) {
        launcherDataStore.update {
            it.copy(appSearch = it.appSearch.copy(moveFrozenAppsToEnd = move))
        }
    }
    // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===

    val hiddenItemsButton
        get() = launcherDataStore.data.map { it.searchResults.hiddenItemsShowButton }.distinctUntilChanged()

    fun setHiddenItemsButton(hiddenItemsButton: Boolean) {
        launcherDataStore.update {
            it.copy(searchResults = it.searchResults.copy(hiddenItemsShowButton = hiddenItemsButton))
        }
    }

    val favorites
        get() = launcherDataStore.data.map { it.favorites.favoritesEnabled }.distinctUntilChanged()

    fun setFavorites(favorites: Boolean) {
        launcherDataStore.update {
            it.copy(favorites = it.favorites.copy(favoritesEnabled = favorites))
        }
    }

    val allApps
        get() = launcherDataStore.data.map { it.appSearch.searchAllApps }.distinctUntilChanged()

    fun setAllApps(allAppsGrid: Boolean) {
        launcherDataStore.update {
            it.copy(appSearch = it.appSearch.copy(searchAllApps = allAppsGrid))
        }
    }

    val openKeyboard
        get() = launcherDataStore.data.map { it.searchBar.searchBarKeyboard }.distinctUntilChanged()

    fun setOpenKeyboard(openKeyboard: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(searchBarKeyboard = openKeyboard))
        }
    }

    val privateKeyboard
        get() = launcherDataStore.data.map { it.searchBar.privateKeyboard }.distinctUntilChanged()

    fun setPrivateKeyboard(privateKeyboard: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(privateKeyboard = privateKeyboard))
        }
    }

    val showAppRecommendations
        get() = launcherDataStore.data.map { it.searchBar.showAppRecommendations }.distinctUntilChanged()

    fun setShowAppRecommendations(show: Boolean) {
        launcherDataStore.update {
            it.copy(searchBar = it.searchBar.copy(showAppRecommendations = show))
        }
    }

    val reversedResults
        get() = launcherDataStore.data.map { it.searchResults.searchResultsReversed }.distinctUntilChanged()

    fun setReversedResults(reversedResults: Boolean) {
        launcherDataStore.update {
            it.copy(searchResults = it.searchResults.copy(searchResultsReversed = reversedResults))
        }
    }

    val separateWorkProfile
        get() = launcherDataStore.data.map { it.searchResults.separateWorkProfile }.distinctUntilChanged()

    fun setSeparateWorkProfile(separateWorkProfile: Boolean) {
        launcherDataStore.update {
            it.copy(searchResults = it.searchResults.copy(separateWorkProfile = separateWorkProfile))
        }
    }

    val showAppDetails
        get() = launcherDataStore.data.map { it.appSearch.appsShowDetails }.distinctUntilChanged()

    fun setShowAppDetails(showAppDetails: Boolean) {
        launcherDataStore.update {
            it.copy(appSearch = it.appSearch.copy(appsShowDetails = showAppDetails))
        }
    }

}
