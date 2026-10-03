package de.mm20.launcher2.ui.settings.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlin.math.roundToInt
import de.mm20.launcher2.ui.settings.appearance.presets.PresetsSettingsRoute
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemesSettingsRoute
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemesSettingsRoute
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object AppearanceSettingsRoute: NavKey

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    val colorThemeName by viewModel.colorThemeName.collectAsStateWithLifecycle(null)
    val typographyThemeName by viewModel.typographyThemeName.collectAsStateWithLifecycle(null)
    val shapeThemeName by viewModel.shapeThemeName.collectAsStateWithLifecycle(null)
    val transparencyThemeName by viewModel.transparencyThemeName.collectAsStateWithLifecycle(null)
    val compatModeColors by viewModel.compatModeColors.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null) {
            return@rememberLauncherForActivityResult
        }
        backStack.add(ImportThemeSettingsRoute(it))
    }

    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                Preference(
                    icon = R.drawable.wallpaper_24px,
                    title = stringResource(id = R.string.wallpaper),
                    summary = stringResource(id = R.string.preference_wallpaper_summary),
                    onClick = {
                        backStack.add(WallpaperSettingsRoute)
                    }
                )
                // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
                
                val theme by viewModel.colorScheme.collectAsState()
                ListPreference(
                    title = stringResource(id = R.string.preference_theme),
                    items = listOf(
                        stringResource(id = R.string.preference_theme_system) to ColorScheme.System,
                        stringResource(id = R.string.preference_theme_light) to ColorScheme.Light,
                        stringResource(id = R.string.preference_theme_dark) to ColorScheme.Dark,
                        stringResource(id = R.string.preference_theme_time) to ColorScheme.Time,
                    ),
                    value = theme,
                    onValueChanged = { newValue ->
                        if (newValue == null) return@ListPreference
                        viewModel.setColorScheme(newValue)
                    }
                )
                AnimatedVisibility(theme == ColorScheme.Time) {
                    Column {
                        val nightStart by viewModel.colorSchemeNightStart.collectAsState()
                        val dayStart by viewModel.colorSchemeDayStart.collectAsState()
                        SliderPreference(
                            title = stringResource(id = R.string.preference_theme_night_start),
                            value = nightStart ?: 20,
                            min = 0,
                            max = 23,
                            step = 1,
                            onValueChanged = { viewModel.setColorSchemeNightStart(it) },
                            label = { Text(formatHour(it)) },
                        )
                        SliderPreference(
                            title = stringResource(id = R.string.preference_theme_day_start),
                            value = dayStart ?: 7,
                            min = 0,
                            max = 23,
                            step = 1,
                            onValueChanged = { viewModel.setColorSchemeDayStart(it) },
                            label = { Text(formatHour(it)) },
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(id = R.string.preference_screen_presets),
                    onClick = {
                        backStack.add(PresetsSettingsRoute)
                    },
                    icon = R.drawable.auto_awesome_24dp,
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    summary = colorThemeName,
                    onClick = {
                        backStack.add(ColorSchemesSettingsRoute)
                    },
                    icon = R.drawable.palette_24px,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_typography),
                    summary = typographyThemeName,
                    onClick = {
                        backStack.add(TypographiesSettingsRoute)
                    },
                    icon = R.drawable.text_fields_24px,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_shapes),
                    summary = shapeThemeName,
                    onClick = {
                        backStack.add(ShapeSchemesSettingsRoute)
                    },
                    icon = R.drawable.crop_square_24px,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_transparencies),
                    summary = transparencyThemeName,
                    onClick = {
                        backStack.add(TransparencySchemesSettingsRoute)
                    },
                    icon = R.drawable.opacity_24px,
                )
            }
        }
        item {
            PreferenceCategory {
                val committedFontScale by viewModel.fontScale.collectAsStateWithLifecycle(1f)
                var liveFontScale by remember(committedFontScale) { mutableFloatStateOf(committedFontScale) }
                FontScalePreview(fontScale = liveFontScale)
                SliderPreference(
                    title = stringResource(R.string.preference_font_scale),
                    value = committedFontScale,
                    min = 0.8f,
                    max = 2f,
                    step = 0.05f,
                    onValueChange = { liveFontScale = it },
                    onValueChanged = { viewModel.setFontScale(it) },
                    label = { Text("${(it * 100).roundToInt()}%") },
                )
            }
        }

        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.theme_import_title),
                    icon = R.drawable.arrow_circle_down_24px,
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )
                Preference(
                    title = stringResource(R.string.theme_export_title),
                    icon = R.drawable.arrow_circle_up_24px,
                    onClick = {
                        backStack.add(ExportThemeSettingsRoute)
                    }
                )
            }
        }

        if (isAtLeastApiLevel(31)) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
                    ListPreference(
                        title = stringResource(R.string.preference_mdy_color_source),
                        items = listOf(
                            stringResource(R.string.preference_mdy_color_source_system) to false,
                            stringResource(R.string.preference_mdy_color_source_wallpaper) to true,
                        ),
                        value = compatModeColors,
                        onValueChanged = {
                            viewModel.setCompatModeColors(it)
                        }
                    )
                }
            }
        }
    }
}
private fun formatHour(hour: Int): String {
    return "%02d:00".format(hour)
}

/** Sample home-screen-style icon + label, scaled live as the font size slider is dragged. */
@Composable
private fun FontScalePreview(fontScale: Float) {
    val baseDensity = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(de.mm20.launcher2.ui.R.drawable.photo_24px), // Fallback icon
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
            }
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale),
            ) {
                Text(
                    text = stringResource(R.string.preference_font_scale_preview_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
