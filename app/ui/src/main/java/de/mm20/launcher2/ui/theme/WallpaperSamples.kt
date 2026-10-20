package de.mm20.launcher2.ui.theme

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil

/** Immutable small grid of luminance extrema; no full wallpaper bitmap survives decoding. */
internal class WallpaperSamples(
    private val width: Int,
    private val height: Int,
    private val minima: DoubleArray,
    private val maxima: DoubleArray,
    private val aspect: Float,
) {
    fun choice(
        screenWidth: Float, screenHeight: Float, forced: Boolean? = null,
        leftFraction: Float = 0f, topFraction: Float = 0f,
        rightFraction: Float = 1f, bottomFraction: Float = 1f,
    ): ContrastChoice {
        if (screenWidth <= 0 || screenHeight <= 0 || abs(screenWidth / screenHeight - aspect) > 0.01f)
            return chooseContrast(forcedDark = forced)
        val left = floor(leftFraction * width).toInt().coerceIn(0, width - 1)
        val top = floor(topFraction * height).toInt().coerceIn(0, height - 1)
        val right = ceil(rightFraction * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil(bottomFraction * height).toInt().coerceIn(top + 1, height)
        var low = 1.0
        var high = 0.0
        for (y in top until bottom) for (x in left until right) {
            low = minOf(low, minima[y * width + x])
            high = maxOf(high, maxima[y * width + x])
        }
        return chooseContrast(low, high, forced)
    }
}
