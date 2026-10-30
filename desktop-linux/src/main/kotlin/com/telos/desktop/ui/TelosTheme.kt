package com.telos.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.uikmp.TelosSharedColors
import de.mm20.launcher2.uikmp.TelosSharedTheme
import de.mm20.launcher2.uikmp.telosSharedColorScheme

typealias TelosColors = TelosSharedColors

fun telosColorScheme(accent: Color = TelosColors.primary) = telosSharedColorScheme(accent)

@Composable
fun TelosDesktopTheme(accent: Color = TelosColors.primary, content: @Composable () -> Unit) {
    TelosSharedTheme(accent, content)
}
