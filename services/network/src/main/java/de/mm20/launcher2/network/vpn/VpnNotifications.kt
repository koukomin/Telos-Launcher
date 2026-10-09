package de.mm20.launcher2.network.vpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.network.R
import de.mm20.launcher2.network.api.NetworkSettingsValues
import de.mm20.launcher2.network.api.NotificationDetail

/** The notifications of Telos Network: the ongoing one while the VPN runs, and the alert when it stops by itself. */
internal object VpnNotifications {
    const val CHANNEL_SERVICE = "telos_network_service"
    const val CHANNEL_ALERTS = "telos_network_alerts"
    const val ID_SERVICE = 7101
    const val ID_ALERT = 7102

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager(context)
        if (nm.getNotificationChannel(CHANNEL_SERVICE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    context.getString(I18nR.string.net_channel_service),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(I18nR.string.net_channel_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    private fun openApp(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The "Turn off" action: a PendingIntent that tells the service to stop. */
    private fun stopAction(context: Context): PendingIntent {
        val intent = Intent(context, TelosVpnService::class.java).setAction(TelosVpnService.ACTION_STOP)
        return PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The foreground notification. [blocked] is only shown with [NotificationDetail.WithCounters]. */
    fun service(context: Context, settings: NetworkSettingsValues, blocked: Long): Notification {
        ensureChannels(context)
        val text = if (settings.notificationDetail == NotificationDetail.WithCounters) {
            context.getString(I18nR.string.net_notif_blocked, blocked)
        } else {
            context.getString(I18nR.string.net_notif_text)
        }
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_network_notification)
            .setContentTitle(context.getString(I18nR.string.net_notif_title))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .addAction(0, context.getString(I18nR.string.net_notif_stop), stopAction(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(
                if (settings.notificationHideOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .build()
    }

    /** Tells the user that the VPN stopped by itself and that the internet works normally. */
    fun alert(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_network_notification)
            .setContentTitle(context.getString(I18nR.string.net_alert_stopped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager(context).notify(ID_ALERT, notification)
        } catch (e: SecurityException) {
            // notifications are not allowed
        }
    }

    fun cancelAlert(context: Context) {
        manager(context).cancel(ID_ALERT)
    }
}
