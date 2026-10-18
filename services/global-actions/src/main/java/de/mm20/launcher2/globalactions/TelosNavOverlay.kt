package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Thin edge strips over other apps. Off unless the user enables Telos navigation.
 * Swipe in from a side: back. Swipe up from the bottom: home. Hold then release: recents.
 */
class TelosNavOverlay(
    private val service: AccessibilityService,
    private val onRecents: () -> Unit,
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableListOf<View>()
    private var attached = false

    fun setEnabled(enabled: Boolean) {
        if (enabled) attach() else detach()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        if (attached) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        val density = service.resources.displayMetrics.density
        val strip = (24 * density).toInt()
        addStrip(Gravity.LEFT or Gravity.FILL_VERTICAL, strip, WindowManager.LayoutParams.MATCH_PARENT) { dx, dy, held ->
            if (dx > 48 * density) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        addStrip(Gravity.RIGHT or Gravity.FILL_VERTICAL, strip, WindowManager.LayoutParams.MATCH_PARENT) { dx, dy, held ->
            if (dx < -48 * density) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        addStrip(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, WindowManager.LayoutParams.MATCH_PARENT, strip) { dx, dy, held ->
            if (held) onRecents()
            else if (dy < -48 * density) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        attached = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip(
        gravity: Int,
        width: Int,
        height: Int,
        onSwipe: (dx: Float, dy: Float, held: Boolean) -> Unit,
    ) {
        val view = FrameLayout(service)
        var startX = 0f
        var startY = 0f
        var downAt = 0L
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val held = System.currentTimeMillis() - downAt > 400 && abs(dx) < 40 && abs(dy) < 40
                    onSwipe(dx, dy, held)
                    true
                }
                else -> true
            }
        }
        val type = if (Build.VERSION.SDK_INT >= 22)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
        }
        runCatching { wm.addView(view, lp) }.onSuccess { views.add(view) }
    }

    private fun detach() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        attached = false
    }
}
