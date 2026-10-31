package de.mm20.launcher2.ui.settings.colorscheme
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.toLegacyJson
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

class ColorSchemesSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedColors = uiSettings.colorsId
    val colors: Flow<List<Colors>> = themeRepository.colors.getAll()

    fun getTheme(id: UUID): Flow<Colors?> {
        return themeRepository.colors.get(id)
    }

    fun updateTheme(colors: Colors) {
        themeRepository.colors.update(colors)
    }

    fun selectTheme(colors: Colors) {
        uiSettings.setColorsId(colors.id)
    }

    fun duplicate(colors: Colors) {
        themeRepository.colors.create(colors.copy(id = UUID.randomUUID()))
    }

    fun delete(colors: Colors) {
        viewModelScope.launch {
            // Don't leave a dangling selection that silently falls back to the default.
            if (uiSettings.colorsId.first() == colors.id) {
                uiSettings.setColorsId(DefaultThemeId)
            }
        }
        themeRepository.colors.delete(colors)
    }

    fun exportTheme(context: Context, colors: Colors) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${colors.name}.kvtheme")
                file.writeText(colors.toLegacyJson())
                file
            }
            val shared = withContext(Dispatchers.IO) {
                ShareActions.shareFile(context, file, "application/json")
            }
            if (!shared) {
                Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createNew(context: Context): UUID {
        val uuid = UUID.randomUUID()
        themeRepository.colors.create(
            Colors(
                id = uuid,
                name = context.getString(I18nR.string.new_theme_name)
            )
        )
        return uuid
    }
}