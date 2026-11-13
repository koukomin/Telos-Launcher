package de.mm20.launcher2.ui.media.hub

/** A rail must leave enough readable width and height after insets and user font scaling. */
internal fun useMediaRail(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
    widthDp >= 840f * fontScale && heightDp >= 480f * fontScale
