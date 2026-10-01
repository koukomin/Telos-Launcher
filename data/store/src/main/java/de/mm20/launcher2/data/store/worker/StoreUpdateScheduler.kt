package de.mm20.launcher2.data.store.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues/cancels the periodic [StoreUpdateWorker] run. Mirrors `CurrencyRepository`'s worker scheduling in `:data:currencies`. */
class StoreUpdateScheduler(private val context: Context) {

    fun enable() {
        val request = PeriodicWorkRequest.Builder(StoreUpdateWorker::class.java, 6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StoreUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(StoreUpdateWorker.WORK_NAME)
    }
}
