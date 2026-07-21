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
        get() = dataStore.data.map { it.webAppAdBlockEnabled }.distinctUntilChanged()

    fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppAdBlockEnabled = enabled) }
    }

    val trackingParamStrippingEnabled
        get() = dataStore.data.map { it.webAppTrackingParamStrippingEnabled }.distinctUntilChanged()

    fun setTrackingParamStrippingEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppTrackingParamStrippingEnabled = enabled) }
    }

    val zoomControlsEnabled
        get() = dataStore.data.map { it.webAppZoomControlsEnabled }.distinctUntilChanged()

    fun setZoomControlsEnabled(enabled: Boolean) {
        dataStore.update { it.copy(webAppZoomControlsEnabled = enabled) }
    }
}
