package de.mm20.launcher2.ktx

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Single Source of Truth for Android System Intents used across Telos Launcher.
 */
object TelosIntents {

    /**
     * Intent to open the Application Details settings page for a specific package name.
     */
    fun appDetails(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent to open the Display Over Other Apps / Overlay Permission settings page.
     */
    fun overlayPermission(packageName: String): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent to open Notification Listener / Access settings.
     */
    fun notificationListenerSettings(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent to open Battery Saver / Optimization settings.
     */
    fun batteryOptimizationSettings(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Intent to open Accessibility settings.
     */
    fun accessibilitySettings(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Safely launches system app details for a given package name.
     */
    fun launchAppDetails(context: Context, packageName: String): Boolean {
        return context.tryStartActivity(appDetails(packageName))
    }
}
