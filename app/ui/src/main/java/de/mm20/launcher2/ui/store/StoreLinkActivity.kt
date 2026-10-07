package de.mm20.launcher2.ui.store

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/**
 * Opens the Store for an obtainium:// link (obtainium://add/<address> or obtainium://app/<app>),
 * the links Obtainium uses to share apps, so a link from a web page or a friend adds the app here.
 */
class StoreLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.dataString.orEmpty()
        startActivity(
            Intent()
                .setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STORE)
                .putExtra(SettingsDeepLinkContract.EXTRA_STORE_URL, link)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
