package de.mm20.launcher2.network.impl.blocklist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.mm20.launcher2.network.api.BlocklistController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Daily check for newer Rethink blocklists. Scheduled only when the user downloaded them and left auto update on. */
class BlocklistUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val controller: BlocklistController by inject()

    override suspend fun doWork(): Result {
        if (!controller.autoUpdate.value || !controller.installed.value) return Result.success()
        return controller.update(force = false).fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }
}
