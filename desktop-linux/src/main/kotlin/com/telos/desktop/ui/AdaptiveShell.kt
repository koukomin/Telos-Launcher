package com.telos.desktop.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ShellFormFactor { Compact, Tablet, Desktop }

object AdaptiveShell {
    fun formFactor(width: Dp, height: Dp): ShellFormFactor = when {
        width < 700.dp -> ShellFormFactor.Compact
        width < 1100.dp || height < 700.dp -> ShellFormFactor.Tablet
        else -> ShellFormFactor.Desktop
    }

    fun topBarHeight(form: ShellFormFactor): Float = when (form) {
        ShellFormFactor.Compact -> 32f
        else -> 36f
    }

    fun showDock(form: ShellFormFactor): Boolean = form != ShellFormFactor.Compact
}
