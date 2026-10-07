package de.mm20.launcher2.data.store.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.store.updater.StoreUpdater
import org.koin.core.context.GlobalContext

/**
 * The periodic update check. WorkManager builds workers itself, so the real work is done by the
 * [StoreUpdater] from Koin: new versions are stored, the user is notified, and updates are installed
 * silently when that was allowed in the Store settings and Shizuku or root is available.
 */
class StoreUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val updater = GlobalContext.get().get<StoreUpdater>()
            val summary = updater.checkAll(background = true)
            // a handful of unreachable sources next to working ones is no reason to retry the whole run
            if (summary.failed > 0 && summary.updates.isEmpty() && summary.installed.isEmpty() && summary.failed >= 5) Result.retry()
            else Result.success()
        } catch (e: Exception) {
            CrashReporter.logException(e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "store_update_check"
    }
}
