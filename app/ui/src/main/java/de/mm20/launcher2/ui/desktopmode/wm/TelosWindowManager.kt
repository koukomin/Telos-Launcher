// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.wm

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResizeDirection {
    NORTH, SOUTH, EAST, WEST,
    NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST
}

enum class SnapState {
    NONE, LEFT_HALF, RIGHT_HALF,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    MAXIMIZE
}

data class TelosWindowState(
    val id: String,
    val title: String,
    val iconRes: Int? = null,
    val x: Dp = 80.dp,
    val y: Dp = 80.dp,
    val width: Dp = 600.dp,
    val height: Dp = 400.dp,
    val zIndex: Float = 1f,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val isActive: Boolean = true,
    val isPinned: Boolean = false, // Always-on-top
    val isShaded: Boolean = false, // Collapsed to titlebar
    val opacity: Float = 1.0f,
    val snapState: SnapState = SnapState.NONE,
    val restoreX: Dp = 80.dp,
    val restoreY: Dp = 80.dp,
    val restoreWidth: Dp = 600.dp,
    val restoreHeight: Dp = 400.dp,
    val content: @Composable () -> Unit = {}
)

class TelosWindowManager {
    private val _windows = MutableStateFlow<List<TelosWindowState>>(emptyList())
    val windows: StateFlow<List<TelosWindowState>> = _windows.asStateFlow()

    private var nextZIndex = 10f

    fun addWindow(window: TelosWindowState) {
        val current = _windows.value
        val existing = current.firstOrNull { it.id == window.id }
        if (existing != null) {
            focusWindow(window.id)
            return
        }
        val maxZ = (current.maxOfOrNull { it.zIndex } ?: 0f) + 1f
        val newWindow = window.copy(
            zIndex = if (window.isPinned) maxZ + 1000f else maxZ,
            isActive = true
        )
        val updated = current.map { it.copy(isActive = false) } + newWindow
        _windows.value = updated
    }

    fun focusWindow(id: String) {
        val current = _windows.value
        if (current.none { it.id == id }) return
        val maxZ = (current.maxOfOrNull { it.zIndex } ?: 0f) + 1f
        _windows.value = current.map { win ->
            if (win.id == id) {
                win.copy(
                    zIndex = if (win.isPinned) maxZ + 1000f else maxZ,
                    isActive = true,
                    isMinimized = false
                )
            } else {
                win.copy(isActive = false)
            }
        }
    }

    fun closeWindow(id: String) {
        _windows.value = _windows.value.filterNot { it.id == id }
    }

    fun moveWindow(id: String, dx: Dp, dy: Dp) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id && !win.isMaximized) {
                val newX = (win.x + dx).coerceAtLeast(0.dp)
                val newY = (win.y + dy).coerceAtLeast(0.dp)
                win.copy(x = newX, y = newY, snapState = SnapState.NONE)
            } else win
        }
    }

    fun resizeWindow(
        id: String,
        direction: ResizeDirection,
        dx: Dp,
        dy: Dp,
        minWidth: Dp = 260.dp,
        minHeight: Dp = 160.dp
    ) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id && !win.isMaximized && !win.isShaded) {
                var newX = win.x
                var newY = win.y
                var newW = win.width
                var newH = win.height

                when (direction) {
                    ResizeDirection.EAST -> {
                        newW = (win.width + dx).coerceAtLeast(minWidth)
                    }
                    ResizeDirection.WEST -> {
                        val possibleW = win.width - dx
                        if (possibleW >= minWidth) {
                            newX = win.x + dx
                            newW = possibleW
                        }
                    }
                    ResizeDirection.SOUTH -> {
                        newH = (win.height + dy).coerceAtLeast(minHeight)
                    }
                    ResizeDirection.NORTH -> {
                        val possibleH = win.height - dy
                        if (possibleH >= minHeight) {
                            newY = win.y + dy
                            newH = possibleH
                        }
                    }
                    ResizeDirection.SOUTH_EAST -> {
                        newW = (win.width + dx).coerceAtLeast(minWidth)
                        newH = (win.height + dy).coerceAtLeast(minHeight)
                    }
                    ResizeDirection.SOUTH_WEST -> {
                        val possibleW = win.width - dx
                        if (possibleW >= minWidth) {
                            newX = win.x + dx
                            newW = possibleW
                        }
                        newH = (win.height + dy).coerceAtLeast(minHeight)
                    }
                    ResizeDirection.NORTH_EAST -> {
                        newW = (win.width + dx).coerceAtLeast(minWidth)
                        val possibleH = win.height - dy
                        if (possibleH >= minHeight) {
                            newY = win.y + dy
                            newH = possibleH
                        }
                    }
                    ResizeDirection.NORTH_WEST -> {
                        val possibleW = win.width - dx
                        if (possibleW >= minWidth) {
                            newX = win.x + dx
                            newW = possibleW
                        }
                        val possibleH = win.height - dy
                        if (possibleH >= minHeight) {
                            newY = win.y + dy
                            newH = possibleH
                        }
                    }
                }
                win.copy(x = newX, y = newY, width = newW, height = newH, snapState = SnapState.NONE)
            } else win
        }
    }

    fun snapWindow(id: String, snapState: SnapState, desktopWidth: Dp, desktopHeight: Dp) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id) {
                val halfW = desktopWidth / 2
                val halfH = desktopHeight / 2
                val restoreX = if (win.snapState == SnapState.NONE) win.x else win.restoreX
                val restoreY = if (win.snapState == SnapState.NONE) win.y else win.restoreY
                val restoreW = if (win.snapState == SnapState.NONE) win.width else win.restoreWidth
                val restoreH = if (win.snapState == SnapState.NONE) win.height else win.restoreHeight

                when (snapState) {
                    SnapState.NONE -> win.copy(
                        x = restoreX, y = restoreY, width = restoreW, height = restoreH,
                        isMaximized = false, snapState = SnapState.NONE
                    )
                    SnapState.LEFT_HALF -> win.copy(
                        x = 0.dp, y = 0.dp, width = halfW, height = desktopHeight,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.RIGHT_HALF -> win.copy(
                        x = halfW, y = 0.dp, width = halfW, height = desktopHeight,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.TOP_LEFT -> win.copy(
                        x = 0.dp, y = 0.dp, width = halfW, height = halfH,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.TOP_RIGHT -> win.copy(
                        x = halfW, y = 0.dp, width = halfW, height = halfH,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.BOTTOM_LEFT -> win.copy(
                        x = 0.dp, y = halfH, width = halfW, height = halfH,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.BOTTOM_RIGHT -> win.copy(
                        x = halfW, y = halfH, width = halfW, height = halfH,
                        isMaximized = false, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                    SnapState.MAXIMIZE -> win.copy(
                        x = 0.dp, y = 0.dp, width = desktopWidth, height = desktopHeight,
                        isMaximized = true, snapState = snapState,
                        restoreX = restoreX, restoreY = restoreY, restoreWidth = restoreW, restoreHeight = restoreH
                    )
                }
            } else win
        }
    }

    fun toggleMaximize(id: String, desktopWidth: Dp, desktopHeight: Dp) {
        val win = _windows.value.firstOrNull { it.id == id } ?: return
        if (win.isMaximized) {
            snapWindow(id, SnapState.NONE, desktopWidth, desktopHeight)
        } else {
            snapWindow(id, SnapState.MAXIMIZE, desktopWidth, desktopHeight)
        }
    }

    fun toggleShade(id: String) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id) win.copy(isShaded = !win.isShaded) else win
        }
    }

    fun toggleMinimize(id: String) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id) win.copy(isMinimized = !win.isMinimized) else win
        }
    }

    fun togglePin(id: String) {
        val current = _windows.value
        val maxZ = (current.maxOfOrNull { it.zIndex } ?: 0f) + 1000f
        _windows.value = current.map { win ->
            if (win.id == id) {
                val newPinned = !win.isPinned
                win.copy(
                    isPinned = newPinned,
                    zIndex = if (newPinned) maxZ else (win.zIndex % 1000f)
                )
            } else win
        }
    }

    fun setOpacity(id: String, opacity: Float) {
        _windows.value = _windows.value.map { win ->
            if (win.id == id) win.copy(opacity = opacity.coerceIn(0.2f, 1.0f)) else win
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
