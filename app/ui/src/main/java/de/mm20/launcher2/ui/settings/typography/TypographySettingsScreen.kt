package de.mm20.launcher2.ui.settings.typography
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.serialization.UUIDSerializer
import de.mm20.launcher2.themes.typography.DefaultEmphasizedTextStyles
import de.mm20.launcher2.themes.typography.DefaultTextStyles
import de.mm20.launcher2.themes.typography.FontManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.theme.typography.fontFamilyOf
import de.mm20.launcher2.ui.theme.typography.typographyOf
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import de.mm20.launcher2.themes.typography.FontFamily as ThemeFontFamily
import de.mm20.launcher2.themes.typography.FontWeight as ThemeFontWeight
import de.mm20.launcher2.themes.typography.TextStyle as ThemeTextStyle

@Serializable
data class TypographySettingsRoute(
    @Serializable(with = UUIDSerializer::class) val id: UUID
) : NavKey

@Composable
fun TypographySettingsScreen(themeId: UUID) {
    val viewModel: TypographySettingsScreenVM = viewModel()

    val context = LocalContext.current

    val theme by remember(
        viewModel,
        themeId
    ) { viewModel.getTypography(themeId) }.collectAsStateWithLifecycle(null)

    val previewTypography = theme?.let { typographyOf(it) }

    var editName by remember { mutableStateOf(false) }

    if (editName) {
        var name by remember(theme) { mutableStateOf(theme?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTypography(theme!!.copy(name = name))
                        editName = false
                    }
                ) {
                    Text(stringResource(I18nR.string.save))
                }
            }
        )
    }

    PreferenceScreen(
        title = {
            Text(
                theme?.name ?: "",
                modifier = Modifier.clickable {
                    editName = true
                },
            )
        },
        helpUrl = "https://koukomin.github.io/Telos-Launcher/docs/user-guide/customization/color-schemes",
    ) {
        if (theme == null || previewTypography == null) return@PreferenceScreen

        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_typography_fonts)) {
                FontPreference(title = stringResource(I18nR.string.hc_brand), theme!!.fonts["brand"], onValueChange = {
                    viewModel.updateTypography(
                        theme!!.copy(
                            fonts = theme!!.fonts.toMutableMap().apply { put("brand", it) })
                    )
                })
                FontPreference(
                    title = stringResource(I18nR.string.hc_plain),
                    theme!!.fonts["plain"],
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                fonts = theme!!.fonts.toMutableMap().apply { put("plain", it) })
                        )
                    }
                )
                for (key in theme!!.fonts.keys.sorted()) {
                    if (key == "plain" || key == "brand") continue
                    FontPreference(
                        title = key.capitalize(Locale.current),
                        theme!!.fonts[key],
                        removable = true,
                        onValueChange = {
                            viewModel.updateTypography(
                                theme!!.copy(
                                    fonts = theme!!.fonts.toMutableMap().apply {
                                        if (it == null) {
                                            remove(key)
                                        } else {
                                            put(key, it)
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
        if (theme!!.fonts.size < 8) {
            item {
                FilledTonalButton(
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                    val firstFreeIndex = (1 .. 8).firstOrNull {
                        !theme!!.fonts.containsKey("custom $it")
                    }
                    if (firstFreeIndex != null) {
                        viewModel.updateTypography(
                            theme!!.copy(
                                fonts = theme!!.fonts.toMutableMap()
                                    .apply { put("custom $firstFreeIndex", ThemeFontFamily.SansSerif) })
                        )
                    }
                }) {
                    Icon(
                        painterResource(BaseR.drawable.add_20px),
                        null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(I18nR.string.add_font_style))
                }
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.au3_appearance_typo_body)) {
                TypographyPreview(previewTypography) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShapedLauncherIcon(
                            size = 48.dp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            stringResource(I18nR.string.typo_preview_medium1),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_small),
                    textStyle = previewTypography.bodySmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodySmall,
                    defaultValue = DefaultTextStyles.bodySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodySmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_medium),
                    textStyle = previewTypography.bodyMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodyMedium,
                    defaultValue = DefaultTextStyles.bodyMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodyMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_large),
                    textStyle = previewTypography.bodyLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodyLarge,
                    defaultValue = DefaultTextStyles.bodyLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodyLarge = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_small_emphasized),
                    textStyle = previewTypography.bodySmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodySmall,
                    parentValue = theme!!.styles.bodySmall,
                    defaultValue = DefaultEmphasizedTextStyles.bodySmall,
                    defaultValueParent = DefaultTextStyles.bodySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodySmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_medium_emphasized),
                    textStyle = previewTypography.bodyMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodyMedium,
                    parentValue = theme!!.styles.bodyMedium,
                    defaultValue = DefaultEmphasizedTextStyles.bodyMedium,
                    defaultValueParent = DefaultTextStyles.bodyMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodyMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_body_large_emphasized),
                    textStyle = previewTypography.bodyLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodyLarge,
                    parentValue = theme!!.styles.bodyLarge,
                    defaultValue = DefaultEmphasizedTextStyles.bodyLarge,
                    defaultValueParent = DefaultTextStyles.bodyLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodyLarge = it)
                            )
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.au3_appearance_typo_label)) {
                TypographyPreview(previewTypography) {
                    FilterChip(
                        modifier = Modifier
                            .padding(end = 16.dp),
                        label = {
                            Text(stringResource(I18nR.string.typo_preview_short1))
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(BaseR.drawable.tag_20px),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        selected = false,
                        onClick = {},
                    )
                    Button(onClick = {}) {
                        Text(stringResource(I18nR.string.typo_preview_medium2))
                    }
                }
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_small),
                    textStyle = previewTypography.labelSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelSmall,
                    defaultValue = DefaultTextStyles.labelSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_medium),
                    textStyle = previewTypography.labelMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelMedium,
                    defaultValue = DefaultTextStyles.labelMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_large),
                    textStyle = previewTypography.labelLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelLarge,
                    defaultValue = DefaultTextStyles.labelLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelLarge = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_small_emphasized),
                    textStyle = previewTypography.labelSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelSmall,
                    parentValue = theme!!.styles.labelSmall,
                    defaultValue = DefaultEmphasizedTextStyles.labelSmall,
                    defaultValueParent = DefaultTextStyles.labelSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_medium_emphasized),
                    textStyle = previewTypography.labelMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelMedium,
                    parentValue = theme!!.styles.labelMedium,
                    defaultValue = DefaultEmphasizedTextStyles.labelMedium,
                    defaultValueParent = DefaultTextStyles.labelMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_label_large_emphasized),
                    textStyle = previewTypography.labelLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelLarge,
                    parentValue = theme!!.styles.labelLarge,
                    defaultValue = DefaultEmphasizedTextStyles.labelLarge,
                    defaultValueParent = DefaultTextStyles.labelLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelLarge = it)
                            )
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.au3_appearance_typo_title)) {
                TypographyPreview(previewTypography) {
                    PrimaryTabRow(0, modifier = Modifier.width(300.dp)) {
                        LeadingIconTab(
                            selected = true,
                            text = { Text(stringResource(I18nR.string.typo_preview_short1)) },
                            icon = {
                                Icon(
                                    painterResource(BaseR.drawable.person_24px_filled),
                                    contentDescription = null,
                                )
                            },
                            onClick = {}
                        )
                        LeadingIconTab(
                            selected = false,
                            text = { Text(stringResource(I18nR.string.typo_preview_short2)) },
                            icon = {
                                Icon(
                                    painterResource(BaseR.drawable.enterprise_24px),
                                    contentDescription = null
                                )
                            },
                            onClick = {}
                        )
                    }
                }
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_small),
                    textStyle = previewTypography.titleSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleSmall,
                    defaultValue = DefaultTextStyles.titleSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_medium),
                    textStyle = previewTypography.titleMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleMedium,
                    defaultValue = DefaultTextStyles.titleMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_large),
                    textStyle = previewTypography.titleLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleLarge,
                    defaultValue = DefaultTextStyles.titleLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleLarge = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_small_emphasized),
                    textStyle = previewTypography.titleSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleSmall,
                    parentValue = theme!!.styles.titleSmall,
                    defaultValue = DefaultEmphasizedTextStyles.titleSmall,
                    defaultValueParent = DefaultTextStyles.titleSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_medium_emphasized),
                    textStyle = previewTypography.titleMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleMedium,
                    parentValue = theme!!.styles.titleMedium,
                    defaultValue = DefaultEmphasizedTextStyles.titleMedium,
                    defaultValueParent = DefaultTextStyles.titleMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_title_large_emphasized),
                    textStyle = previewTypography.titleLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleLarge,
                    parentValue = theme!!.styles.titleLarge,
                    defaultValue = DefaultEmphasizedTextStyles.titleLarge,
                    defaultValueParent = DefaultTextStyles.titleLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleLarge = it)
                            )
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.au3_appearance_typo_headline)) {
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_small),
                    textStyle = previewTypography.headlineSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineSmall,
                    defaultValue = DefaultTextStyles.headlineSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_medium),
                    textStyle = previewTypography.headlineMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineMedium,
                    defaultValue = DefaultTextStyles.headlineMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_large),
                    textStyle = previewTypography.headlineLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineLarge,
                    defaultValue = DefaultTextStyles.headlineLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineLarge = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_small_emphasized),
                    textStyle = previewTypography.headlineSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineSmall,
                    parentValue = theme!!.styles.headlineSmall,
                    defaultValue = DefaultEmphasizedTextStyles.headlineSmall,
                    defaultValueParent = DefaultTextStyles.headlineSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineSmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_medium_emphasized),
                    textStyle = previewTypography.headlineMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineMedium,
                    parentValue = theme!!.styles.headlineMedium,
                    defaultValue = DefaultEmphasizedTextStyles.headlineMedium,
                    defaultValueParent = DefaultTextStyles.headlineMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_headline_large_emphasized),
                    textStyle = previewTypography.headlineLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineLarge,
                    parentValue = theme!!.styles.headlineLarge,
                    defaultValue = DefaultEmphasizedTextStyles.headlineLarge,
                    defaultValueParent = DefaultTextStyles.headlineLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineLarge = it)
                            )
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.au3_appearance_typo_display)) {
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_small),
                    textStyle = previewTypography.displaySmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displaySmall,
                    defaultValue = DefaultTextStyles.displaySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displaySmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_medium),
                    textStyle = previewTypography.displayMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displayMedium,
                    defaultValue = DefaultTextStyles.displayMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displayMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_large),
                    textStyle = previewTypography.displayLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displayLarge,
                    defaultValue = DefaultTextStyles.displayLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displayLarge = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_small_emphasized),
                    textStyle = previewTypography.displaySmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displaySmall,
                    parentValue = theme!!.styles.displaySmall,
                    defaultValue = DefaultEmphasizedTextStyles.displaySmall,
                    defaultValueParent = DefaultTextStyles.displaySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displaySmall = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_medium_emphasized),
                    textStyle = previewTypography.displayMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displayMedium,
                    parentValue = theme!!.styles.displayMedium,
                    defaultValue = DefaultEmphasizedTextStyles.displayMedium,
                    defaultValueParent = DefaultTextStyles.displayMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displayMedium = it)
                            )
                        )
                    },
                )
                TextStylePreference(
                    title = stringResource(I18nR.string.hc_display_large_emphasized),
                    textStyle = previewTypography.displayLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displayLarge,
                    parentValue = theme!!.styles.displayLarge,
                    defaultValue = DefaultEmphasizedTextStyles.displayLarge,
                    defaultValueParent = DefaultTextStyles.displayLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displayLarge = it)
                            )
                        )
                    },
                )
            }
        }
    }
}