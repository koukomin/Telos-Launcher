package de.mm20.launcher2.ui.settings.floating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.search.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FloatingLauncherZoneAppsScreenVM : ViewModel(), KoinComponent {
    private val floatingLauncherSettings: FloatingLauncherSettings by inject()
    private val appRepository: AppRepository by inject()
    private val freezeManager: FreezeManager by inject()
    private val iconService: IconService by inject()

    private val zone = MutableStateFlow<FloatingLauncherZone?>(null)

    fun init(zone: FloatingLauncherZone) {
        this.zone.value = zone
    }

    val apps = appRepository.findMany()
        .map { apps -> apps.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedKeys = zone.combine(floatingLauncherSettings.zones) { zone, zones ->
        if (zone == null) emptySet() else (zones[zone]?.apps ?: emptyList()).toSet()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun toggleApp(key: String) {
        val zone = zone.value ?: return
        val current = selectedKeys.value
        val newApps = if (key in current) current - key else current + key
        floatingLauncherSettings.setZoneApps(zone, newApps.toList())
    }

    fun isFrozen(app: Application): Boolean {
        return freezeManager.isFrozen(app.componentName.packageName)
    }

    fun getIcon(app: Application, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(app, size)
    }
}
