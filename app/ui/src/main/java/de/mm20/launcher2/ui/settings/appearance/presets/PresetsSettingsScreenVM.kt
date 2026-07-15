package de.mm20.launcher2.ui.settings.appearance.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.presets.ThemePresetsRepository
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

    private val _presets = MutableStateFlow<List<ThemeBundle>>(emptyList())
    val presets = _presets.asStateFlow()

    init {
        viewModelScope.launch {
            _presets.value = presetsRepository.list()
        }
    }

    fun install(bundle: ThemeBundle) {
        val colors = bundle.colors ?: return
        viewModelScope.launch {
            val existing = themeRepository.colors.get(colors.id).first()
            if (existing != null) {
                themeRepository.colors.update(colors)
            } else {
                themeRepository.colors.create(colors)
            }
            uiSettings.setColorsId(colors.id)
        }
    }
}
