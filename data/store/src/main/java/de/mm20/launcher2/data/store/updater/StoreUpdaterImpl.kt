package de.mm20.launcher2.data.store.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.store.action.StoreAction
import de.mm20.launcher2.store.action.StoreActionHandler
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry
import de.mm20.launcher2.store.installer.AppInstaller
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.StoreOptions
import de.mm20.launcher2.store.repository.StoreRepository
import de.mm20.launcher2.store.updater.CheckSummary
import de.mm20.launcher2.store.updater.StoreUpdater

/**
 * The Store's update check, the way Obtainium does it: ask every source for its newest release,
 * remember the answer, and tell the user (and, when they allow it and Shizuku or root makes it
 * possible, install without asking).
 */
class StoreUpdaterImpl(
    private val context: Context,
    private val registry: StoreFetcherRegistry,
    private val repository: StoreRepository,
    private val options: StoreOptions,
    private val handler: StoreActionHandler,
    private val installer: AppInstaller,
) : StoreUpdater {

    private val dao = AppDatabase.getInstance(context).storeItemDao()
    private val prefs = context.getSharedPreferences("store_notified", Context.MODE_PRIVATE)

    override suspend fun checkAll(background: Boolean): CheckSummary {
        val items = repository.getAll()
        var failed = 0
        val updates = ArrayList<StoreItem>()
        for (item in items) {
            if (item.source is AppSource.AffiliatePlayStore) continue
            val o = options.item(item.id)
            if (background && o.excludeFromBackground) continue
            val refreshed = refresh(item)
            if (refreshed == null) {
                failed++
                continue
            }
            if (isUpdate(refreshed)) updates += refreshed
        }
        options.updateGlobal { it.copy(lastCheckMillis = System.currentTimeMillis()) }

        val installed = ArrayList<StoreItem>()
        val global = options.global.value
        if (background && global.autoInstall && updates.isNotEmpty() && installer.canInstallSilently()) {
            for (item in updates.toList()) {
                if (options.item(item.id).trackOnly) continue
                if (handler.install(item) is StoreAction.Installed) {
                    installed += item
                    updates.remove(item)
                }
            }
        }
        if (background && global.notifyUpdates) notify(updates, installed)
        return CheckSummary(updates, installed, failed)
    }

    override suspend fun checkOne(item: StoreItem): Boolean = refresh(item) != null

    /** Asks the source and stores the answer; null when the source could not be reached. */
    private suspend fun refresh(item: StoreItem): StoreItem? {
        val release = registry.resolveLatestRelease(item.source).getOrNull() ?: return null
        val now = System.currentTimeMillis()
        dao.updateLatestRelease(
            id = item.id,
            versionCode = release.versionCode,
            version = release.version,
            downloadUrl = release.downloadUrl,
            changelog = release.changelog,
            size = release.size,
            publishedAt = release.publishedAt,
            checkedAt = now,
        )
        return item.copy(latestRelease = release, lastCheckedAt = now)
    }

    /** A newer version, unless the app is pinned or the user chose to skip exactly this version */
    private fun isUpdate(item: StoreItem): Boolean {
        val o = options.item(item.id)
        if (o.pinned) return false
        if (o.skippedVersion != null && o.skippedVersion == item.latestRelease?.version) return false
        return item.hasUpdate
    }

    // ---- notification ----

    private fun notify(updates: List<StoreItem>, installed: List<StoreItem>) {
        // only announce versions the user has not been told about yet
        val fresh = updates.filter { prefs.getString(it.id, null) != it.latestRelease?.version }
        if (fresh.isEmpty() && installed.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // not allowed to show notifications: don't count these versions as announced
        if (!nm.areNotificationsEnabled()) return
        fresh.forEach { prefs.edit().putString(it.id, it.latestRelease?.version).apply() }
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STORE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            fresh.isNotEmpty() -> if (fresh.size == 1) "Update for ${fresh[0].displayName}" else "${fresh.size} app updates"
            else -> if (installed.size == 1) "${installed[0].displayName} was updated" else "${installed.size} apps were updated"
        }
        val text = (if (fresh.isNotEmpty()) fresh.joinToString { it.displayName } else installed.joinToString { it.displayName })
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL = "store_updates"
        private const val NOTIFICATION_ID = 7410
    }
}
