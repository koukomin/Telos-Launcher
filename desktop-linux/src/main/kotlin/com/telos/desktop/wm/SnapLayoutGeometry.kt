package com.telos.desktop.wm

/**
 * Helix snap-zone layouts for the Compose window manager and host WM bridge.
 * Geometry is pure math over [WorkArea] so unit tests do not need a display.
 */
enum class SnapLayout {
    HalfLeft,
    HalfRight,
    TwoThirdsLeft,
    OneThirdRight,
    OneThirdLeft,
    TwoThirdsRight,
    GridTopLeft,
    GridTopRight,
    GridBottomLeft,
    GridBottomRight,
    Maximize,
    Restore,
}

data class SnapRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(width >= 0f && height >= 0f) { "SnapRect size must be non-negative" }
    }
}

object SnapLayoutGeometry {
    /** Fraction used for the wide pane in 2/3–1/3 layouts. */
    const val TWO_THIRDS = 2f / 3f
    const val ONE_THIRD = 1f / 3f

    fun rect(layout: SnapLayout, area: WorkArea): SnapRect {
        val w = area.width
        val h = area.height
        val x0 = area.minX
        val y0 = area.minY
        val halfW = w / 2f
        val halfH = h / 2f
        val twoThirdsW = w * TWO_THIRDS
        val oneThirdW = w * ONE_THIRD
        return when (layout) {
            SnapLayout.HalfLeft -> SnapRect(x0, y0, halfW, h)
            SnapLayout.HalfRight -> SnapRect(x0 + halfW, y0, halfW, h)
            SnapLayout.TwoThirdsLeft -> SnapRect(x0, y0, twoThirdsW, h)
            SnapLayout.OneThirdRight -> SnapRect(x0 + twoThirdsW, y0, oneThirdW, h)
            SnapLayout.OneThirdLeft -> SnapRect(x0, y0, oneThirdW, h)
            SnapLayout.TwoThirdsRight -> SnapRect(x0 + oneThirdW, y0, twoThirdsW, h)
            SnapLayout.GridTopLeft -> SnapRect(x0, y0, halfW, halfH)
            SnapLayout.GridTopRight -> SnapRect(x0 + halfW, y0, halfW, halfH)
            SnapLayout.GridBottomLeft -> SnapRect(x0, y0 + halfH, halfW, halfH)
            SnapLayout.GridBottomRight -> SnapRect(x0 + halfW, y0 + halfH, halfW, halfH)
            SnapLayout.Maximize -> SnapRect(x0, y0, w, h)
            SnapLayout.Restore -> SnapRect(x0, y0, w * 0.5f, h * 0.5f)
        }
    }

    /**
     * Cycle left-edge snaps: 50% → 2/3 → 1/3 → 50%.
     * Used by Super+Left.
     */
    fun cycleLeft(current: SnapLayout?): SnapLayout = when (current) {
        SnapLayout.HalfLeft -> SnapLayout.TwoThirdsLeft
        SnapLayout.TwoThirdsLeft -> SnapLayout.OneThirdLeft
        else -> SnapLayout.HalfLeft
    }

    /**
     * Cycle right-edge snaps: 50% → 2/3 → 1/3 → 50%.
     * Used by Super+Right.
     */
    fun cycleRight(current: SnapLayout?): SnapLayout = when (current) {
        SnapLayout.HalfRight -> SnapLayout.TwoThirdsRight
        SnapLayout.TwoThirdsRight -> SnapLayout.OneThirdRight
        else -> SnapLayout.HalfRight
    }

    fun fromLegacyZone(zone: SnapZone): SnapLayout? = when (zone) {
        SnapZone.Left -> SnapLayout.HalfLeft
        SnapZone.Right -> SnapLayout.HalfRight
        SnapZone.TopLeft -> SnapLayout.GridTopLeft
        SnapZone.TopRight -> SnapLayout.GridTopRight
        SnapZone.BottomLeft -> SnapLayout.GridBottomLeft
        SnapZone.BottomRight -> SnapLayout.GridBottomRight
        SnapZone.Maximize -> SnapLayout.Maximize
        SnapZone.None, SnapZone.Restore -> null
    }

    fun toLegacyZone(layout: SnapLayout): SnapZone = when (layout) {
        SnapLayout.HalfLeft -> SnapZone.Left
        SnapLayout.HalfRight -> SnapZone.Right
        SnapLayout.GridTopLeft -> SnapZone.TopLeft
        SnapLayout.GridTopRight -> SnapZone.TopRight
        SnapLayout.GridBottomLeft -> SnapZone.BottomLeft
        SnapLayout.GridBottomRight -> SnapZone.BottomRight
        SnapLayout.Maximize -> SnapZone.Maximize
        SnapLayout.Restore -> SnapZone.Restore
        // 2/3–1/3 maps to nearest half for drag-restore bookkeeping
        SnapLayout.TwoThirdsLeft, SnapLayout.OneThirdLeft -> SnapZone.Left
        SnapLayout.TwoThirdsRight, SnapLayout.OneThirdRight -> SnapZone.Right
    }
}
