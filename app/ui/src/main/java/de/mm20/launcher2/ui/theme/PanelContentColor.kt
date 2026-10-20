package de.mm20.launcher2.ui.theme

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

@androidx.compose.runtime.Composable
internal fun panelContentColor(surface: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
    return androidx.compose.runtime.remember(surface) {
        val a = relativeLuminance(surface.compositeOver(androidx.compose.ui.graphics.Color.Black).toArgb())
        val b = relativeLuminance(surface.compositeOver(androidx.compose.ui.graphics.Color.White).toArgb())
        if (chooseContrast(minOf(a, b), maxOf(a, b)).darkText)
            androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    }
}
