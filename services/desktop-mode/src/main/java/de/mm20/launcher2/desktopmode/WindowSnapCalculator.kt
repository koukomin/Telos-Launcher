// === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
package de.mm20.launcher2.desktopmode

import android.graphics.Rect
import android.util.DisplayMetrics

enum class SnapPosition {
    LEFT_HALF,
    RIGHT_HALF,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    MAXIMIZED
}

object WindowSnapCalculator {
    fun calculateBounds(displayMetrics: DisplayMetrics, snapPosition: SnapPosition, taskbarHeightPx: Int): Rect {
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels - taskbarHeightPx
        val halfW = w / 2
        val halfH = h / 2

        return when (snapPosition) {
            SnapPosition.LEFT_HALF -> Rect(0, 0, halfW, h)
            SnapPosition.RIGHT_HALF -> Rect(halfW, 0, w, h)
            SnapPosition.TOP_LEFT -> Rect(0, 0, halfW, halfH)
            SnapPosition.TOP_RIGHT -> Rect(halfW, 0, w, halfH)
            SnapPosition.BOTTOM_LEFT -> Rect(0, halfH, halfW, h)
            SnapPosition.BOTTOM_RIGHT -> Rect(halfW, halfH, w, h)
            SnapPosition.MAXIMIZED -> Rect(0, 0, w, h)
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
