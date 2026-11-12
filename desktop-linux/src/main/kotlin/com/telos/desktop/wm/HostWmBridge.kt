package com.telos.desktop.wm

import com.telos.desktop.linux.Sys
import com.telos.desktop.session.SessionCompositor

/**
 * Best-effort host window manager bridge for Helix snap layouts.
 *
 * Prefer compositor-native APIs when present:
 * - **niri:** `niri msg action …`
 * - **labwc / stacking:** `wmctrl` geometry when XWayland is available
 *
 * Output size for absolute geometry uses `wlr-randr` when present (same tool as
 * [com.telos.desktop.display.LinuxDisplayManager]). Compose-managed windows are
 * always updated by [TelosWindowManager.applySnapLayout]; this bridge only
 * attempts to move the focused *host* toplevel.
 *
 * Hot-swapping labwc ↔ niri mid-session is not supported; the tiling/stacking
 * preference in `desktop.properties` applies on the next `telos-session` start.
 */
object HostWmBridge {
    data class Result(val ok: Boolean, val backend: String, val detail: String = "")

    fun activeCompositor(): String {
        val env = System.getenv("TELOS_COMPOSITOR")?.trim()?.lowercase().orEmpty()
        if (env == SessionCompositor.Niri || env == SessionCompositor.Labwc) return env
        val desk = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().lowercase()
        if (desk.contains("niri")) return SessionCompositor.Niri
        if (desk.contains("labwc")) return SessionCompositor.Labwc
        return SessionCompositor.resolve(env.ifBlank { null }, Sys.which("labwc") != null, Sys.which("niri") != null)
    }

    fun applySnapLayout(layout: SnapLayout, area: WorkArea): Result {
        val compositor = activeCompositor()
        return when (compositor) {
            SessionCompositor.Niri -> applyNiri(layout) ?: applyWmctrl(layout, area)
                .copy(detail = "niri msg unavailable; tried wmctrl")
            else -> applyWmctrl(layout, area)
        }
    }

    private fun applyNiri(layout: SnapLayout): Result? {
        if (Sys.which("niri") == null) return null
        val actions = niriActions(layout) ?: return Result(false, SessionCompositor.Niri, "unsupported layout")
        var last = Result(false, SessionCompositor.Niri, "no action")
        for (action in actions) {
            val r = Sys.run("niri", "msg", "action", *action, timeoutSec = 3)
            last = Result(r.ok, SessionCompositor.Niri, r.stdout.trim().ifBlank { action.joinToString(" ") })
            if (!r.ok) return last
        }
        return last
    }

    /**
     * niri column width / consume actions approximating Helix snap zones.
     * Exact 2×2 grid placement is best-effort (consume into column + height).
     */
    internal fun niriActions(layout: SnapLayout): List<Array<String>>? = when (layout) {
        SnapLayout.HalfLeft, SnapLayout.HalfRight -> listOf(arrayOf("set-window-width", "50%"))
        SnapLayout.TwoThirdsLeft, SnapLayout.TwoThirdsRight -> listOf(arrayOf("set-window-width", "66%"))
        SnapLayout.OneThirdLeft, SnapLayout.OneThirdRight -> listOf(arrayOf("set-window-width", "33%"))
        SnapLayout.Maximize -> listOf(arrayOf("maximize-column"))
        SnapLayout.Restore -> listOf(arrayOf("set-window-width", "50%"))
        SnapLayout.GridTopLeft, SnapLayout.GridTopRight,
        SnapLayout.GridBottomLeft, SnapLayout.GridBottomRight ->
            listOf(arrayOf("set-window-width", "50%"), arrayOf("set-window-height", "50%"))
    }

    private fun applyWmctrl(layout: SnapLayout, area: WorkArea): Result {
        if (Sys.which("wmctrl") == null) {
            return Result(
                false,
                "wmctrl",
                "wmctrl not on PATH; Compose snap applied only. Install wmctrl for host XWayland windows.",
            )
        }
        val rect = SnapLayoutGeometry.rect(layout, area)
        // wmctrl -e gravity,x,y,w,h  (gravity 0 = northwest)
        val r = Sys.run(
            "wmctrl",
            "-r", ":ACTIVE:",
            "-e", "0,${rect.x.toInt()},${rect.y.toInt()},${rect.width.toInt()},${rect.height.toInt()}",
            timeoutSec = 3,
        )
        return Result(r.ok, "wmctrl", r.stdout.trim())
    }
}
