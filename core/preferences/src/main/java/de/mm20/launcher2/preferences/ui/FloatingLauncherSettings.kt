package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.FloatingLauncherFolder
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.preferences.SidebarPanelConfig
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * One UI Edge Panel style overlay: up to six small tabs, one per zone (each screen edge split
 * into thirds), independently toggleable, each expanding into its own quick app launcher, drawn
 * over other apps via SYSTEM_ALERT_WINDOW. Opt-in, off by default; only RightTop is enabled by
 * default among the six zones once turned on.
 */
class FloatingLauncherSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val enabled
        get() = dataStore.data.map { it.floatingLauncherEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherEnabled = enabled) }
    }

    val zones
        get() = dataStore.data.map { it.floatingLauncherZones }.distinctUntilChanged()

    fun setZoneEnabled(zone: FloatingLauncherZone, enabled: Boolean) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(enabled = enabled)))
        }
    }

    fun setZoneApps(zone: FloatingLauncherZone, apps: List<String>) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: SidebarPanelConfig.AppGrid()
            val newPanels = current.panels.toMutableList().apply {
                if (current.activePanelIndex in indices) {
                    set(current.activePanelIndex, panel.copy(apps = apps))
                } else {
                    add(panel.copy(apps = apps))
                }
            }
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(panels = newPanels)))
        }
    }

    /** Shared across all zones. */
    val columns
        get() = dataStore.data.map { it.floatingLauncherColumns }.distinctUntilChanged()

    fun setColumns(columns: Int) {
        dataStore.update { it.copy(floatingLauncherColumns = columns.coerceIn(1, 2)) }
    }

    /** Caps how many rows the panel shows (per column) before it scrolls. */
    val maxPerColumn
        get() = dataStore.data.map { it.floatingLauncherMaxPerColumn }.distinctUntilChanged()

    fun setMaxPerColumn(maxPerColumn: Int) {
        dataStore.update { it.copy(floatingLauncherMaxPerColumn = maxPerColumn.coerceIn(3, 20)) }
    }

    /** Width in dp of each collapsed tab. */
    val thickness
        get() = dataStore.data.map { it.floatingLauncherThickness }.distinctUntilChanged()

    fun setThickness(thickness: Int) {
        dataStore.update { it.copy(floatingLauncherThickness = thickness) }
    }

    val color
        get() = dataStore.data.map { it.floatingLauncherColor }.distinctUntilChanged()

    fun setColor(color: Int) {
        dataStore.update { it.copy(floatingLauncherColor = color) }
    }

    /** Opacity of each collapsed tab, [0f, 1f]. */
    val alpha
        get() = dataStore.data.map { it.floatingLauncherAlpha }.distinctUntilChanged()

    fun setAlpha(alpha: Float) {
        dataStore.update { it.copy(floatingLauncherAlpha = alpha.coerceIn(0.1f, 1f)) }
    }

    /** When true, tabs are fully invisible (alpha forced to 0 at render time) but still
     * tappable - for anyone who wants the trigger without a visible on-screen element. */
    val hideIndicator
        get() = dataStore.data.map { it.floatingLauncherHideIndicator }.distinctUntilChanged()

    fun setHideIndicator(hide: Boolean) {
        dataStore.update { it.copy(floatingLauncherHideIndicator = hide) }
    }

    val hapticFeedback
        get() = dataStore.data.map { it.floatingLauncherHapticFeedback }.distinctUntilChanged()

    fun setHapticFeedback(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherHapticFeedback = enabled) }
    }

    /** Hide every tab while the Gaming context profile is active. */
    val autoHideGaming
        get() = dataStore.data.map { it.floatingLauncherAutoHideGaming }.distinctUntilChanged()

    fun setAutoHideGaming(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncherAutoHideGaming = enabled) }
    }

    /**
     * Bundles [appKeys] (which must currently be loose top-level entries in [zone]'s app list)
     * into a new folder named [name], replacing the first of them in the list with the folder's
     * sentinel entry so its position is preserved.
     */
    fun createFolder(zone: FloatingLauncherZone, name: String, appKeys: List<String>) {
        if (appKeys.isEmpty()) return
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: SidebarPanelConfig.AppGrid()
            
            val folder = FloatingLauncherFolder(id = UUID.randomUUID().toString(), name = name, appKeys = appKeys)
            val insertAt = panel.apps.indexOf(appKeys.first()).coerceAtLeast(0)
            val newApps = panel.apps.toMutableList()
            newApps.removeAll(appKeys)
            newApps.add(insertAt.coerceAtMost(newApps.size), FloatingLauncherFolder.sentinelKey(folder.id))
            
            val newPanel = panel.copy(apps = newApps, folders = panel.folders + folder)
            val newPanels = current.panels.toMutableList().apply {
                if (current.activePanelIndex in indices) set(current.activePanelIndex, newPanel)
                else add(newPanel)
            }
            
            it.copy(
                floatingLauncherZones = it.floatingLauncherZones + (
                    zone to current.copy(panels = newPanels)
                ),
            )
        }
    }

    fun renameFolder(zone: FloatingLauncherZone, folderId: String, name: String) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: return@update it
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: return@update it
                
            val newFolders = panel.folders.map { folder ->
                if (folder.id == folderId) folder.copy(name = name) else folder
            }
            val newPanel = panel.copy(folders = newFolders)
            val newPanels = current.panels.toMutableList().apply {
                set(current.activePanelIndex, newPanel)
            }
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(panels = newPanels)))
        }
    }

    /** Deletes the folder, returning its apps to the top level at the folder's old position. */
    fun deleteFolder(zone: FloatingLauncherZone, folderId: String) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: return@update it
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: return@update it
                
            val folder = panel.folders.firstOrNull { it.id == folderId } ?: return@update it
            val sentinel = FloatingLauncherFolder.sentinelKey(folderId)
            val insertAt = panel.apps.indexOf(sentinel).coerceAtLeast(0)
            val newApps = panel.apps.toMutableList()
            newApps.remove(sentinel)
            newApps.addAll(insertAt.coerceAtMost(newApps.size), folder.appKeys)
            
            val newPanel = panel.copy(apps = newApps, folders = panel.folders - folder)
            val newPanels = current.panels.toMutableList().apply {
                set(current.activePanelIndex, newPanel)
            }
            it.copy(
                floatingLauncherZones = it.floatingLauncherZones + (
                    zone to current.copy(panels = newPanels)
                ),
            )
        }
    }

    fun setActivePanel(zone: FloatingLauncherZone, index: Int) {
        dataStore.update {
            val current = it.floatingLauncherZones[zone] ?: return@update it
            it.copy(floatingLauncherZones = it.floatingLauncherZones + (zone to current.copy(activePanelIndex = index)))
        }
    }
}
