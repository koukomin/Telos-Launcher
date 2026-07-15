package de.mm20.launcher2.themes.presets

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.themes.ThemeBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Bundled theme presets, shipped as `.kvtheme` JSON assets under `assets/theme_presets/`.
 * Adding a new preset only requires dropping a new file there — no repository/DB changes.
 */
class ThemePresetsRepository(
    private val context: Context,
) {
    suspend fun list(): List<ThemeBundle> = withContext(Dispatchers.IO) {
        val fileNames = try {
            context.assets.list(ASSET_DIR) ?: emptyArray()
        } catch (e: IOException) {
            CrashReporter.logException(e)
            emptyArray()
        }
        fileNames.mapNotNull { fileName ->
            try {
                val json = context.assets.open("$ASSET_DIR/$fileName").bufferedReader()
                    .use { it.readText() }
                ThemeBundle.fromJson(json)
            } catch (e: IOException) {
                CrashReporter.logException(e)
                null
            }
        }
    }

    companion object {
        private const val ASSET_DIR = "theme_presets"
    }
}
