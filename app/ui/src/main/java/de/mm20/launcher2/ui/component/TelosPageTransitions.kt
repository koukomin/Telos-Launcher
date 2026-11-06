package de.mm20.launcher2.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.ui.UiSettings
import org.koin.compose.koinInject

@Composable
fun rememberVirtualAppPageEffects(): Boolean {
    val ui: UiSettings = koinInject()
    val enabled by ui.virtualAppPageEffects.collectAsStateWithLifecycle(false)
    return enabled
}

fun telosPageEnter(effects: Boolean) =
    if (effects) slideInHorizontally(tween(280)) { it / 2 } + scaleIn(tween(280), initialScale = 0.9f) + fadeIn(tween(280))
    else EnterTransition.None

fun telosPageExit(effects: Boolean) =
    if (effects) slideOutHorizontally(tween(280)) { -it / 4 }
    else ExitTransition.None

fun telosPagePopEnter(effects: Boolean) =
    if (effects) slideInHorizontally(tween(280)) { -it / 4 }
    else EnterTransition.None

fun telosPagePopExit(effects: Boolean) =
    if (effects) slideOutHorizontally(tween(280)) { it / 2 } + scaleOut(tween(280), targetScale = 0.9f) + fadeOut(tween(280))
    else ExitTransition.None

fun telosPageTransition(effects: Boolean) = telosPageEnter(effects) togetherWith telosPageExit(effects)

fun telosPagePopTransition(effects: Boolean) = telosPagePopEnter(effects) togetherWith telosPagePopExit(effects)
