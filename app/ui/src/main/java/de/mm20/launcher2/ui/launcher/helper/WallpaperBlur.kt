package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
/** @param blurRadius target blur radius in pixels */
fun WallpaperBlur(blurRadius: () -> Int) {
    if (!isAtLeastApiLevel(31)) return
    val context = LocalContext.current

    val radius = blurRadius()
    DisposableEffect(Unit) {
        onDispose {
            // Blur setting switched off (or scaffold left): don't leave the last blur behind
            val window = (context as? Activity)?.window ?: return@onDispose
            val attrs = window.attributes
            attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            window.attributes = attrs
            window.setBackgroundBlurRadius(0)
        }
    }
    val animatable = remember { Animatable(0, Int.VectorConverter) }
    LaunchedEffect(radius) {
        animatable.animateTo(radius) {
            if (value > 0) {
                val windowAttributes = (context as Activity).window.attributes
                windowAttributes.flags =
                    windowAttributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                context.window.attributes = windowAttributes
                context.window.setBackgroundBlurRadius(value)
            } else {
                val windowAttributes = (context as Activity).window.attributes
                windowAttributes.flags =
                    windowAttributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                context.window.attributes = windowAttributes
                context.window.setBackgroundBlurRadius(0)
            }
        }

    }
}