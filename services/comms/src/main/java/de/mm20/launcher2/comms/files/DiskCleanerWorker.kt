package de.mm20.launcher2.comms.files

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class DiskCleanerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cleaner = DiskCleaner(applicationContext)
        if (!cleaner.shouldRunNow()) return Result.success()
        val junk = cleaner.scan()
        if (junk.isEmpty()) {
            cleaner.markRan()
            return Result.success()
        }
        cleaner.fillTrash(TelosTrash(applicationContext), junk)
        return Result.success()
    }

    companion object {
        const val WORK = "telos_disk_cleaner"
        fun enqueue(context: Context) {
            val c = DiskCleaner(context)
            val wm = WorkManager.getInstance(context)
            if (c.intervalHours <= 0) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val hours = c.intervalHours.coerceIn(1, 24 * 31).toLong()
            val constraints = Constraints.Builder()
                .setRequiresCharging(c.onlyCharging)
                .build()
            val req = PeriodicWorkRequestBuilder<DiskCleanerWorker>(hours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
