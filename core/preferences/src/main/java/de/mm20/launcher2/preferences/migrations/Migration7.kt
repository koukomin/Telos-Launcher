package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.LauncherSettingsData

class Migration7 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 7
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 7
        )
    }
}
