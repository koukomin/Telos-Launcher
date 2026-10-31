package de.mm20.launcher2.ui.settings.appearance
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.shapes.Shapes
import de.mm20.launcher2.themes.ThemeBundle
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.themes.typography.Typography
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ExportThemeSettingsScreenVM: ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()

    val colorSchemes = themeRepository.colors.getAll().map { it.filter { !it.builtIn } }
    val typographySchemes = themeRepository.typographies.getAll().map { it.filter { !it.builtIn } }
    val shapeSchemes = themeRepository.shapes.getAll().map { it.filter { !it.builtIn } }
    val transparencySchemes = themeRepository.transparencies.getAll().map { it.filter { !it.builtIn } }

    var themeName by mutableStateOf("")
    var themeAuthor by mutableStateOf("")


    fun init() {
        // Only reset when nothing has been picked yet, so a configuration change
        // does not wipe the name the user typed.
        if (colorScheme == null && typographyScheme == null && shapeScheme == null && transparencyScheme == null) {
            themeName = ""
        }
    }

    /** File name (without extension) that is safe to use in the cache dir and as a suggested name. */
    val fileBaseName: String
        get() = themeName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "theme" }

    var colorScheme by mutableStateOf<Colors?>(null)
        @JvmName("_setColorScheme")
        private set
    fun setColorScheme(scheme: Colors?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        colorScheme = scheme
    }

    var typographyScheme by mutableStateOf<Typography?>(null)
        @JvmName("_setTypographyScheme")
        private set
    fun setTypographyScheme(scheme: Typography?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        typographyScheme = scheme
    }

    var shapeScheme by mutableStateOf<Shapes?>(null)
        @JvmName("_setShapeScheme")
        private set
    fun setShapeScheme(scheme: Shapes?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        shapeScheme = scheme
    }

    var transparencyScheme by mutableStateOf<Transparencies?>(null)
        @JvmName("_setTransparencyScheme")
        private set
    fun setTransparencyScheme(scheme: Transparencies?) {
        if (themeName.isBlank() && scheme != null) themeName = scheme.name
        transparencyScheme = scheme
    }

    private fun getThemeBundle(): ThemeBundle {
        return ThemeBundle(
            name = themeName,
            author = themeAuthor.takeIf { it.isNotBlank() },
            colors = colorScheme,
            shapes = shapeScheme,
            typography = typographyScheme,
            transparencies = transparencyScheme,
        )
    }

    fun exportTheme(context: Context, uri: Uri) {
        val themeBundle = getThemeBundle()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.writer()?.use {
                    it.write(themeBundle.toJson())
                }
            } catch (e: java.io.IOException) {
                android.util.Log.e("ExportTheme", "Failed to export theme", e)
            }
        }
    }

    fun shareTheme(context: Context) {
        val themeBundle = getThemeBundle()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "${fileBaseName}.kvtheme")
                try {
                    file.writeText(themeBundle.toJson())
                    file
                } catch (e: java.io.IOException) {
                    android.util.Log.e("ExportTheme", "Failed to write theme", e)
                    null
                }
            } ?: return@launch
            val shared = withContext(Dispatchers.IO) {
                ShareActions.shareFile(context, file, "application/json")
            }
            if (!shared) {
                Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}