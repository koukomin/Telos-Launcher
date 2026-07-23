package de.mm20.launcher2.themes.colors

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.themes.AmoledPresetId
import de.mm20.launcher2.themes.BlackAndWhiteThemeId
import de.mm20.launcher2.themes.CyberpunkPresetId
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.HighContrastThemeId
import de.mm20.launcher2.themes.R
import de.mm20.launcher2.themes.presets.ThemePresetsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class ColorsRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val themePresetsRepository: ThemePresetsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun getAll(): Flow<List<Colors>> {
        return database.themeDao().getAllColors().map {
            getBuiltIn() + it.map { Colors(it) }
        }
    }

    fun get(id: UUID): Flow<Colors?> {
        if (id == DefaultThemeId) return flowOf(default)
        if (id == HighContrastThemeId) return flowOf(highContrast)
        if (id == BlackAndWhiteThemeId) return flowOf(blackAndWhite)
        if (id == AmoledPresetId || id == CyberpunkPresetId) {
            return flow { emit(getPresetColors().find { it.id == id }) }
        }
        return database.themeDao().getColors(id).map { it?.let { Colors(it) } }
            .flowOn(Dispatchers.Default)
    }

    /** Colors bundled in built-in theme presets (Cyberpunk, AMOLED, ...) - surfaced directly in
     * the Colors picker as built-in options, without requiring a visit to the Presets screen to
     * install them into the DB first. */
    private suspend fun getPresetColors(): List<Colors> {
        return themePresetsRepository.list().mapNotNull { it.colors?.copy(builtIn = true) }
    }

    fun create(colors: Colors) {
        scope.launch {
            database.themeDao().insertColors(colors.toEntity())
        }
    }

    fun update(colors: Colors) {
        scope.launch {
            database.themeDao().updateColors(colors.toEntity())
        }
    }


    fun delete(colors: Colors) {
        scope.launch {
            database.themeDao().deleteColors(colors.id)
        }
    }

    fun getOrDefault(id: UUID?): Flow<Colors> {
        if (id == null) return flowOf(default)
        return get(id).map { it ?: default }
    }

    private suspend fun getBuiltIn(): List<Colors> {
        return listOf(
            default,
            highContrast,
            blackAndWhite,
        ) + getPresetColors()
    }

    private val default: Colors
        get() = Colors(
            id = DefaultThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_default),
            corePalette = EmptyCorePalette,
            lightColorScheme = DefaultLightColorScheme,
            darkColorScheme = DefaultDarkColorScheme,
        )


    private val highContrast: Colors
        get() = Colors(
            id = HighContrastThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_high_contrast),
            corePalette = EmptyCorePalette,
            lightColorScheme = HighContrastLightColorScheme,
            darkColorScheme = HighContrastDarkColorScheme,
        )


    private val blackAndWhite: Colors
        get() = Colors(
            id = BlackAndWhiteThemeId,
            builtIn = true,
            name = context.getString(R.string.preference_colors_bw),
            corePalette = EmptyCorePalette,
            lightColorScheme = BlackAndWhiteLightColorScheme,
            darkColorScheme = BlackAndWhiteDarkColorScheme,
        )
}