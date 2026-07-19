package de.mm20.launcher2.webapp

/**
 * Cross-module contract for launching the embedded WebView activity (lives in app:ui) from a
 * WebAppShortcut's launch() (lives in data:webappshortcuts, which cannot depend on app:ui) -
 * launched via an implicit intent using this class name, not a compile-time reference.
 */
object WebAppLaunchContract {
    const val ACTIVITY_CLASS_NAME = "de.mm20.launcher2.ui.webapp.WebAppActivity"
    const val EXTRA_URL = "de.mm20.launcher2.webapp.extra.URL"
    const val EXTRA_LABEL = "de.mm20.launcher2.webapp.extra.LABEL"
}
