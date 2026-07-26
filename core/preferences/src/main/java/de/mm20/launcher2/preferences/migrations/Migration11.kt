package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.LauncherSettingsData
import de.mm20.launcher2.preferences.SidebarPanelConfig

internal class Migration11 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {}

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 11
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        val newZones = currentData.floatingLauncher.floatingLauncherZones.mapValues { (_, config) ->
            if (config.panels.isEmpty() && (config.apps.isNotEmpty() || config.folders.isNotEmpty())) {
                config.copy(
                    panels = listOf(
                        SidebarPanelConfig.AppGrid(
                            apps = config.apps,
                            folders = config.folders
                        )
                    )
                )
            } else {
                config
            }
        }
        
        return currentData.copy(
            schemaVersion = 11,
            floatingLauncher = currentData.floatingLauncher.copy(
                floatingLauncherZones = newZones
            )
        )
    }
}
