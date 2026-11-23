package de.mm20.launcher2.icons

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

/** Adaptive icons that ship a fully transparent background plate (any package). */
fun adaptiveBackgroundIsMissing(background: Drawable?): Boolean {
    if (background == null) return true
    if (background is ColorDrawable) return Color.alpha(background.color) == 0
    return false
}
