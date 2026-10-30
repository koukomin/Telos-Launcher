package com.telos.desktop.wm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

// === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
class ManagedWindow(
    val id: Long,
    val title: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    z: Int,
    shaded: Boolean = false,
    alwaysOnTop: Boolean = false,
    opacity: Float = 1f,
    maximized: Boolean = false,
    snap: SnapZone = SnapZone.None,
    workspace: Int = 0,
    minimized: Boolean = false,
    restoreX: Float = x,
    restoreY: Float = y,
    restoreW: Float = width,
    restoreH: Float = height,
) {
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var z by mutableIntStateOf(z)
    var shaded by mutableStateOf(shaded)
    var alwaysOnTop by mutableStateOf(alwaysOnTop)
    var opacity by mutableStateOf(opacity)
    var maximized by mutableStateOf(maximized)
    var snap by mutableStateOf(snap)
    var workspace by mutableIntStateOf(workspace)
    var minimized by mutableStateOf(minimized)
    var restoreX by mutableStateOf(restoreX)
    var restoreY by mutableStateOf(restoreY)
    var restoreW by mutableStateOf(restoreW)
    var restoreH by mutableStateOf(restoreH)
}
// === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===

enum class ResizeEdge { None, N, S, E, W, NE, NW, SE, SW }

class TelosWindowManager {
    private var nextId = 1L
    private var nextZ = 1
    val windows = mutableStateListOf<ManagedWindow>()
    var focusedId by mutableStateOf<Long?>(null)
        private set
    var decoration by mutableStateOf(WindowDecorationTheme())

    // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
    var workArea by mutableStateOf(WorkArea.of(1920f, 1080f))
    var currentWorkspace by mutableIntStateOf(0)
    val workspaceCount = 4

    fun setScreen(width: Float, height: Float, topBar: Float = WorkArea.DEFAULT_TOP_BAR_PX, dock: Float = WorkArea.DEFAULT_DOCK_PX, dockVisible: Boolean = true) {
        workArea = WorkArea.of(width, height, topBar, dock, dockVisible)
        windows.filter { it.maximized || it.snap != SnapZone.None }.forEach { applySnap(it, it.snap.let { z -> if (it.maximized) SnapZone.Maximize else z }) }
    }

    fun constrainToWorkArea(w: ManagedWindow) {
        val area = workArea
        w.y = w.y.coerceAtLeast(area.minY)
        if (w.y + 36f > area.maxY) w.y = (area.maxY - 36f).coerceAtLeast(area.minY)
        w.x = w.x.coerceIn(area.minX - w.width + 80f, area.maxX - 80f)
    }
    // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===

    fun open(title: String, x: Float = 120f, y: Float = 80f, w: Float = 640f, h: Float = 420f): ManagedWindow {
        val existing = windows.find { it.title == title }
        if (existing != null) {
            focus(existing.id)
            return existing
        }
        val area = workArea
        val win = ManagedWindow(
            id = nextId++,
            title = title,
            x = x.coerceAtLeast(area.minX),
            y = y.coerceAtLeast(area.minY),
            width = w,
            height = h.coerceAtMost(area.height),
            z = nextZ++,
            workspace = currentWorkspace,
        )
        windows += win
        focusedId = win.id
        return win
    }

    fun close(id: Long) {
        windows.removeAll { it.id == id }
        if (focusedId == id) focusedId = visibleWindows().maxByOrNull { it.z }?.id
    }

    fun visibleWindows(): List<ManagedWindow> = windows.filter { it.workspace == currentWorkspace && !it.minimized }

    // === TELOS_PENDING_REVIEW_START: telos_desktop_kwin_parity_upgrade ===
    fun minimize(id: Long) {
        val w = windows.find { it.id == id } ?: return
        w.minimized = true
        bump(w)
        if (focusedId == id) focusedId = visibleWindows().maxByOrNull { it.z }?.id
    }

    fun restore(id: Long) {
        val w = windows.find { it.id == id } ?: return
        w.minimized = false
        w.maximized = false
        w.snap = SnapZone.None
        w.x = w.restoreX
        w.y = w.restoreY.coerceAtLeast(workArea.minY)
        w.width = w.restoreW
        w.height = w.restoreH
        bump(w)
        focus(id)
    }

    fun toggleMaximize(id: Long) {
        val w = windows.find { it.id == id } ?: return
        if (w.maximized) restore(id) else {
            w.restoreX = w.x; w.restoreY = w.y; w.restoreW = w.width; w.restoreH = w.height
            applySnap(w, SnapZone.Maximize)
            bump(w)
        }
    }

    fun endDragSnap(id: Long) {
        val w = windows.find { it.id == id } ?: return
        val a = workArea
        val zone = when {
            w.y <= a.minY + 12f -> SnapZone.Maximize
            w.x <= a.minX + 12f && w.y <= a.minY + a.height / 3 -> SnapZone.TopLeft
            w.x + w.width >= a.maxX - 12f && w.y <= a.minY + a.height / 3 -> SnapZone.TopRight
            w.x <= a.minX + 12f && w.y + w.height >= a.maxY - 12f -> SnapZone.BottomLeft
            w.x + w.width >= a.maxX - 12f && w.y + w.height >= a.maxY - 12f -> SnapZone.BottomRight
            w.x <= a.minX + 12f -> SnapZone.Left
            w.x + w.width >= a.maxX - 12f -> SnapZone.Right
            else -> null
        }
        if (zone != null) {
            if (!w.maximized) {
                w.restoreX = w.x; w.restoreY = w.y; w.restoreW = w.width; w.restoreH = w.height
            }
            applySnap(w, zone)
            bump(w)
        }
    }

    fun moveToWorkspace(id: Long, index: Int) {
        val w = windows.find { it.id == id } ?: return
        w.workspace = index.coerceIn(0, workspaceCount - 1)
        bump(w)
    }
    // === TELOS_PENDING_REVIEW_END: telos_desktop_kwin_parity_upgrade ===

    fun focus(id: Long) {
        val w = windows.find { it.id == id } ?: return
        currentWorkspace = w.workspace
        w.z = nextZ++
        if (w.alwaysOnTop) w.z = nextZ++
        focusedId = id
        windows.sortBy { it.z }
    }

    fun cycleFocus(forward: Boolean = true) {
        val vis = visibleWindows().sortedBy { it.z }
        if (vis.isEmpty()) return
        val idx = vis.indexOfFirst { it.id == focusedId }.let { if (it < 0) 0 else it }
        val next = if (forward) (idx + 1) % vis.size else (idx - 1 + vis.size) % vis.size
        focus(vis[next].id)
    }

    fun drag(id: Long, delta: Offset, bounds: IntSize) {
        val w = windows.find { it.id == id } ?: return
        if (w.maximized) {
            w.maximized = false
            w.snap = SnapZone.None
            w.width = w.restoreW
            w.height = w.restoreH
        }
        w.x += delta.x
        w.y += delta.y
        // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
        constrainToWorkArea(w)
        // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===
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
        // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
        y = y.coerceAtLeast(workArea.minY)
        if (y + height > workArea.maxY) height = (workArea.maxY - y).coerceAtLeast(160f)
        // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===
        w.x = x
        w.y = y
        w.width = width
        w.height = height
        bump(w)
    }

    fun aeroSnap(id: Long, screen: Size, zone: SnapZone) {
        val w = windows.find { it.id == id } ?: return
        applySnap(w, zone)
        bump(w)
    }

    // === TELOS_PENDING_REVIEW_START: telos_kde_replacement_suite ===
    private fun applySnap(w: ManagedWindow, zone: SnapZone) {
        val a = workArea
        w.snap = zone
        w.maximized = zone == SnapZone.Maximize
        when (zone) {
            SnapZone.None, SnapZone.Restore -> {
                w.maximized = false
                w.snap = SnapZone.None
                w.y = w.y.coerceAtLeast(a.minY)
            }
            SnapZone.Maximize -> {
                w.x = a.minX
                w.y = a.minY
                w.width = a.width
                w.height = a.height
            }
            SnapZone.Left -> {
                w.x = a.minX; w.y = a.minY; w.width = a.width / 2f; w.height = a.height
            }
            SnapZone.Right -> {
                w.x = a.minX + a.width / 2f; w.y = a.minY; w.width = a.width / 2f; w.height = a.height
            }
            SnapZone.TopLeft -> {
                w.x = a.minX; w.y = a.minY; w.width = a.width / 2f; w.height = a.height / 2f
            }
            SnapZone.TopRight -> {
                w.x = a.minX + a.width / 2f; w.y = a.minY; w.width = a.width / 2f; w.height = a.height / 2f
            }
            SnapZone.BottomLeft -> {
                w.x = a.minX; w.y = a.minY + a.height / 2f; w.width = a.width / 2f; w.height = a.height / 2f
            }
            SnapZone.BottomRight -> {
                w.x = a.minX + a.width / 2f; w.y = a.minY + a.height / 2f; w.width = a.width / 2f; w.height = a.height / 2f
            }
        }
    }
    // === TELOS_PENDING_REVIEW_END: telos_kde_replacement_suite ===

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

    fun switchWorkspace(index: Int) {
        currentWorkspace = index.coerceIn(0, workspaceCount - 1)
        focusedId = visibleWindows().maxByOrNull { it.z }?.id
    }

    // === TELOS_PENDING_REVIEW_START: telos_window_drag_and_ui_fix ===
    private fun bump(w: ManagedWindow) {
        // Snapshot-backed fields already notify Compose; keep hook for focus/z sorts.
        w.z = w.z
    }
    // === TELOS_PENDING_REVIEW_END: telos_window_drag_and_ui_fix ===
}

enum class SnapZone { None, Left, Right, Maximize, Restore, TopLeft, TopRight, BottomLeft, BottomRight }
