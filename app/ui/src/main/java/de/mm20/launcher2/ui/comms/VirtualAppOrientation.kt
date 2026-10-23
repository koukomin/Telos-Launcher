package de.mm20.launcher2.ui.comms

import android.app.Activity
import android.content.pm.ActivityInfo

/** All virtual apps follow the user's display orientation / sensor fluidly. */
fun Activity.applyVirtualAppOrientation(
    route: String?,
    commsTab: String?,
    allowDialerLandscape: Boolean,
) {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
}
