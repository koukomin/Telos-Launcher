package de.mm20.launcher2.ui.common

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/** Keyboard shortcut opens the separately installed app, or its source in Store. */
internal fun openExternalTermux(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.termux") ?: Intent().apply {
        setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
        putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STORE)
        putExtra(SettingsDeepLinkContract.EXTRA_STORE_URL, "https://github.com/termux/termux-app")
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
