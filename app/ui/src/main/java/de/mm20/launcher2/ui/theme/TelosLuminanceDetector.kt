package de.mm20.launcher2.ui.theme

import android.graphics.Bitmap

/**
 * Fast 0ms Luminance Detector for Dynamic Contrast and Theme Text Adaptation.
 * Samples 5 strategic points (4 corners + center) to quickly decide if a surface/wallpaper is dark.
 */
object TelosLuminanceDetector {

    /**
     * Returns true if the bitmap's sampled area is dark (luminance < 0.5), false if light.
     */
    fun isDarkSurface(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled) return true

        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true

        val points = arrayOf(
            Pair(w / 4, h / 4),
            Pair(3 * w / 4, h / 4),
            Pair(w / 2, h / 2),
            Pair(w / 4, 3 * h / 4),
            Pair(3 * w / 4, 3 * h / 4)
        )

        var totalLuminance = 0f

        for ((x, y) in points) {
            val pixel = bitmap.getPixel(x, y)
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            totalLuminance += lum
        }

        val avgLuminance = totalLuminance / points.size
        return avgLuminance < 0.5f
    }
}
