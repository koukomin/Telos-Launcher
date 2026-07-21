package de.mm20.launcher2.applock

/** Cross-module contract for opening Settings directly to a specific App Lock screen from a
 * lower module (e.g. a notification's PendingIntent) that can't depend on :app:ui directly -
 * mirrors [de.mm20.launcher2.webapp.WebAppLaunchContract]'s shape. */
object SettingsDeepLinkContract {
    const val ACTIVITY_CLASS_NAME = "de.mm20.launcher2.ui.settings.SettingsActivity"
    const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
    const val ROUTE_INTRUDER_PHOTOS = "settings/applock/intruderphotos"
}
