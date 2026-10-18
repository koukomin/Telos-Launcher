package de.mm20.launcher2.data.store.updater

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
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
    private val tools: de.mm20.launcher2.store.updater.StoreTools,
) : StoreUpdater {

    private val dao = AppDatabase.getInstance(context).storeItemDao()
    private val prefs = context.getSharedPreferences("store_notified", Context.MODE_PRIVATE)

    private val checkMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkAll(background: Boolean): CheckSummary = checkMutex.withLock {
        discoverInstalled()
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
        if (background && updates.isNotEmpty() && installer.canInstallSilently()) {
            for (item in updates.toList()) {
                val itemOptions = options.item(item.id)
                if (!de.mm20.launcher2.store.updater.StoreUpdatePolicy.automatic(itemOptions, global.autoInstall)) continue
                if (handler.install(item) is StoreAction.Installed) {
                    installed += item
                    updates.remove(item)
                }
            }
        }
        if (background && global.notifyUpdates) notify(updates, installed)
        CheckSummary(updates, installed, failed)
    }

    /** Discover supported non-Play installs without opting them into automatic installation. */
    private suspend fun discoverInstalled() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val pm = context.packageManager
        val tracked = repository.getAll().map { it.packageName }.toSet()
        val now = System.currentTimeMillis()
        val candidates = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
            android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES).filter { app ->
            app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0 &&
            app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 &&
                app.packageName != context.packageName && app.packageName !in tracked &&
                now - prefs.getLong("discovery:${app.packageName}", 0) >= 24 * 60 * 60 * 1000L &&
                runCatching {
                    val installer = if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(app.packageName).installingPackageName
                        else @Suppress("DEPRECATION") pm.getInstallerPackageName(app.packageName)
                    de.mm20.launcher2.store.updater.StoreUpdatePolicy.discoverInstaller(installer)
                }.getOrDefault(false)
        }
        for (app in candidates) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val source = tools.findSourceForInstalled(app.packageName)
            // Check each package at most daily; new packages are still discovered immediately.
            prefs.edit().putLong("discovery:${app.packageName}", now).apply()
            if (source == null || source is AppSource.AffiliatePlayStore) continue
            val item = StoreItem(java.util.UUID.randomUUID().toString(), app.packageName,
                pm.getApplicationLabel(app).toString(), source)
            options.update(item.id) { it.copy(autoUpdate = false) }
            repository.insertItem(item)
        }
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
        // the icon of the (first) app next to the text
        val largeIcon = (if (fresh.isNotEmpty()) fresh else installed).firstOrNull()?.let { appIcon(it.packageName) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(
                de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_store_app")
                    ?: de.mm20.launcher2.base.R.drawable.ic_app_store_full,
            )
            .let { de.mm20.launcher2.search.VirtualAppAppearance.decorate(it, context, "telos_store_app") }
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    private fun appIcon(packageName: String): android.graphics.Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128)
    }.getOrNull()

    companion object {
        private const val CHANNEL = "store_updates"
        private const val NOTIFICATION_ID = 7410
    }
}
