package de.mm20.launcher2.webapp

/**
 * Cross-module contract for launching the web app lock gate (lives in app:ui) from a
 * WebAppShortcut's launch() (lives in data:webappshortcuts, which cannot depend on app:ui) -
 * launched via an implicit intent using this class name, not a compile-time reference. Mirrors
 * [WebAppLaunchContract], which the gate itself uses on success to actually open the shortcut.
 */
object WebAppLockLaunchContract {
    const val ACTIVITY_CLASS_NAME = "de.mm20.launcher2.ui.applock.WebAppLockActivity"
    const val EXTRA_URL = "de.mm20.launcher2.webapp.extra.URL"
    const val EXTRA_LABEL = "de.mm20.launcher2.webapp.extra.LABEL"
    const val EXTRA_RENDERER_PACKAGE = "de.mm20.launcher2.webapp.extra.RENDERER_PACKAGE"
}
