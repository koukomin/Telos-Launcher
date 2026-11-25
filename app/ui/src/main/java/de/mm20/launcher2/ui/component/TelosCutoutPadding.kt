package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dynamic Display Cutout & Notch Inset Padding Modifier for Jetpack Compose.
 * Safe inset handling for punch-hole cameras, notches, and foldable displays in portrait and landscape.
 */
@Composable
fun Modifier.telosCutoutPadding(): Modifier {
    return this.windowInsetsPadding(WindowInsets.displayCutout)
}
