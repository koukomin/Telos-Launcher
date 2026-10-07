package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.content.Context

class GlobalActionsService(private val context: Context) {
    fun openNotificationDrawer() {
        try {
            expandNotificationPanel()
        } catch (e: Exception) {
            LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun lockScreen() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    /** Locks the screen through the accessibility service (Android 9+). Returns false if that is not possible. */
    fun lockScreenOrFalse(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return false
        return LauncherAccessibilityService.getInstance()
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }

    fun openQuickSettings() {
        try {
            expandQuickSettings()
        } catch (e: Exception) {
            LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    fun openPowerDialog() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    /**
     * Takes a screenshot through the accessibility service (Android 9+).
     * Returns false if the accessibility service is not running or the system refused.
     */
    fun takeScreenshot(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return false
        return LauncherAccessibilityService.getInstance()
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
    }

    /**
     * Takes a screenshot and hands the picture to [callback] (Android 11+), or null when the
     * accessibility service is not running or the system refused. Used by Telos Screenshot, which
     * edits and saves the picture itself.
     */
    fun takeScreenshotBitmap(callback: (android.graphics.Bitmap?) -> Unit) {
        val service = LauncherAccessibilityService.getInstance()
        if (service == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        service.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            context.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = try {
                        android.graphics.Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) {
                        null
                    } finally {
                        buffer.close()
                    }
                    callback(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    callback(null)
                }
            },
        )
    }

    /** True if the accessibility service is running, which Telos Screenshot needs */
    fun isAccessibilityRunning(): Boolean = LauncherAccessibilityService.getInstance() != null

    /**
     * Swipes up on the screen to scroll the content down by about [fraction] of the screen
     * height. Calls [done] with whether the gesture was carried out.
     */
    fun swipeUp(fraction: Float, done: (Boolean) -> Unit) {
        val service = LauncherAccessibilityService.getInstance()
        if (service == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            done(false)
            return
        }
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.75f
        val endY = startY - metrics.heightPixels * fraction
        val path = android.graphics.Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        val sent = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) = done(true)
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) = done(false)
            },
            null,
        )
        if (!sent) done(false)
    }

    fun openRecents() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    private fun expandNotificationPanel() {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    }

    private fun expandQuickSettings() {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandSettingsPanel")
        method.invoke(statusBarService)
    }

}