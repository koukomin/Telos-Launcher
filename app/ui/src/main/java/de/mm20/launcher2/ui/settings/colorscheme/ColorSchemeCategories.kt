package de.mm20.launcher2.ui.settings.colorscheme

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.themes.colors.ColorScheme
import de.mm20.launcher2.themes.colors.CorePalette
import de.mm20.launcher2.themes.colors.Theme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import hct.Hct
import scheme.SchemeExpressive

internal fun LazyListScope.CorePaletteCategory(
    theme: Theme,
    previewColorScheme: ColorScheme,
    selectedColorScheme: ColorScheme,
    selectedDefaultScheme: ColorScheme,
    mergedCorePalette: CorePalette,
    previewDarkTheme: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    updateTheme: (Theme) -> Unit
) {
item {

            PreferenceCategory(
                title = stringResource(I18nR.string.preference_custom_colors_corepalette),
            ) {
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_primary),
                    value = theme?.corePalette?.primary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    primary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.primary,
                )
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_secondary),
                    value = theme?.corePalette?.secondary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    secondary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.secondary,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            SchemeExpressive(
                                Hct.fromInt(it), false, 0.0,
                            ).secondaryPaletteKeyColor
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_tertiary),
                    value = theme?.corePalette?.tertiary,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    tertiary = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.tertiary,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            SchemeExpressive(
                                Hct.fromInt(it), false, 0.0,
                            ).tertiaryPaletteKeyColor
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_neutral),
                    value = theme?.corePalette?.neutral,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    neutral = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.neutral,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            SchemeExpressive(
                                Hct.fromInt(it), false, 0.0,
                            ).neutralPaletteKeyColor
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_neutral_variant),
                    value = theme?.corePalette?.neutralVariant,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    neutralVariant = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.neutralVariant,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            SchemeExpressive(
                                Hct.fromInt(it), false, 0.0,
                            ).neutralVariantPaletteKeyColor
                        }
                    },
                )
                CorePaletteColorPreference(
                    title = stringResource(I18nR.string.au3_appearance_cs_error),
                    value = theme?.corePalette?.error,
                    onValueChange = {
                        viewModel.updateTheme(
                            theme!!.copy(
                                corePalette = theme!!.corePalette.copy(
                                    error = it
                                )
                            )
                        )
                    },
                    defaultValue = systemPalette.error,
                    autoGenerate = {
                        theme!!.corePalette.primary?.let {
                            SchemeExpressive(
                                Hct.fromInt(it), false, 0.0,
                            ).errorPalette.keyColor.toInt()
                        }
                    },
                )
            }
        }
}

