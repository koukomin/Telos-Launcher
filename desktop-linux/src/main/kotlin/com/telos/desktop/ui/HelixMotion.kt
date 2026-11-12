package com.telos.desktop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

object HelixMotion {
    const val OverlayMs = 160
    const val WindowMs = 120
    const val DrawerMs = 180
    const val ToastMs = 4000L

    val enter: EnterTransition =
        fadeIn(tween(OverlayMs)) + scaleIn(initialScale = 0.96f, animationSpec = tween(OverlayMs))
    val exit: ExitTransition =
        fadeOut(tween(WindowMs)) + scaleOut(targetScale = 0.96f, animationSpec = tween(WindowMs))

    /** Yakuake-style Kickoff: slides down from under the top panel. */
    val drawerEnter: EnterTransition =
        slideInVertically(
            initialOffsetY = { full -> -full },
            animationSpec = tween(DrawerMs),
        ) + fadeIn(tween(OverlayMs))
    val drawerExit: ExitTransition =
        slideOutVertically(
            targetOffsetY = { full -> -full },
            animationSpec = tween(WindowMs),
        ) + fadeOut(tween(WindowMs))
}
