package de.mm20.launcher2.ui.help

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.applock.SettingsDeepLinkContract

internal fun openHelpRoute(context: Context, route: String, commsTab: String? = null) {
    val intent = Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
        .setPackage(context.packageName)
        .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (commsTab != null) {
        intent.putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, commsTab)
    }
    runCatching { context.startActivity(intent) }
}

internal fun openHelpApp(context: Context, topicId: String) {
    val intent = Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
        .setPackage(context.packageName)
        .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_TELOS_PAGE)
        .putExtra(SettingsDeepLinkContract.EXTRA_HELP_TOPIC, topicId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
