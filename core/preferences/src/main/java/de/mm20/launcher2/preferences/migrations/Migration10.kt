package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.preferences.FloatingLauncherZoneConfig
import de.mm20.launcher2.preferences.LauncherSettingsData

/**
 * Moves the floating quick launcher from a single tab (edge + free-form vertical position) to six
 * fixed zones (each edge split into thirds). If the user had it enabled, this maps their old
 * edge/position onto whichever new zone occupies roughly the same spot, so their existing setup
 * keeps working in the same place rather than resetting to the new default (RightTop only).
 */
@Suppress("DEPRECATION")
class Migration10 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 10
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        if (!currentData.floatingLauncher.floatingLauncherEnabled) {
            return currentData.copy(schemaVersion = 10)
        }
        val isLeft = currentData.floatingLauncher.floatingLauncherEdge == FloatingLauncherEdge.Left
        val zone = when {
            currentData.floatingLauncher.floatingLauncherPosition < 1f / 3f ->
                if (isLeft) FloatingLauncherZone.LeftTop else FloatingLauncherZone.RightTop

            currentData.floatingLauncher.floatingLauncherPosition > 2f / 3f ->
                if (isLeft) FloatingLauncherZone.LeftBottom else FloatingLauncherZone.RightBottom

            else -> if (isLeft) FloatingLauncherZone.LeftMiddle else FloatingLauncherZone.RightMiddle
        }
        return currentData.copy(
            schemaVersion = 10,
            floatingLauncher = currentData.floatingLauncher.copy(
                floatingLauncherZones = mapOf(zone to FloatingLauncherZoneConfig(enabled = true)),
            ),
        )
    }
}
