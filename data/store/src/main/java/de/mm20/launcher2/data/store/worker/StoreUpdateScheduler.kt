package de.mm20.launcher2.data.store.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import de.mm20.launcher2.store.options.StoreOptions
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
class StoreUpdateScheduler(private val context: Context, private val options: StoreOptions) {

    private var started = false

    fun enable() {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            options.global
                .map { it.checkIntervalHours to it.wifiOnly }
                .distinctUntilChanged()
                .collect { (hours, wifiOnly) -> apply(hours, wifiOnly) }
        }
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
