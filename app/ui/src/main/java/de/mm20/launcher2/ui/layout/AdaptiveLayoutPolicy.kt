package de.mm20.launcher2.ui.layout

import kotlin.math.floor

/** Constraint policy only; dimensions are available dp after parent padding/insets.
 * Font scale affects space budgets, never the density used to render text.
 */
object AdaptiveLayoutPolicy {
    const val MinimumTouchTarget = 48f
    const val ContentWidth = 840f
    const val Gap = 12f

    fun columns(width: Float, minimumCell: Float, preferred: Int = Int.MAX_VALUE): Int =
        minOf(preferred.coerceAtLeast(1), floor(width.coerceAtLeast(0f) / minimumCell.coerceAtLeast(MinimumTouchTarget)).toInt().coerceAtLeast(1))

    fun stackPreference(width: Float, fontScale: Float): Boolean =
        width < 360f * fontScale.coerceAtLeast(1f)

    fun sideBySide(width: Float, height: Float, fontScale: Float): Boolean =
        width >= 2f * 320f * fontScale.coerceAtLeast(1f) + Gap &&
            height >= 320f * fontScale.coerceAtLeast(1f)

    fun keypadWidth(width: Float, columns: Int, fontScale: Float): Float =
        maxOf(width, columns * (72f * fontScale + 8f) + 24f)

    /** Center reading/form content without constraining media, editors or workspace canvases. */
    fun contentGutter(width: Float): Float = maxOf(Gap, (width - ContentWidth) / 2f)
}
