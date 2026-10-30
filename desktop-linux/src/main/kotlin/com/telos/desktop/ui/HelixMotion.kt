package com.telos.desktop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

object HelixMotion {
    const val OverlayMs = 160
    const val WindowMs = 120
    const val ToastMs = 4000L

    val enter: EnterTransition =
        fadeIn(tween(OverlayMs)) + scaleIn(initialScale = 0.96f, animationSpec = tween(OverlayMs))
    val exit: ExitTransition =
        fadeOut(tween(WindowMs)) + scaleOut(targetScale = 0.96f, animationSpec = tween(WindowMs))
}
