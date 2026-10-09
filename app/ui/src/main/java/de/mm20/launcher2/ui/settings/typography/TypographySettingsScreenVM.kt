package de.mm20.launcher2.ui.settings.typography

import android.content.Context
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.themes.DefaultThemeId
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.typography.Typography
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class TypographySettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedTypography = uiSettings.typographyId
    val typography: Flow<List<Typography>> = themeRepository.typographies.getAll()

    fun getTypography(id: UUID): Flow<Typography?> {
        return themeRepository.typographies.get(id)
    }

    fun updateTypography(typography: Typography) {
        themeRepository.typographies.update(typography)
    }

    fun selectTypography(typography: Typography) {
        uiSettings.setTypographyId(typography.id)
    }

    fun duplicate(typography: Typography) {
        themeRepository.typographies.create(typography.copy(id = UUID.randomUUID()))
    }

    fun delete(typography: Typography) {
        viewModelScope.launch {
            // Don't leave a dangling selection that silently falls back to the default.
            if (uiSettings.typographyId.first() == typography.id) {
                uiSettings.setTypographyId(DefaultThemeId)
            }
        }
        themeRepository.typographies.delete(typography)
    }

    fun createNew(context: Context): UUID {
        val uuid = UUID.randomUUID()
        themeRepository.typographies.create(
            Typography(
                id = uuid,
                name = context.getString(R.string.new_theme_name)
            )
        )
        return uuid
    }
}