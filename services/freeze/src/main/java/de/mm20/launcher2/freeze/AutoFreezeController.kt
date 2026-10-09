package de.mm20.launcher2.freeze

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches for screen-off, idle timeout, and battery-saver events and freezes the apps the user
 * explicitly opted in via settings ([FreezeSettings.candidates]) - minus whatever
 * [FreezeExclusionChecker] vetoes (foreground, active notification/media session/foreground
 * service, Android Auto, or a per-app "never freeze" override).
 *
 * Trigger enablement and the idle timeout come from [FreezeProfileManager.resolvedSettings], i.e.
 * whichever [de.mm20.launcher2.preferences.FreezeProfile] the user has selected.
 *
 * It only ever acts on the explicit opt-in candidate list, never on "whatever's running in the
 * background".
 *
 * Also keeps a "N apps ready to freeze" notification in sync (see [updateUnfrozenNotification]),
 * so the user can freeze on demand without waiting for screen-off/idle/battery-saver to trigger.
 *
 * Registered as a Koin singleton so it's created (and starts listening) once, at app start.
 */
class AutoFreezeController internal constructor(
    private val context: Context,
    private val freezeManager: FreezeManager,
    private val settings: FreezeSettings,
    private val exclusionChecker: FreezeExclusionChecker,
    private val profileManager: FreezeProfileManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onPowerSaveModeChanged()
            }
        }
    }

    private val freezeNowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            freezeAllCandidatesNow()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            freezeNowReceiver,
            IntentFilter(ACTION_FREEZE_NOW),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            try {
                freezeManager.refreshBackendState()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AutoFreezeController", "backend refresh failed", e)
            }
        }

        // Keep the "ready to freeze" notification in sync with the candidate list itself (e.g.
        // the user just added/removed a candidate in settings), on top of the trigger-driven
        // refreshes below. Deliberately not polled continuously - that would work against the
        // whole point of a battery-saving feature; it's refreshed at every point this class
        // already wakes up for, which covers the common cases.
        scope.launch {
            settings.candidates.collect {
                try {
                    updateUnfrozenNotification()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("AutoFreezeController", "notification update failed", e)
                }
            }
        }
    }

    private fun onScreenOff() {
        idleJob?.cancel()
        scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            if (profileManager.resolvedSettings.first().freezeOnScreenOff) {
                freezeCandidates()
            }
        }
        // The idle timeout counts from the moment the screen turns off; turning the screen
        // back on (onScreenOn) cancels it.
        idleJob = scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            val resolved = profileManager.resolvedSettings.first()
            if (!resolved.freezeOnIdle) return@launch
            delay(resolved.idleTimeoutMinutes * 60_000L)
            freezeCandidates()
        }
    }

    private fun onScreenOn() {
        idleJob?.cancel()
        idleJob = null
        scope.launch { updateUnfrozenNotification() }
    }

    private fun onPowerSaveModeChanged() {
        val powerManager = context.getSystemService<PowerManager>() ?: return
        if (!powerManager.isPowerSaveMode) return
        scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            if (profileManager.resolvedSettings.first().freezeOnBatterySaver) {
                freezeCandidates()
            }
        }
    }

    /**
     * Manual trigger (e.g. the "Freeze now" widget button, or the "ready to freeze" notification):
     * freezes every candidate the user opted in, same exclusion checks as the automatic triggers
     * above - it does not bypass them.
     */
    fun freezeAllCandidatesNow() {
        scope.launch { freezeCandidates() }
    }

    private suspend fun freezableCandidates(): List<String> {
        val candidates = settings.candidates.first()
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .filterNot { freezeManager.isFrozen(it) }
            .filterNot { exclusionChecker.isExcluded(it) }
    }

    private suspend fun freezeCandidates() {
        // A backend that dies (Shizuku binder gone, root denied, ...) must not crash the launcher
        // from this background scope.
        try {
            val freezable = freezableCandidates()
            if (freezable.isEmpty()) return
            freezeManager.refreshBackendState()
            // Not freeze(): this runs from a background trigger (screen-off/idle/battery-saver, no
            // foreground activity), and Island's freeze mechanism needs a foreground context to
            // launch its Activity - see FreezeManager.freezeInBackground.
            freezeManager.freezeInBackground(freezable)
            updateUnfrozenNotification()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AutoFreezeController", "auto freeze failed", e)
        }
    }

    /**
     * Shows (or updates, or cancels) a notification listing how many freeze candidates aren't
     * currently frozen, so the user can freeze them on demand rather than waiting for the next
     * screen-off/idle/battery-saver trigger. Gated by [FreezeSettings.autoFreezeEnabled] - this is
     * part of the auto-freeze feature, not a standalone thing, so it stays quiet if that's off.
     */
    @SuppressLint("MissingPermission")
    private suspend fun updateUnfrozenNotification() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (!settings.autoFreezeEnabled.first()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        val freezable = freezableCandidates()
        if (freezable.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.freeze_ready_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val freezeNowIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_FREEZE_NOW).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ac_unit_24px)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.freeze_ready_notification_title, freezable.size, freezable.size
                )
            )
            .setContentText(context.getString(R.string.freeze_ready_notification_text))
            .setContentIntent(freezeNowIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // areNotificationsEnabled() covers POST_NOTIFICATIONS on API 33+ and the per-app switch below it
        // (checkSelfPermission(POST_NOTIFICATIONS) reports "denied" on API 26-32 even when notifications work).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "freeze_ready"
        private const val NOTIFICATION_ID = 4822
        private const val ACTION_FREEZE_NOW = "de.mm20.launcher2.freeze.ACTION_FREEZE_NOW"
    }
}
