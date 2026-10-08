package de.mm20.launcher2.ui.downloads

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.downloads.logic.LinkParser
import de.mm20.launcher2.ui.R

/**
 * "Telos Downloads" in the share menu (text) and for opening download links (http or https links
 * of files) from other apps. It only collects the links and opens the add sheet; nothing is downloaded
 * before the user confirms. Magnet links and .torrent files stay with Telos Video until torrents come to Downloads.
 */
class DownloadShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
        val links = LinkParser.extractHttp(text)
        if (links.isEmpty()) {
            Toast.makeText(this, R.string.dl_invalid_links, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(
                Intent().setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                    .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_DOWNLOADS)
                    .putStringArrayListExtra(SettingsDeepLinkContract.EXTRA_DOWNLOAD_URLS, ArrayList(links))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }
}
