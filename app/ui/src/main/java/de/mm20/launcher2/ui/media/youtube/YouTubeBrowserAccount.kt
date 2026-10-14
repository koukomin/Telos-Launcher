package de.mm20.launcher2.ui.media.youtube

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** Opens both websites in the same ordinary browser profile. Never imports browser cookies. */
internal object YouTubeBrowserAccount {
    fun open(context: Context, music: Boolean): Boolean = try {
        val provider = CustomTabsClient.getPackageName(context, null)
        if (provider == null) false else {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            // Pin the provider: otherwise Android may route a YouTube link to its native app.
            tab.intent.setPackage(provider)
            tab.launchUrl(context, Uri.parse(if (music) "https://music.youtube.com/" else "https://www.youtube.com/"))
            true
        }
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
