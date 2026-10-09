package de.mm20.launcher2.applock

/** Cross-module contract for opening Settings directly to a specific App Lock screen from a
 * lower module (e.g. a notification's PendingIntent) that can't depend on :app:ui directly -
 * mirrors [de.mm20.launcher2.webapp.WebAppLaunchContract]'s shape. */
object SettingsDeepLinkContract {
    const val ACTIVITY_CLASS_NAME = "de.mm20.launcher2.ui.settings.SettingsActivity"
    const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
    const val ROUTE_INTRUDER_PHOTOS = "settings/applock/intruderphotos"
    /** Opens the Store dashboard directly - used by the virtual "Telos Store" app drawer entry. */
    const val ROUTE_STORE = "settings/store"
    // === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
    const val ROUTE_COMMS = "settings/comms"
    const val EXTRA_COMMS_TAB = "de.mm20.launcher2.settings.COMMS_TAB"
    const val EXTRA_DIAL_NUMBER = "de.mm20.launcher2.settings.DIAL_NUMBER"
    /** Text to put in the message field of Telos Messages */
    const val EXTRA_SMS_BODY = "de.mm20.launcher2.settings.SMS_BODY"
    /** Pictures or videos (content addresses) to attach to a message in Telos Messages */
    const val EXTRA_SMS_ATTACHMENTS = "de.mm20.launcher2.settings.SMS_ATTACHMENTS"
    const val ROUTE_RADIO = "settings/radio"
    const val ROUTE_MUSIC = "settings/music"
    const val ROUTE_VIDEO = "settings/video"
    /** An address or an obtainium:// link to add in the Store */
    const val EXTRA_STORE_URL = "de.mm20.launcher2.settings.STORE_URL"
    const val ROUTE_PHOTOS = "settings/photos"
    const val ROUTE_FILES = "settings/files"
    const val ROUTE_CALCULATOR = "settings/calculator"
    const val ROUTE_VOICE_RECORDER = "settings/voice_recorder"
    const val ROUTE_SCREEN_RECORDER = "settings/screen_recorder"
    const val ROUTE_SCREENSHOT = "settings/screenshot"
    const val ROUTE_NOTES = "settings/notes"
    const val ROUTE_CALENDAR = "settings/calendar"
    const val ROUTE_DOWNLOADS = "settings/downloads"
    const val ROUTE_NETWORK = "settings/network"
    /** Links (string array) to offer in the add sheet of Telos Downloads, from a share or an Open with */
    const val EXTRA_DOWNLOAD_URLS = "de.mm20.launcher2.settings.DOWNLOAD_URLS"
    // === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
}
