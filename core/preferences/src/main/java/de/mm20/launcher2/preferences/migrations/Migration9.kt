package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.LauncherSettingsData

/**
 * Moves the default weather provider from "metno" to "openmeteo". Safe to do unconditionally for
 * anyone still on "metno": MetNo requires a `metno_contact` string resource this build never
 * defines, so [de.mm20.launcher2.weather.metno.MetNoProvider.isAvailable] is always false here and
 * MetNo was never selectable in the provider list - a stored "metno" value can only be the
 * untouched schema default, never a deliberate user choice.
 */
class Migration9 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 9
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 9,
            weatherProvider = if (currentData.weatherProvider == "metno") "openmeteo" else currentData.weatherProvider,
        )
    }
}
