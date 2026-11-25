package de.mm20.launcher2.ktx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralized Single Source of Truth for Telos Launcher Notification Channels (Android 8.0+ / API 26+).
 */
object TelosNotificationChannels {

    const val CHANNEL_STORE_UPDATES = "telos_store_updates"
    const val CHANNEL_SCREEN_RECORDER = "telos_screen_recorder"
    const val CHANNEL_CALL_RECORDING = "telos_call_recording"
    const val CHANNEL_BATTERY_ALERTS = "telos_battery_alerts"
    const val CHANNEL_BACKUP_PROGRESS = "telos_backup_progress"

    /**
     * Registers all Telos Launcher system notification channels with the NotificationManager.
     */
    fun registerNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_STORE_UPDATES,
                "Store Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Telos Store app updates"
            },
            NotificationChannel(
                CHANNEL_SCREEN_RECORDER,
                "Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen recording status"
            },
            NotificationChannel(
                CHANNEL_CALL_RECORDING,
                "Call Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active call recording status"
            },
            NotificationChannel(
                CHANNEL_BATTERY_ALERTS,
                "Battery Health & Thermal Guard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for device battery temperature and charging alerts"
            },
            NotificationChannel(
                CHANNEL_BACKUP_PROGRESS,
                "Backup & Restore",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Progress notifications for launcher backups"
            }
        )

        manager.createNotificationChannels(channels)
    }
}
