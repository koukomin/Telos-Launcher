package com.telos.desktop.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

data class ManagedWindow(
    val id: Long,
    val title: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var z: Int,
    var shaded: Boolean = false,
    var alwaysOnTop: Boolean = false,
    var opacity: Float = 1f,
    var maximized: Boolean = false,
)

enum class ResizeEdge { None, N, S, E, W, NE, NW, SE, SW }

class TelosWindowManager {
    private var nextId = 1L
    private var nextZ = 1
    val windows = mutableStateListOf<ManagedWindow>()
    var focusedId by mutableStateOf<Long?>(null)
        private set
    var decoration by mutableStateOf(WindowDecorationTheme())

    fun open(title: String, x: Float = 120f, y: Float = 80f, w: Float = 640f, h: Float = 420f): ManagedWindow {
        val win = ManagedWindow(
            id = nextId++,
            title = title,
            x = x,
            y = y,
            width = w,
            height = h,
            z = nextZ++,
        )
        windows += win
        focusedId = win.id
        return win
    }

    fun close(id: Long) {
        windows.removeAll { it.id == id }
        if (focusedId == id) focusedId = windows.maxByOrNull { it.z }?.id
    }

    fun focus(id: Long) {
        val w = windows.find { it.id == id } ?: return
        w.z = nextZ++
        if (w.alwaysOnTop) w.z = nextZ++
        focusedId = id
        windows.sortBy { it.z }
    }

    fun drag(id: Long, delta: Offset, bounds: IntSize) {
        val w = windows.find { it.id == id } ?: return
        if (w.maximized) return
        w.x = (w.x + delta.x).coerceIn(-w.width + 80f, bounds.width - 80f)
        w.y = (w.y + delta.y).coerceIn(0f, bounds.height - 40f)
        bump(w)
    }

    fun resize(id: Long, edge: ResizeEdge, delta: Offset) {
        val w = windows.find { it.id == id } ?: return
        if (w.maximized || w.shaded) return
        var x = w.x
        var y = w.y
        var width = w.width
        var height = w.height
        when (edge) {
            ResizeEdge.E, ResizeEdge.NE, ResizeEdge.SE -> width = (width + delta.x).coerceAtLeast(240f)
            ResizeEdge.W, ResizeEdge.NW, ResizeEdge.SW -> {
                val dw = delta.x.coerceAtMost(width - 240f)
                x += dw
                width -= dw
            }
            else -> {}
        }
        when (edge) {
            ResizeEdge.S, ResizeEdge.SE, ResizeEdge.SW -> height = (height + delta.y).coerceAtLeast(160f)
            ResizeEdge.N, ResizeEdge.NE, ResizeEdge.NW -> {
                val dh = delta.y.coerceAtMost(height - 160f)
                y += dh
                height -= dh
            }
            else -> {}
        }
        w.x = x
        w.y = y
        w.width = width
        w.height = height
        bump(w)
    }

    fun aeroSnap(id: Long, screen: Size, zone: SnapZone) {
        val w = windows.find { it.id == id } ?: return
        when (zone) {
            SnapZone.Left -> {
                w.x = 0f; w.y = 0f; w.width = screen.width / 2f; w.height = screen.height; w.maximized = false
            }
            SnapZone.Right -> {
                w.x = screen.width / 2f; w.y = 0f; w.width = screen.width / 2f; w.height = screen.height; w.maximized = false
            }
            SnapZone.Maximize -> {
                w.x = 0f; w.y = 0f; w.width = screen.width; w.height = screen.height; w.maximized = true
            }
            SnapZone.Restore -> w.maximized = false
        }
        bump(w)
    }

    fun toggleShade(id: Long) {
        val w = windows.find { it.id == id } ?: return
        w.shaded = !w.shaded
        bump(w)
    }

    fun toggleAlwaysOnTop(id: Long) {
        val w = windows.find { it.id == id } ?: return
        w.alwaysOnTop = !w.alwaysOnTop
        bump(w)
    }

    fun setOpacity(id: Long, value: Float) {
        val w = windows.find { it.id == id } ?: return
        w.opacity = value.coerceIn(0.25f, 1f)
        bump(w)
    }

    private fun bump(w: ManagedWindow) {
        val i = windows.indexOfFirst { it.id == w.id }
        if (i >= 0) windows[i] = w.copy()
    }
}

enum class SnapZone { Left, Right, Maximize, Restore }
