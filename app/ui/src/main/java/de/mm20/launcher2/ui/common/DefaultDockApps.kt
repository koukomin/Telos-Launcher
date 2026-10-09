package de.mm20.launcher2.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves the device's default handler apps (dialer, SMS, browser, gallery, media player) via
 * PackageManager - the standard way to find "the phone's default X app" without hardcoding
 * package names, since a launcher has no access to another launcher's saved home screen layout.
 * Each resolved app is upserted into the searchable DB so it can be placed in the dock.
 */
suspend fun resolveDefaultSystemApps(
    context: Context,
    appRepository: AppRepository,
    searchableRepository: SavableSearchableRepository,
    maxCount: Int,
): List<DockItem> {
    // On a fresh cold start (first launch, or right after clearing app data) the repository's
    // own package scan hasn't finished yet - findOne() below would silently return null for
    // every candidate until it does. Wait for at least one app to show up first.
    appRepository.findMany().first { it.isNotEmpty() }

    val intents = listOf(
        Intent(Intent.ACTION_DIAL), // Dialer
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), // SMS
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), // Browser
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), // Image Viewer
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC), // Media Player
    )

    val pm = context.packageManager
    val items = mutableListOf<DockItem>()

    for (intent in intents) {
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo != null) {
            val pkg = resolveInfo.activityInfo.packageName
            // Use the app's own key from the repository, not one built from the resolved
            // intent's activity component - that's frequently a different activity (e.g. a
            // dialer/gallery intent handler) than the app's actual launcher activity, which is
            // what the repository indexes under and what upsert() below stores. A hand-rolled
            // key here would silently point at a searchable that's never actually in the DB.
            val app = appRepository.findOne(pkg, Process.myUserHandle()).first()
            // The same app can handle several of the intents (e.g. a messaging app that also dials)
            if (app != null && items.none { it is DockItem.Searchable && it.key == app.key }) {
                searchableRepository.upsert(app)
                items.add(DockItem.Searchable(app.key))
            }
        }
        if (items.size >= maxCount) break
    }

    return items
}
