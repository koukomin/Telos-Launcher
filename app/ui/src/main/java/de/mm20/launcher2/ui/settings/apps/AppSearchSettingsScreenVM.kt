package de.mm20.launcher2.ui.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.asPreferenceState
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppSearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchUiSettings: SearchUiSettings by inject()
    private val uiSettings: UiSettings by inject()

    val allApps = searchUiSettings.allApps.asPreferenceState(viewModelScope, null)
    val showAppDetails = searchUiSettings.showAppDetails.asPreferenceState(viewModelScope, null)
    val moveFrozenAppsToEnd = searchUiSettings.moveFrozenAppsToEnd.asPreferenceState(viewModelScope, null)
    val drawerStyle = searchUiSettings.drawerStyle.asPreferenceState(viewModelScope, de.mm20.launcher2.preferences.DrawerStyle.Classic)

    val showList = uiSettings.gridSettings.map { it.showList }.asPreferenceState(viewModelScope, null)
    val showListIcons = uiSettings.gridSettings.map { it.showListIcons }.asPreferenceState(viewModelScope, null)

    fun setAllApps(allApps: Boolean) = searchUiSettings.setAllApps(allApps)
    fun setHideAppDetails(hideAppDetails: Boolean) = searchUiSettings.setShowAppDetails(hideAppDetails)
    fun setShowList(showList: Boolean) = uiSettings.setGridShowList(showList)
    fun setShowListIcons(showIcons: Boolean) = uiSettings.setGridShowListIcons(showIcons)
    fun setMoveFrozenAppsToEnd(move: Boolean) = searchUiSettings.setMoveFrozenAppsToEnd(move)
    fun setDrawerStyle(style: de.mm20.launcher2.preferences.DrawerStyle) = searchUiSettings.setDrawerStyle(style)
}
