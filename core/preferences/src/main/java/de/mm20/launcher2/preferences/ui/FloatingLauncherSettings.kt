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
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherEnabled = enabled)) }
    }

    val zones
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherZones }.distinctUntilChanged()

    fun setZoneEnabled(zone: FloatingLauncherZone, enabled: Boolean) {
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (zone to current.copy(enabled = enabled))))
        }
    }

    fun setZoneApps(zone: FloatingLauncherZone, apps: List<String>) {
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: SidebarPanelConfig.AppGrid()
            val newPanels = current.panels.toMutableList().apply {
                if (current.activePanelIndex in indices) {
                    set(current.activePanelIndex, panel.copy(apps = apps))
                } else {
                    add(panel.copy(apps = apps))
                }
            }
            it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (zone to current.copy(panels = newPanels))))
        }
    }

    /** Shared across all zones. */
    val columns
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherColumns }.distinctUntilChanged()

    fun setColumns(columns: Int) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherColumns = columns.coerceIn(1, 2))) }
    }

    /** Caps how many rows the panel shows (per column) before it scrolls. */
    val maxPerColumn
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherMaxPerColumn }.distinctUntilChanged()

    fun setMaxPerColumn(maxPerColumn: Int) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherMaxPerColumn = maxPerColumn.coerceIn(3, 20))) }
    }

    /** Width in dp of each collapsed tab. */
    val thickness
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherThickness }.distinctUntilChanged()

    fun setThickness(thickness: Int) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherThickness = thickness)) }
    }

    val color
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherColor }.distinctUntilChanged()

    fun setColor(color: Int) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherColor = color)) }
    }

    /** Opacity of each collapsed tab, [0f, 1f]. */
    val alpha
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherAlpha }.distinctUntilChanged()

    fun setAlpha(alpha: Float) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherAlpha = alpha.coerceIn(0.1f, 1f))) }
    }

    /** When true, tabs are fully invisible (alpha forced to 0 at render time) but still
     * tappable - for anyone who wants the trigger without a visible on-screen element. */
    val hideIndicator
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherHideIndicator }.distinctUntilChanged()

    fun setHideIndicator(hide: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherHideIndicator = hide)) }
    }

    val hapticFeedback
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherHapticFeedback }.distinctUntilChanged()

    fun setHapticFeedback(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherHapticFeedback = enabled)) }
    }

    val showLabels
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherShowLabels }.distinctUntilChanged()

    fun setShowLabels(show: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherShowLabels = show)) }
    }

    /** Opacity of the panel card, [0.3f, 1f]. */
    val panelAlpha
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherPanelAlpha }.distinctUntilChanged()

    fun setPanelAlpha(alpha: Float) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherPanelAlpha = alpha.coerceIn(0.3f, 1f))) }
    }

    /** Icon size in the panel, in dp. */
    val iconSize
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherIconSize }.distinctUntilChanged()

    fun setIconSize(size: Int) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherIconSize = size.coerceIn(32, 72))) }
    }

    val floatingWindows
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherFloatingWindows }.distinctUntilChanged()

    fun setFloatingWindows(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherFloatingWindows = enabled)) }
    }

    val tools
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherTools }.distinctUntilChanged()

    fun setTools(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherTools = enabled)) }
    }

    /** Hide every tab while the Gaming context profile is active. */
    val autoHideGaming
        get() = dataStore.data.map { it.floatingLauncher.floatingLauncherAutoHideGaming }.distinctUntilChanged()

    fun setAutoHideGaming(enabled: Boolean) {
        dataStore.update { it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherAutoHideGaming = enabled)) }
    }

    /**
     * Bundles [appKeys] (which must currently be loose top-level entries in [zone]'s app list)
     * into a new folder named [name], replacing the first of them in the list with the folder's
     * sentinel entry so its position is preserved.
     */
    fun createFolder(zone: FloatingLauncherZone, name: String, appKeys: List<String>) {
        if (appKeys.isEmpty()) return
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: FloatingLauncherZoneConfig()
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
                floatingLauncher = it.floatingLauncher.copy(
                    floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (
                        zone to current.copy(panels = newPanels)
                    ),
                ),
            )
        }
    }

    fun renameFolder(zone: FloatingLauncherZone, folderId: String, name: String) {
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: return@update it
            val panel = current.panels.getOrNull(current.activePanelIndex) as? SidebarPanelConfig.AppGrid
                ?: return@update it

            val newFolders = panel.folders.map { folder ->
                if (folder.id == folderId) folder.copy(name = name) else folder
            }
            val newPanel = panel.copy(folders = newFolders)
            val newPanels = current.panels.toMutableList().apply {
                set(current.activePanelIndex, newPanel)
            }
            it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (zone to current.copy(panels = newPanels))))
        }
    }

    /** Deletes the folder, returning its apps to the top level at the folder's old position. */
    fun deleteFolder(zone: FloatingLauncherZone, folderId: String) {
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: return@update it
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
                floatingLauncher = it.floatingLauncher.copy(
                    floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (
                        zone to current.copy(panels = newPanels)
                    ),
                ),
            )
        }
    }

    fun setActivePanel(zone: FloatingLauncherZone, index: Int) {
        dataStore.update {
            val current = it.floatingLauncher.floatingLauncherZones[zone] ?: return@update it
            it.copy(floatingLauncher = it.floatingLauncher.copy(floatingLauncherZones = it.floatingLauncher.floatingLauncherZones + (zone to current.copy(activePanelIndex = index))))
        }
    }
}
