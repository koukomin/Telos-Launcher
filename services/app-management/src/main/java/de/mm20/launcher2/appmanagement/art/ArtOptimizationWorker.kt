package de.mm20.launcher2.appmanagement.art

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class ArtOptimizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val uiSettings: UiSettings by inject()

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!uiSettings.artOptimizerMasterEnabled.first()) {
            return Result.success()
        }

        if (!TelosArtOptimizer.isPrivilegedAvailable(context)) {
            return Result.retry()
        }

        val checkPowerSave = uiSettings.artOptimizationSkipPowerSave.first()
        if (checkPowerSave) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isPowerSaveMode == true) {
                return Result.retry()
            }
        }

        createNotificationChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(de.mm20.launcher2.base.R.drawable.speed_24px)
            .setContentTitle(context.getString(de.mm20.launcher2.i18n.R.string.art_optimizer_title))
            .setContentText(context.getString(de.mm20.launcher2.i18n.R.string.art_optimizer_audit_scanning))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build())
        }

        setForeground(foregroundInfo)

        val profile = uiSettings.artOptimizationProfile.first()
        val skipFrozen = uiSettings.artSkipFrozenApps.first()
        val frozenMode = uiSettings.artFrozenAppsMode.first()
        val fallbackEmpty = uiSettings.artFallbackEmptyProfileToSpeed.first()
        val runSqliteVacuum = uiSettings.artSqliteOptimizeEnabled.first()

        TelosArtOptimizer.optimizeSmartPackages(
            context = context,
            profile = profile,
            skipFrozenApps = skipFrozen,
            frozenAppsMode = frozenMode,
            fallbackEmptyProfileToSpeed = fallbackEmpty,
        ) { progress ->
            if (progress.isRunning && progress.totalPackages > 0) {
                val text = context.getString(
                    de.mm20.launcher2.i18n.R.string.art_optimizer_progress_fmt,
                    progress.currentPackage,
                    progress.currentIndex,
                    progress.totalPackages
                )
                notificationBuilder.setContentText(text)
                    .setProgress(progress.totalPackages, progress.currentIndex, false)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
        }

        if (runSqliteVacuum) {
            SqliteVacuumEngine.optimizeDatabases(context)
        }

        return Result.success()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(de.mm20.launcher2.i18n.R.string.art_optimizer_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(de.mm20.launcher2.i18n.R.string.art_optimizer_summary)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_TAG = "art_optimization_work"
        const val CHANNEL_ID = "art_optimizer_channel"
        const val NOTIFICATION_ID = 90210

        fun schedule(
            context: Context,
            frequencyDays: Long = 1,
            requiresCharging: Boolean = true,
            requiresIdle: Boolean = true,
            requiresBatteryNotLow: Boolean = true
        ) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(requiresCharging)
                .setRequiresDeviceIdle(requiresIdle)
                .setRequiresBatteryNotLow(requiresBatteryNotLow)
                .build()

            val request = PeriodicWorkRequestBuilder<ArtOptimizationWorker>(frequencyDays, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
        }
    }
}
