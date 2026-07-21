package de.mm20.launcher2.applock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts a notification on a failed App Lock authentication attempt - independent of whether
 * intruder photo capture itself is enabled, though in practice the two are normally turned on
 * together. Tapping the notification (or its "show more" action) opens the captured-photos
 * gallery via [SettingsDeepLinkContract], since this module can't depend on :app:ui directly.
 */
class IntruderPhotoNotifier(private val context: Context) {
    fun notifyFailedUnlock(appLabel: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel()

        val openGallery = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_INTRUDER_PHOTOS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.lock_24px)
            .setContentTitle(context.getString(R.string.intruder_photo_notification_title))
            .setContentText(
                context.getString(R.string.intruder_photo_notification_text, appLabel)
            )
            .setContentIntent(openGallery)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.intruder_photo_notification_show_more), openGallery)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check above and this call.
        }
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.intruder_photo_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    private companion object {
        private const val CHANNEL_ID = "intruder_photo"
        private const val NOTIFICATION_ID = 4824
    }
}
