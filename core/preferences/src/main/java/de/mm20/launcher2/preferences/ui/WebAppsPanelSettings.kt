package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The ordered list of WebAppShortcut keys pinned to the Web Apps Panel. Whether the panel is
 * reachable at all, and from which direction, is not tracked here - it's derived from whichever
 * gesture slot (if any) has [de.mm20.launcher2.preferences.GestureAction.WebAppsPanel] assigned,
 * via the existing [GestureSettings].
 */
class WebAppsPanelSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val items
        get() = dataStore.data.map { it.webAppsPanel.webAppsPanelItems }.distinctUntilChanged()

    fun setItems(items: List<String>) {
        dataStore.update { it.copy(webAppsPanel = it.webAppsPanel.copy(webAppsPanelItems = items)) }
    }

    fun addItem(key: String) {
        dataStore.update {
            if (it.webAppsPanel.webAppsPanelItems.contains(key)) it
            else it.copy(webAppsPanel = it.webAppsPanel.copy(webAppsPanelItems = it.webAppsPanel.webAppsPanelItems + key))
        }
    }

    fun removeItem(key: String) {
        dataStore.update {
            it.copy(webAppsPanel = it.webAppsPanel.copy(webAppsPanelItems = it.webAppsPanel.webAppsPanelItems - key))
        }
    }
}
