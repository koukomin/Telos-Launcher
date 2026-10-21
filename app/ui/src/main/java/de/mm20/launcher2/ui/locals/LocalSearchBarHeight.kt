package de.mm20.launcher2.ui.locals

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Measured search content, excluding system/IME insets, for adjacent home controls. */
val LocalSearchBarHeight = compositionLocalOf { 56.dp }
