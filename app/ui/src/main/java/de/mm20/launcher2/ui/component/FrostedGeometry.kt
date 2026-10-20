package de.mm20.launcher2.ui.component

import kotlin.math.roundToInt

/** Common center-crop in window coordinates: scrolling a card must not move its wallpaper. */
internal object FrostedGeometry {
    data class Crop(val left: Float, val top: Float, val width: Int, val height: Int)
    fun crop(imageWidth: Int, imageHeight: Int, windowWidth: Int, windowHeight: Int): Crop? {
        if (minOf(imageWidth, imageHeight, windowWidth, windowHeight) <= 0) return null
        val scale = maxOf(windowWidth.toFloat() / imageWidth, windowHeight.toFloat() / imageHeight)
        val width = (imageWidth * scale).roundToInt()
        val height = (imageHeight * scale).roundToInt()
        return Crop((windowWidth - width) / 2f, (windowHeight - height) / 2f, width, height)
    }
}
