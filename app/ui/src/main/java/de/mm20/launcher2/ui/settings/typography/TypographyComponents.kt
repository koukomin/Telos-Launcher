package de.mm20.launcher2.ui.settings.typography

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.themes.typography.FontManager
import de.mm20.launcher2.themes.typography.FontFamily as ThemeFontFamily
import de.mm20.launcher2.themes.typography.FontWeight as ThemeFontWeight
import de.mm20.launcher2.themes.typography.TextStyle as ThemeTextStyle
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.theme.typography.fontFamilyOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun FontPreference(
    title: String,
    value: ThemeFontFamily?,
    removable: Boolean = false,
    onValueChange: (ThemeFontFamily?) -> Unit = {},
) {
    val context = LocalContext.current
    val fontManager = remember(context) { FontManager(context) }

    var showDialog by remember { mutableStateOf(false) }
    var showFontSettings by remember { mutableStateOf(false) }

    val fontVariantAxes = remember(value) {
        if (value is ThemeFontFamily.VariableFontFamily) {
            fontManager.getFontSettings(value)
        } else {
            emptyList()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(
            MaterialTheme.colorScheme.surfaceBright,
            MaterialTheme.shapes.extraSmall
        )
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Preference(
                title = title,
                summary = getFontName(context, value),
                icon = {
                    Text(
                        text = stringResource(I18nR.string.typo_preview_extra_short),
                        style = TextStyle(
                            fontFamily = remember(value) { fontFamilyOf(context, value) },
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    )
                },
                onClick = { showDialog = true },
                containerColor = Color.Transparent,
            )
        }
        if (fontVariantAxes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            IconButton(
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = { showFontSettings = true }
            ) {
                Icon(painterResource(BaseR.drawable.custom_typography_24px), null)
            }
        }
    }

    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        val fonts = remember { fontManager.getInstalledFonts() }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (fonts.builtIn.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        stringResource(I18nR.string.typo_preview_extra_short),
                        null,
                        fonts.builtIn,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }
            if (fonts.deviceDefault.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        stringResource(I18nR.string.typo_preview_extra_short),
                        stringResource(I18nR.string.font_category_device_default),
                        fonts.deviceDefault,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }
            if (fonts.generic.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        stringResource(I18nR.string.typo_preview_extra_short),
                        stringResource(I18nR.string.font_category_generic),
                        fonts.generic,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }

            if (removable) {
                item {
                    PreferenceCategory() {
                        Preference(
                            title = stringResource(I18nR.string.remove_font_style),
                            icon = {
                                Icon(
                                    painterResource(BaseR.drawable.delete_24px),
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                onValueChange(null)
                                showDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (value is ThemeFontFamily.VariableFontFamily) {
        DismissableBottomSheet(
            expanded = showFontSettings,
            onDismissRequest = {
                showFontSettings = false
            }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(I18nR.string.typo_preview_long),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = fontFamilyOf(context, value)
                    )
                }

                PreferenceCategory {
                    for (axis in fontVariantAxes) {
                        SliderPreference(
                            title = axis.label,
                            value = value.settings.get(axis.name) ?: axis.defaultValue,
                            onValueChanged = {
                                val map = value.settings.toMutableMap()
                                map[axis.name] = it

                                if (value is ThemeFontFamily.LauncherDefault) {
                                    onValueChange(value.copy(settings = map))
                                }
                            },
                            min = axis.range.start,
                            max = axis.range.endInclusive,
                            step = axis.step,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FontPickerCategory(
    previewText: String, categoryName: String?,
    fonts: List<ThemeFontFamily?>,
    onFontClick: (ThemeFontFamily?) -> Unit = {},
) {
    val context = LocalContext.current
    PreferenceCategory(categoryName) {
        for (font in fonts) {
            val f = remember(font) { fontFamilyOf(context, font) }
            Preference(
                title = {
                    Text(
                        getFontName(context, font),
                        fontFamily = f
                    )
                },
                icon = {
                    Text(
                        text = previewText,
                        style = TextStyle(
                            fontFamily = f,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    )
                },
                summary = getFontSummary(context, font)?.let { { Text(it, fontFamily = f) } },
                onClick = {
                    onFontClick(font)
                },
            )
        }
    }
}

internal fun getFontName(context: Context, fontFamily: ThemeFontFamily?): String {
    return when (fontFamily) {
        is ThemeFontFamily.LauncherDefault -> "Google Sans Flex"
        is ThemeFontFamily.DeviceHeadline -> context.getString(I18nR.string.font_name_device_headline)
        is ThemeFontFamily.DeviceBody -> context.getString(I18nR.string.font_name_device_body)
        is ThemeFontFamily.System -> fontFamily.name
        is ThemeFontFamily.SansSerif -> "sans-serif"
        is ThemeFontFamily.Serif -> "serif"
        is ThemeFontFamily.Monospace -> "monospace"
        null -> context.getString(I18nR.string.au3_appearance_font_default)
    }
}

internal fun getFontSummary(context: Context, fontFamily: ThemeFontFamily?): String? {
    return when (fontFamily) {
        is ThemeFontFamily.DeviceHeadline -> {
            val resId = context.resources
                .getIdentifier("config_headlineFontFamily", "string", "android")
            if (resId != 0) return context.getString(resId)
            return "sans-serif"
        }

        is ThemeFontFamily.DeviceBody -> {
            val resId = context.resources
                .getIdentifier("config_bodyFontFamily", "string", "android")
            if (resId != 0) return context.getString(resId)
            return "sans-serif"
        }

        else -> null
    }
}

@Composable
internal fun SliderRow(
    @DrawableRes icon: Int,
    min: Float,
    max: Float,
    step: Float = 1f,
    value: Float,
    onValueChange: (Float) -> Unit,
    formatValue: (Float) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Slider(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = ((max - min) / step).roundToInt() - 1,
        )
        Text(
            modifier = Modifier.width(32.dp),
            text = formatValue(value),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TextStylePreference(
    title: String,
    textStyle: TextStyle,
    fonts: Map<String, ThemeFontFamily?>,
    value: ThemeTextStyle<ThemeFontWeight?>?,
    parentValue: ThemeTextStyle<ThemeFontWeight.Absolute?>? = null,
    defaultValue: ThemeTextStyle<ThemeFontWeight>?,
    defaultValueParent: ThemeTextStyle<ThemeFontWeight.Absolute?>? = null,
    onValueChange: (ThemeTextStyle<ThemeFontWeight.Absolute?>?) -> Unit = {},
) {
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    Preference(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        },
        icon = {
            Text(
                text = stringResource(I18nR.string.typo_preview_extra_short),
                style = textStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        },
        onClick = { showDialog = true },
    )
    var fontFamily by remember(value) {
        mutableStateOf(value?.fontFamily)
    }
    var fontSize by remember(value) {
        mutableStateOf(value?.fontSize)
    }
    var lineHeight by remember(value) {
        mutableStateOf(value?.lineHeight)
    }
    var weight by remember(value) {
        mutableStateOf((value?.fontWeight as? ThemeFontWeight.Absolute)?.weight)
    }
    var letterSpacing by remember(value) {
        mutableStateOf(value?.letterSpacing)
    }

    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = {
            onValueChange(
                ThemeTextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = weight?.let { ThemeFontWeight.Absolute(it) },
                    letterSpacing = letterSpacing
                )
            )
            showDialog = false
        },
    ) {
        val actualFontFamily = fontFamily
            ?: parentValue?.fontFamily
            ?: defaultValue?.fontFamily
            ?: defaultValueParent?.fontFamily!!
        val actualFontSize = fontSize
            ?: parentValue?.fontSize
            ?: defaultValue?.fontSize
            ?: defaultValueParent?.fontSize!!
        val actualLineHeight = lineHeight
            ?: parentValue?.lineHeight
            ?: defaultValue?.lineHeight
            ?: defaultValueParent?.lineHeight!!
        val actualWeight = when {
            weight != null -> FontWeight(weight!!)
            parentValue?.fontWeight != null -> FontWeight(parentValue.fontWeight!!.weight)
            defaultValue?.fontWeight is ThemeFontWeight.Absolute -> FontWeight((defaultValue.fontWeight as ThemeFontWeight.Absolute).weight)
            defaultValue?.fontWeight is ThemeFontWeight.Relative &&
                    defaultValueParent?.fontWeight is ThemeFontWeight.Absolute -> {
                FontWeight(
                    (defaultValueParent.fontWeight as ThemeFontWeight.Absolute).weight +
                            (defaultValue.fontWeight as ThemeFontWeight.Relative).relativeWeight
                )
            }

            else -> FontWeight.Normal
        }
        val actualLetterSpacing = letterSpacing
            ?: parentValue?.letterSpacing
            ?: defaultValue?.letterSpacing
            ?: defaultValueParent?.letterSpacing!!

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(I18nR.string.typo_preview_long),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = fontFamilyOf(context, fonts[actualFontFamily]),
                    fontSize = actualFontSize.sp,
                    lineHeight = actualLineHeight.em,
                    fontWeight = actualWeight,
                    letterSpacing = actualLetterSpacing.em,
                )
            }
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for ((name, font) in fonts) {
                    val f = remember(font) { fontFamilyOf(context, font) }
                    Column(
                        modifier = Modifier
                            .width(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedIconToggleButton(
                            modifier = Modifier.size(56.dp),
                            checked = name == actualFontFamily,
                            onCheckedChange = {
                                if (it) fontFamily = name
                            },
                        ) {
                            Text(
                                text = stringResource(I18nR.string.typo_preview_extra_short),
                                fontFamily = f,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = name.capitalize(LocaleList.current),
                            fontFamily = f,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
            }

            SliderRow(
                icon = BaseR.drawable.format_bold_24px,
                min = 100f,
                max = 900f,
                step = 100f,
                value = actualWeight.weight.toFloat(),
                onValueChange = { weight = it.toInt() },
                formatValue = {
                    it.roundToInt().toString()
                }
            )
            SliderRow(
                icon = BaseR.drawable.format_size_24px,
                min = floor((defaultValue?.fontSize ?: defaultValueParent?.fontSize)!! / 2f),
                max = ceil((defaultValue?.fontSize ?: defaultValueParent?.fontSize)!! * 2f),
                value = actualFontSize.toFloat(),
                onValueChange = {
                    fontSize = it.roundToInt()
                },
                formatValue = {
                    it.roundToInt().toString()
                }
            )
            SliderRow(
                icon = BaseR.drawable.format_line_spacing_24px,
                min = 0.5f,
                max = 2f,
                step = 0.05f,
                value = actualLineHeight,
                onValueChange = { lineHeight = it },
                formatValue = {
                    (it * 100).roundToInt().toString() + "%"
                }
            )
            SliderRow(
                icon = BaseR.drawable.format_letter_spacing_24px,
                min = -0.25f,
                max = 1f,
                step = 0.01f,
                value = actualLetterSpacing,
                onValueChange = { letterSpacing = (it * 100f).roundToInt() / 100f },
                formatValue = {
                    (it * 100).roundToInt().toString() + "%"
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp)
            )

            TextButton(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                onClick = {
                    fontFamily = null
                    fontSize = null
                    lineHeight = null
                    weight = null
                    letterSpacing = null
                    onValueChange(null)
                }
            ) {
                Icon(
                    painterResource(BaseR.drawable.restart_alt_20px), null,
                    modifier = Modifier
                        .padding(ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize)
                )
                Text(stringResource(I18nR.string.preference_restore_default))
            }
        }
    }
}

@Composable
internal fun TypographyPreview(
    previewTypography: Typography,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MaterialTheme(
            typography = previewTypography
        ) {
            content()
        }
    }
}
