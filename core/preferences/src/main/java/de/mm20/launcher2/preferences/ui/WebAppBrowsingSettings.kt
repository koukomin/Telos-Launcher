package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Browsing behavior for the embedded WebView renderer used by web app shortcuts (not Custom
 * Tabs, which defers to the chosen browser's own settings for all of this).
 */
class WebAppBrowsingSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val adBlockEnabled
        get() = dataStore.data.map { it.webAppBrowsing.webAppAdBlockEnabled }.distinctUntilChanged()

    fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppAdBlockEnabled = enabled)) }
    }

    val trackingParamStrippingEnabled
        get() = dataStore.data.map { it.webAppBrowsing.webAppTrackingParamStrippingEnabled }.distinctUntilChanged()

    fun setTrackingParamStrippingEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppTrackingParamStrippingEnabled = enabled)) }
    }

    val zoomControlsEnabled
        get() = dataStore.data.map { it.webAppBrowsing.webAppZoomControlsEnabled }.distinctUntilChanged()

    fun setZoomControlsEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppZoomControlsEnabled = enabled)) }
    }

    /** Whether the top bar (back/forward, menu) sits at the bottom of the screen instead of the top. */
    val topBarAtBottom
        get() = dataStore.data.map { it.webAppBrowsing.webAppTopBarAtBottom }.distinctUntilChanged()

    fun setTopBarAtBottom(atBottom: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppTopBarAtBottom = atBottom)) }
    }

    /** Whether swiping on the top bar switches between the user's other web app shortcuts. */
    val swipeToSwitchEnabled
        get() = dataStore.data.map { it.webAppBrowsing.webAppSwipeToSwitchEnabled }.distinctUntilChanged()

    fun setSwipeToSwitchEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppSwipeToSwitchEnabled = enabled)) }
    }

    val groupsEnabled
        get() = dataStore.data.map { it.webAppBrowsing.webAppGroupsEnabled }.distinctUntilChanged()

    fun setGroupsEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppGroupsEnabled = enabled)) }
    }

    val groups
        get() = dataStore.data.map { it.webAppBrowsing.webAppGroups }.distinctUntilChanged()

    fun setGroups(groups: List<de.mm20.launcher2.preferences.WebAppGroup>) {
        dataStore.update { it.copy(webAppBrowsing = it.webAppBrowsing.copy(webAppGroups = groups)) }
    }
}
