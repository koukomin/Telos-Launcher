package de.mm20.launcher2.ui.comms

import android.app.Activity
import android.content.pm.ActivityInfo
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/** Phones lock the dialer to portrait unless the user allows rotation. Other virtual apps follow the sensor. */
fun Activity.applyVirtualAppOrientation(
    route: String?,
    commsTab: String?,
    allowDialerLandscape: Boolean,
) {
    val phone = resources.configuration.smallestScreenWidthDp < 600
    val dialer = route == SettingsDeepLinkContract.ROUTE_COMMS && commsTab != "messages"
    requestedOrientation = when {
        phone && dialer && !allowDialerLandscape -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_USER
    }
}
