package de.mm20.launcher2.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.preferences.migrations.Migration2
import de.mm20.launcher2.preferences.migrations.Migration3
import de.mm20.launcher2.preferences.migrations.Migration4
import de.mm20.launcher2.preferences.migrations.Migration5
import de.mm20.launcher2.preferences.migrations.Migration6
import de.mm20.launcher2.preferences.migrations.Migration7
import de.mm20.launcher2.preferences.migrations.Migration8
import de.mm20.launcher2.preferences.migrations.Migration9
import de.mm20.launcher2.preferences.migrations.Migration10
import de.mm20.launcher2.settings.BaseSettings
import java.io.File

internal class LauncherDataStore(
    private val context: Context,
): BaseSettings<LauncherSettingsData>(
    context,
    fileName = "settings.json",
    serializer = LauncherSettingsDataSerializer(context),
    migrations = listOf(
        Migration2(),
        Migration3(),
        Migration4(),
        Migration5(),
        Migration6(),
        Migration7(),
        Migration8(),
        Migration9(),
        Migration10(),
    ),
    // Last line of defense: if the file genuinely can't be read despite the serializer's
    // unknown-value tolerance, snapshot it before DataStore overwrites it with the replacement.
    // Losing every setting is bad enough; losing them UNRECOVERABLY (as the previous plain
    // ReplaceFileCorruptionHandler did) must never happen - the snapshot keeps the data around
    // for a fixed build (or a manual restore) to recover.
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e("MM20", "settings.json is unreadable, resetting to defaults", exception)
        try {
            val file = File(context.filesDir, "datastore/settings.json")
            if (file.exists()) {
                file.copyTo(File(context.filesDir, "datastore/settings.json.corrupt"), overwrite = true)
            }
        } catch (e: Exception) {
            Log.e("MM20", "Could not back up corrupt settings.json", e)
        }
        LauncherSettingsData()
    }
) {

    val data
        get() = context.dataStore.data

    fun update(block: (LauncherSettingsData) -> LauncherSettingsData) {
        updateData(block)
    }
}
