package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.MeasurementSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val data
        get() = launcherDataStore.data.map {
            LocationSearchSettingsData(
                providers = it.locationSearch.locationSearchProviders,
                searchRadius = it.locationSearch.locationSearchRadius,
                hideUncategorized = it.locationSearch.locationSearchHideUncategorized,
                overpassUrl = it.locationSearch.locationSearchOverpassUrl,
                tileServer = it.locationSearch.locationSearchTileServer,
                measurementSystem = it.locale.localeMeasurementSystem,
                showMap = it.locationSearch.locationSearchShowMap,
                showPositionOnMap = it.locationSearch.locationSearchShowPositionOnMap,
                themeMap = it.locationSearch.locationSearchThemeMap,
            )
        }

    val enabledProviders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchProviders }

    val osmLocations
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchProviders.contains("openstreetmaps") }

    fun setOsmLocations(osmLocations: Boolean) {
        launcherDataStore.update {
            if (osmLocations) {
                it.copy(locationSearch = it.locationSearch.copy(locationSearchProviders = it.locationSearch.locationSearchProviders + "openstreetmaps"))
            } else {
                it.copy(locationSearch = it.locationSearch.copy(locationSearchProviders = it.locationSearch.locationSearchProviders - "openstreetmaps"))
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchProviders - "openstreetmaps" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        launcherDataStore.update {
            if (enabled) {
                it.copy(locationSearch = it.locationSearch.copy(locationSearchProviders = it.locationSearch.locationSearchProviders + authority))
            } else {
                it.copy(locationSearch = it.locationSearch.copy(locationSearchProviders = it.locationSearch.locationSearchProviders - authority))
            }
        }
    }

    val searchRadius
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchRadius }

    fun setSearchRadius(searchRadius: Int) {
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchRadius = searchRadius))
        }
    }

    val hideUncategorized
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchHideUncategorized }

    fun setHideUncategorized(hideUncategorized: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchHideUncategorized = hideUncategorized))
        }
    }

    val overpassUrl
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchOverpassUrl }

    fun setOverpassUrl(overpassUrl: String?) {
        var url = overpassUrl
        if (url.isNullOrBlank()) {
            url = DefaultOverpassUrl
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (url.endsWith('/')) {
                url = url.substringBeforeLast('/')
            }
            if (url.endsWith("/api/interpreter")) {
                url = url.substringBeforeLast("/api/interpreter")
            }
        }
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchOverpassUrl = url))
        }
    }

    val tileServer
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchTileServer }

    fun setTileServer(tileServer: String?) {
        var url = tileServer
        if (url.isNullOrBlank()) {
            url = DefaultTileServerUrl
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (!url.contains("\${z}") || !url.contains("\${x}") || !url.contains("\${y}")) {
                url = "$url/\${z}/\${x}/\${y}.png"
            }
        }
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchTileServer = url))
        }
    }

    val showMap
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchShowMap }

    fun setShowMap(showMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchShowMap = showMap))
        }
    }

    val themeMap
        get() = launcherDataStore.data.map { it.locationSearch.locationSearchThemeMap }

    fun setThemeMap(themeMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearch = it.locationSearch.copy(locationSearchThemeMap = themeMap))
        }
    }

    val measurementSystem
        get() = launcherDataStore.data.map { it.locale.localeMeasurementSystem }

    companion object {
        const val DefaultTileServerUrl = "https://tile.openstreetmap.org/\${z}/\${x}/\${y}.png"
        const val DefaultOverpassUrl = "https://overpass-api.de"
    }

}

data class LocationSearchSettingsData(
    val providers: Set<String> = setOf("openstreetmaps"),
    val searchRadius: Int = 1500,
    val hideUncategorized: Boolean = true,
    val overpassUrl: String? = null,
    val tileServer: String? = null,
    val measurementSystem: MeasurementSystem = MeasurementSystem.System,
    val showMap: Boolean = false,
    val showPositionOnMap: Boolean = false,
    val themeMap: Boolean = true,
)