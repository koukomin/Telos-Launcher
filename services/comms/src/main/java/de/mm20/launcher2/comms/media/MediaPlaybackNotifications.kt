package de.mm20.launcher2.comms.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/** Services share a package, so their notifications must not replace/cancel one another. */
internal object MediaPlaybackNotifications {
    const val MUSIC = 7401
    const val RADIO = 7402
    const val TV = 7403
    const val YOUTUBE = 7404

    fun open(context: Context, space: String, requestCode: Int): PendingIntent {
        val intent = Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MEDIA)
            .putExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE, space)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
