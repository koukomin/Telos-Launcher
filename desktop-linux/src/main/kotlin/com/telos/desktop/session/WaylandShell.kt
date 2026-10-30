package com.telos.desktop.session

import com.telos.desktop.wm.WorkArea

/** W0 exclusive-zone sizes. Keep in sync with labwc rc.xml margin and niri struts. */
object WaylandShell {
    const val PANEL_PX = WorkArea.DEFAULT_TOP_BAR_PX.toInt()
    const val DOCK_PX = WorkArea.DEFAULT_DOCK_PX.toInt()
    const val NOTIFY_FIFO = "telos-notify.jsonl"
    const val PHASE = "W3"
    const val LAYER_SOCK = "telos-layer.sock"
}
