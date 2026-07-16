package de.mm20.launcher2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import de.mm20.launcher2.ui.theme.shapes.shapesOf
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.transparencySchemeOf
import de.mm20.launcher2.ui.theme.typography.typographyOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.compose.koinInject
import java.time.LocalTime
import de.mm20.launcher2.preferences.ColorScheme as ColorSchemePref


@Composable
fun LauncherTheme(
    content: @Composable () -> Unit
) {
    val uiSettings: UiSettings = koinInject()
    val themeRepository: ThemeRepository = koinInject()

    val themeColors by remember {
        uiSettings.colorsId.flatMapLatest {
            themeRepository.colors.getOrDefault(it)
        }
    }.collectAsState(null)

    val themeShapes by remember {
        uiSettings.shapesId.flatMapLatest {
            themeRepository.shapes.getOrDefault(it)
        }
    }.collectAsState(null)

    val themeTypography by remember {
        uiSettings.typographyId.flatMapLatest {
            themeRepository.typographies.getOrDefault(it)
        }
    }.collectAsState(null)

    val themeTransparencies by remember {
        uiSettings.transparenciesId.flatMapLatest {
            themeRepository.transparencies.getOrDefault(it)
        }
    }.collectAsState(null)

    val colorSchemePref by remember { uiSettings.colorScheme }.collectAsState(
        ColorSchemePref.System
    )
    val nightStart by remember { uiSettings.colorSchemeNightStart }.collectAsState(20)
    val dayStart by remember { uiSettings.colorSchemeDayStart }.collectAsState(7)

    val darkTheme = when (colorSchemePref) {
        ColorSchemePref.Dark -> true
        ColorSchemePref.Light -> false
        ColorSchemePref.System -> isSystemInDarkTheme()
        ColorSchemePref.Time -> rememberIsNight(nightStart, dayStart)
    }

    if (themeColors == null || themeShapes == null || themeTransparencies == null || themeTypography == null) {
        return
    }

    val colorScheme = if (darkTheme) {
        darkColorSchemeOf(themeColors!!)
    } else {
        lightColorSchemeOf(themeColors!!)
    }

    val shapes = shapesOf(themeShapes!!)
    val typography = typographyOf(themeTypography!!)

    val transparencyScheme = transparencySchemeOf(themeTransparencies!!)


    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalTransparencyScheme provides transparencyScheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

/**
 * True while the current time falls in the dark ("night") window that starts at [nightStart]
 * and ends at [dayStart] (hours of day, wrapping past midnight). Re-checks every minute so the
 * theme flips at the boundary without restarting the launcher.
 */
@Composable
private fun rememberIsNight(nightStart: Int, dayStart: Int): Boolean {
    var isNight by remember { mutableStateOf(computeIsNight(LocalTime.now().hour, nightStart, dayStart)) }
    LaunchedEffect(nightStart, dayStart) {
        while (true) {
            isNight = computeIsNight(LocalTime.now().hour, nightStart, dayStart)
            delay(60_000L)
        }
    }
    return isNight
}

private fun computeIsNight(hour: Int, nightStart: Int, dayStart: Int): Boolean {
    if (nightStart == dayStart) return false
    val nightLength = ((dayStart - nightStart) + 24) % 24
    val offset = ((hour - nightStart) + 24) % 24
    return offset < nightLength
}

