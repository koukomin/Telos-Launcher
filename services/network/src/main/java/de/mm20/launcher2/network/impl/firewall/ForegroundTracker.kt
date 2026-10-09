package de.mm20.launcher2.network.impl.firewall

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import de.mm20.launcher2.network.api.AppDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds out which apps are in the foreground, which the firewall needs for its "background" rules.
 * Android does not tell this to a VPN, so it reads the usage events (`UsageStatsManager`), which needs
 * the "usage access" special permission that the user grants in the Android settings. Without it
 * [isForeground] returns null and the background rules never block.
 *
 * Polls every two seconds, only while [setNeeded] was called with true. An app counts as foreground
 * while it has a resumed activity and for [GRACE_MS] after it was paused, so that connections that
 * an app opens while the user switches away are not cut.
 */
internal class ForegroundTracker(
    private val context: Context,
    private val apps: AppDirectory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _available = MutableStateFlow(hasPermission())

    /** True when usage access is granted. */
    val available: StateFlow<Boolean> = _available

    // package -> millis when it was last seen in the foreground (Long.MAX_VALUE = resumed right now)
    private val lastForeground = ConcurrentHashMap<String, Long>()
    private var lastQuery = 0L

    fun refreshAvailability() {
        _available.value = hasPermission()
        if (!_available.value) stopPolling()
    }

    private fun hasPermission(): Boolean = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    /** Starts or stops polling; call with true while any rule depends on the foreground state. */
    @Synchronized
    fun setNeeded(needed: Boolean) {
        if (needed && _available.value) startPolling() else stopPolling()
    }

    private fun startPolling() {
        if (job?.isActive == true) return
        lastQuery = 0L
        job = scope.launch {
            while (isActive) {
                try {
                    poll()
                } catch (e: Exception) {
                    // permission revoked or service busy; try again next round
                    _available.value = hasPermission()
                }
                delay(POLL_MS)
            }
        }
    }

    private fun stopPolling() {
        job?.cancel()
        job = null
    }

    private fun poll() {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - INITIAL_LOOKBACK_MS else lastQuery - 1
        val events = usm.queryEvents(from, now) ?: return
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                RESUMED -> lastForeground[pkg] = Long.MAX_VALUE
                PAUSED -> lastForeground[pkg] = event.timeStamp
            }
        }
        lastQuery = now
    }

    /** True/false when known, null when usage access is missing or the app is a system uid. */
    fun isForeground(appId: Int): Boolean? {
        if (!_available.value || job?.isActive != true) return null
        if (appId < FIRST_APPLICATION_UID) return null
        val entry = apps.byUid(appId) ?: return null
        val now = System.currentTimeMillis()
        return entry.packages.any { pkg ->
            val t = lastForeground[pkg] ?: return@any false
            t == Long.MAX_VALUE || now - t < GRACE_MS
        }
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val GRACE_MS = 15_000L
        const val INITIAL_LOOKBACK_MS = 60L * 60 * 1000
        const val FIRST_APPLICATION_UID = 10000

        // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND and ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
        const val RESUMED = 1
        const val PAUSED = 2
    }
}
