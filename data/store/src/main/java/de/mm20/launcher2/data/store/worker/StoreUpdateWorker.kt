package de.mm20.launcher2.data.store.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.data.store.StoreItemMapper
import de.mm20.launcher2.data.store.fetcher.FDroidFetcher
import de.mm20.launcher2.data.store.fetcher.GitHubFetcher
import de.mm20.launcher2.data.store.fetcher.StoreFetcherRegistryImpl
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.store.fetcher.StoreFetcherRegistry

/**
 * Periodically refreshes every tracked [de.mm20.launcher2.store.model.StoreItem]'s latest release
 * and logs when a newer `versionCode` is available than what's installed. Follows the same
 * pattern as `ExchangeRateWorker` (`:data:currencies`): no Koin injection inside the worker
 * itself - `WorkManager` constructs workers directly, so dependencies are built by hand here.
 *
 * This worker only detects and logs updates; it deliberately does not install anything - silent
 * background installs are a user-facing policy decision (auto-update toggle, backend choice)
 * that belongs in a later phase, not baked into the background check itself.
 */
class StoreUpdateWorker(
    private val context: Context,
    params: WorkerParameters,
    private val fetcherRegistry: StoreFetcherRegistry = StoreFetcherRegistryImpl(GitHubFetcher(), FDroidFetcher()),
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(context).storeItemDao()
        val items = try {
            dao.getAll()
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return Result.retry()
        }

        var failures = 0
        for (entity in items) {
            val item = try {
                StoreItemMapper.toDomain(entity)
            } catch (e: Exception) {
                // A malformed sourceJson is a data bug, not a transient failure - skip the item
                // rather than retrying the whole batch forever.
                CrashReporter.logException(e)
                continue
            }

            val release = try {
                fetcherRegistry.fetchLatestRelease(item.source)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                failures++
                continue
            }

            if (release == null) {
                Log.d(TAG, "No release resolved for ${item.packageName}")
                continue
            }

            dao.updateLatestRelease(
                id = item.id,
                versionCode = release.versionCode,
                version = release.version,
                downloadUrl = release.downloadUrl,
                changelog = release.changelog,
                size = release.size,
                publishedAt = release.publishedAt,
                checkedAt = System.currentTimeMillis(),
            )

            val installed = item.installedVersionCode
            val latest = release.versionCode
            if (installed != null && latest != null && latest > installed) {
                Log.i(TAG, "Update available for ${item.packageName}: $installed -> $latest (${release.version})")
            }
        }

        // Only fail the whole run if every item's fetch errored - a handful of unreachable
        // sources alongside otherwise-successful ones shouldn't trigger a full retry/backoff.
        return if (items.isNotEmpty() && failures == items.size) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "store_update_check"
        private const val TAG = "StoreUpdateWorker"
    }
}
