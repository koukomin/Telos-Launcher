package de.mm20.launcher2.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.scale

/**
 * Ultra-Fast 0ms Dominant Color Extractor for Dynamic Glass UI and Theme Adaptation.
 * Downsamples bitmaps to a 16x16 grid to extract dominant colors without UI lag or memory allocation.
 */
object TelosColorExtractor {

    /**
     * Extracts the dominant [Color] from a given bitmap using fast 16x16 downsampling.
     */
    fun extractDominantColor(bitmap: Bitmap?): Color {
        if (bitmap == null || bitmap.isRecycled) return Color(0xFF12131C)

        return try {
            val scaled = bitmap.scale(16, 16, filter = false)
            var redSum = 0L
            var greenSum = 0L
            var blueSum = 0L
            val totalPixels = 16 * 16

            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    val pixel = scaled.getPixel(x, y)
                    redSum += (pixel shr 16) and 0xFF
                    greenSum += (pixel shr 8) and 0xFF
                    blueSum += pixel and 0xFF
                }
            }

            if (!scaled.isRecycled && scaled != bitmap) {
                scaled.recycle()
            }

            Color(
                red = (redSum / totalPixels).toInt(),
                green = (greenSum / totalPixels).toInt(),
                blue = (blueSum / totalPixels).toInt(),
                alpha = 255
            )
        } catch (e: Exception) {
            Color(0xFF12131C)
        }
    }
}
