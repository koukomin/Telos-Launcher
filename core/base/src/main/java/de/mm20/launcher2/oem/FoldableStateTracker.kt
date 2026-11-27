package de.mm20.launcher2.oem

import android.content.Context
import android.os.Build

/**
 * Foldable & Dual-Screen Continuity Tracker for Telos Launcher.
 * Detects Samsung Galaxy Z Fold/Flip, Pixel Fold, and OnePlus Open screen state transitions.
 */
object FoldableStateTracker {

    fun isFoldable(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("android.hardware.type.foldable") ||
            Build.MODEL.contains("fold", ignoreCase = true) ||
            Build.MODEL.contains("flip", ignoreCase = true)
    }

    fun isExpanded(widthDp: Int, heightDp: Int): Boolean {
        return widthDp >= 600 && widthDp > heightDp * 0.85f
    }
}
