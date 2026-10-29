package com.telos.desktop.wm

data class WorkArea(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    val topBarHeight: Float,
    val bottomDockHeight: Float,
) {
    val maxX: Float get() = minX + width
    val maxY: Float get() = minY + height

    companion object {
        const val DEFAULT_TOP_BAR_PX = 36f
        const val DEFAULT_DOCK_PX = 56f

        fun of(
            screenWidth: Float,
            screenHeight: Float,
            topBarHeight: Float = DEFAULT_TOP_BAR_PX,
            bottomDockHeight: Float = DEFAULT_DOCK_PX,
            dockVisible: Boolean = true,
        ): WorkArea {
            val bottom = if (dockVisible) bottomDockHeight else 0f
            return WorkArea(
                minX = 0f,
                minY = topBarHeight,
                width = screenWidth,
                height = (screenHeight - topBarHeight - bottom).coerceAtLeast(120f),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                topBarHeight = topBarHeight,
                bottomDockHeight = bottom,
            )
        }
    }
}
