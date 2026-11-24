package de.mm20.launcher2.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer

/** Shared loaders for Telos virtual-app tiles. Callers keep their drawable and plate colors. */
object VirtualAppIcon {
    fun disc(
        context: Context,
        @DrawableRes foreground: Int,
        backgroundColor: Int,
    ): LauncherIcon? {
        val drawable = ContextCompat.getDrawable(context, foreground) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(ColorDrawable(backgroundColor), 1f),
        )
    }

    fun tile(
        context: Context,
        domain: String,
        @DrawableRes fallback: Int,
    ): LauncherIcon? {
        val res = TelosAppIcons.forDomain(domain) ?: fallback
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer,
        )
    }

    fun tileOnPlate(
        context: Context,
        domain: String,
        @DrawableRes fallback: Int,
        backgroundColor: Int,
    ): LauncherIcon? {
        val res = TelosAppIcons.forDomain(domain) ?: fallback
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = StaticIconLayer(ColorDrawable(backgroundColor), 1f),
        )
    }
}
