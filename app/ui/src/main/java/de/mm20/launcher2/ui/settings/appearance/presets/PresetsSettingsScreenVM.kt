package de.mm20.launcher2.ui.settings.appearance.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.LayoutPreset
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.presets.ThemePresetsRepository
import de.mm20.launcher2.themes.shapes.Shapes
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.themes.typography.Typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PresetsSettingsScreenVM : ViewModel(), KoinComponent {

    private val presetsRepository: ThemePresetsRepository by inject()
    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()
    private val performanceSettings: PerformanceSettings by inject()

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _presets = MutableStateFlow<List<ThemeBundle>>(emptyList())
    val presets = _presets.asStateFlow()

    init {
        viewModelScope.launch {
            _presets.value = presetsRepository.list()
        }
    }

    /**
     * Installs every part of [bundle] that's actually set - previously this only ever looked at
     * [ThemeBundle.colors], silently ignoring shapes/transparencies/typography/layout even though
     * a bundle could carry them.
     */
    fun install(bundle: ThemeBundle) {
        // The screen pops itself right after calling this, which clears the ViewModel and would
        // cancel viewModelScope mid-install. Run in a scope that outlives the screen.
        installScope.launch {
            bundle.colors?.let { installColors(it) }
            bundle.shapes?.let { installShapes(it) }
            bundle.transparencies?.let { installTransparencies(it) }
            bundle.typography?.let { installTypography(it) }
            bundle.layout?.let { installLayout(it) }
        }
    }

    private suspend fun installColors(colors: Colors) {
        if (themeRepository.colors.get(colors.id).first() != null) {
            themeRepository.colors.update(colors)
        } else {
            themeRepository.colors.create(colors)
        }
        uiSettings.setColorsId(colors.id)
    }

    private suspend fun installShapes(shapes: Shapes) {
        if (themeRepository.shapes.get(shapes.id).first() != null) {
            themeRepository.shapes.update(shapes)
        } else {
            themeRepository.shapes.create(shapes)
        }
        uiSettings.setShapesId(shapes.id)
    }

    private suspend fun installTransparencies(transparencies: Transparencies) {
        if (themeRepository.transparencies.get(transparencies.id).first() != null) {
            themeRepository.transparencies.update(transparencies)
        } else {
            themeRepository.transparencies.create(transparencies)
        }
        uiSettings.setTransparenciesId(transparencies.id)
    }

    private suspend fun installTypography(typography: Typography) {
        if (themeRepository.typographies.get(typography.id).first() != null) {
            themeRepository.typographies.update(typography)
        } else {
            themeRepository.typographies.create(typography)
        }
        uiSettings.setTypographyId(typography.id)
    }

    private fun installLayout(layout: LayoutPreset) {
        layout.gridColumnCount?.let { uiSettings.setGridColumnCount(it) }
        layout.gridIconSize?.let { uiSettings.setGridIconSize(it) }
        layout.gridShowLabels?.let { uiSettings.setGridShowLabels(it) }
        layout.iconShape?.let { uiSettings.setIconShape(it) }
        layout.dock?.let { uiSettings.setDock(it) }
        layout.dockRows?.let { uiSettings.setDockRows(it) }
        layout.bottomSearchBar?.let { uiSettings.setBottomSearchBar(it) }
        layout.searchBarStyle?.let { uiSettings.setSearchBarStyle(it) }
        layout.fixedSearchBar?.let { uiSettings.setFixedSearchBar(it) }
        layout.reduceAnimations?.let { performanceSettings.setReduceAnimations(it) }
        layout.animationSpeed?.let { performanceSettings.setAnimationSpeed(it) }
    }
}
