package de.mm20.launcher2.data.store.worker

import de.mm20.launcher2.base.containedScope
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.store.options.StoreOptions
import de.mm20.launcher2.store.repository.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeps the periodic [StoreUpdateWorker] in line with the Store settings: the interval, Wi-Fi only,
 * or off. Changing a setting reschedules it right away.
 */
class StoreUpdateScheduler(
    private val context: Context,
    private val options: StoreOptions,
    private val repository: StoreRepository,
) {

    private var started = false

    fun enable() {
        if (started) return
        started = true
        containedScope(Dispatchers.Default).launch { runCatching { trackTelosItself() } }
        containedScope(Dispatchers.Default).launch {
            options.global
                .map { it.checkIntervalHours to it.wifiOnly }
                .distinctUntilChanged()
                .collect { (hours, wifiOnly) -> apply(hours, wifiOnly) }
        }
    }

    /**
     * Telos checks for its own updates like for any other app: it is in the Store list from the first start
     * (GitHub releases of the project; the APK for this phone's CPU is chosen by the file name).
     * Only the signed release build does this: debug and nightly builds have another package name and
     * the F-Droid build is updated by F-Droid.
     */
    private suspend fun trackTelosItself() {
        val pkg = context.packageName
        if (!pkg.endsWith(".release")) return
        val versionName = context.packageManager.getPackageInfo(pkg, 0).versionName.orEmpty()
        if (versionName.endsWith("-fdroid")) return
        if (repository.getAll().any { it.packageName == pkg }) return
        repository.insertItem(
            StoreItem(
                id = "telos-self",
                packageName = pkg,
                displayName = "Telos",
                source = AppSource.GitHub(owner = "koukomin", repo = "Telos-Launcher"),
            )
        )
    }

    private fun apply(hours: Int, wifiOnly: Boolean) {
        val work = WorkManager.getInstance(context)
        if (hours <= 0) {
            work.cancelUniqueWork(StoreUpdateWorker.WORK_NAME)
            return
        }
        val request = PeriodicWorkRequest.Builder(StoreUpdateWorker::class.java, hours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        work.enqueueUniquePeriodicWork(StoreUpdateWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(StoreUpdateWorker.WORK_NAME)
    }
}
