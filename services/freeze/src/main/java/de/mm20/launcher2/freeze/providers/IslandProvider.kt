package de.mm20.launcher2.freeze.providers

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.freeze.R

/**
 * Freezes/unfreezes apps through Island (com.oasisfeng.island), using its documented public
 * intent API (`com.oasisfeng.island.action.FREEZE` / `UNFREEZE` with a `packages:` data URI).
 *
 * Important limitations:
 * - This only works for apps the user has already cloned/moved into their Island profile.
 * - Island's API is activity-based, so we cannot observe the result synchronously. The methods
 *   here report whether Island was able to *receive* the request (installed, handles the action),
 *   not whether the app actually ended up frozen. We deliberately do NOT report blanket success.
 * - Android blocks starting an Activity from a background process (no visible UI) since API 29,
 *   silently - no exception, the launch is just dropped. [setPackagesSuspended] only works when
 *   called with a foreground context (a button tap in the app). For AutoFreezeController's
 *   screen-off/idle/battery-saver triggers, which run with no foreground activity, use
 *   [postFreezeNotification] instead: a user's tap on a notification IS exempt from that
 *   restriction, so freezing via Island from a background trigger can only ever be "tap this
 *   notification to freeze now", never fully silent - that's an Android + Island platform
 *   limitation, not something fixable in this app.
 */
internal class IslandProvider(private val context: Context) {

    fun isAvailable(): Boolean {
        return try {
            context.packageManager.getPackageInfo(ISLAND_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    fun setPackagesSuspended(packageNames: List<String>, suspended: Boolean, userId: Int = Process.myUid() / 100000): Boolean {
        if (packageNames.isEmpty()) return true
        val intent = buildFreezeIntent(packageNames, suspended) ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Island freeze intent failed", e)
            false
        }
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    /**
     * Posts a notification that freezes [packageNames] via Island when tapped - the only way to
     * trigger Island's activity-based freeze from a background context (see class doc). Replaces
     * any previously posted freeze notification (same id) rather than stacking.
     */
    // The checkSelfPermission(POST_NOTIFICATIONS) guard below is a textbook-correct runtime
    // check right before the only notify() call in this function, but this version of Lint's
    // guard detection for NotificationManagerCompat.notify() doesn't recognize it - known lint
    // limitation, not a missing check.
    @SuppressLint("MissingPermission")
    fun postFreezeNotification(packageNames: List<String>) {
        if (packageNames.isEmpty()) return
        val intent = buildFreezeIntent(packageNames, suspended = true) ?: return

        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.freeze_island_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ac_unit_24px)
            .setContentTitle(context.getString(R.string.freeze_island_notification_title))
            .setContentText(
                context.getString(R.string.freeze_island_notification_text, packageNames.size)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // areNotificationsEnabled() covers POST_NOTIFICATIONS on API 33+ and the per-app switch below it
        // (checkSelfPermission(POST_NOTIFICATIONS) reports "denied" on API 26-32 even when notifications work).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } else {
            // Not granted - degrade silently, same as every other best-effort privileged
            // operation in this module.
            Log.w(TAG, "Cannot post Island freeze notification, POST_NOTIFICATIONS not granted")
        }
    }

    private fun buildFreezeIntent(packageNames: List<String>, suspended: Boolean): Intent? {
        val action = if (suspended) ACTION_FREEZE else ACTION_UNFREEZE
        val intent = Intent(action).apply {
            data = Uri.parse("packages:" + packageNames.joinToString(","))
            `package` = ISLAND_PACKAGE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If Island doesn't handle the action (wrong/old version), don't claim success.
        if (intent.resolveActivity(context.packageManager) == null) return null
        return intent
    }

    companion object {
        private const val TAG = "IslandProvider"
        private const val ISLAND_PACKAGE = "com.oasisfeng.island"
        private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
        private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"
        private const val CHANNEL_ID = "freeze_island"
        private const val NOTIFICATION_ID = 4821
    }
}
