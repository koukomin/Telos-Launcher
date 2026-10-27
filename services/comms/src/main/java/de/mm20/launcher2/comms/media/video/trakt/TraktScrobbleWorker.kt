package de.mm20.launcher2.comms.media.video.trakt

import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Durable exact-ID updates. No credential or stream URL is stored in WorkManager. */
class TraktScrobbleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val session = inputData.getString("session") ?: return Result.success()
        if (Trakt.session(applicationContext) != session) return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        if (TraktLibraryParser.identifiers(id) == null) return Result.success()
        val action = inputData.getString("action") ?: return Result.success()
        val at = inputData.getLong("at", 0)
        // A delayed start must not claim that a closed player is still playing.
        if (action == "start" && System.currentTimeMillis() - at > 30_000) return Result.success()
        val login = Trakt.login(applicationContext)
        if (!login.connected || !login.enabled) return Result.success()
        val season = inputData.getInt("season", -1).takeIf { it >= 0 }
        val episode = inputData.getInt("episode", -1).takeIf { it > 0 }
        if (inputData.getBoolean("series", false) && (season == null || episode == null)) return Result.success()
        val progress = inputData.getFloat("progress", 0f)
        return try {
            if (Trakt.scrobbleId(applicationContext, action, id, season, episode, progress, session)) Result.success()
            else if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }

    companion object {
        /** Latest action for one video supersedes any older unexecuted start/pause action. */
        fun enqueue(context: Context, intent: Intent): androidx.work.Operation {
            val id = intent.getStringExtra("id").orEmpty().take(512)
            val session = intent.getStringExtra("trakt_session").orEmpty().take(128)
            val season = intent.getIntExtra("season", -1)
            val episode = intent.getIntExtra("episode", -1)
            val key = "$session|$id|$season|$episode"
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val data = Data.Builder().putString("id", id).putString("session", session)
                .putString("action", intent.getStringExtra("action")).putFloat("progress", intent.getFloatExtra("progress", 0f))
                .putInt("season", season).putInt("episode", episode).putBoolean("series", intent.getBooleanExtra("series", false))
                .putLong("at", intent.getLongExtra("event_time", System.currentTimeMillis())).build()
            val request = OneTimeWorkRequestBuilder<TraktScrobbleWorker>().setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            return WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("trakt-scrobble-$hash", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
