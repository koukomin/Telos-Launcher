package de.mm20.launcher2.freeze

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager

/**
 * Wraps [UsageStatsManager] queries shared by [FreezeExclusionChecker] (is this one package
 * foreground right now) and the Freeze Dashboard's app status view (which package is foreground,
 * and total per-package runtime). Everything here needs [PermissionGroup.UsageAccess]; every
 * method degrades to an empty/false result rather than throwing if it isn't granted.
 */
class AppUsageStatsProvider internal constructor(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    fun hasPermission(): Boolean = permissionsManager.checkPermissionOnce(PermissionGroup.UsageAccess)

    /** The package currently in the foreground, reconstructed from the last minute of usage
     * events, or null if unknown (no permission, or nothing moved to foreground recently). */
    fun currentForegroundPackage(): String? {
        if (!hasPermission()) return null
        val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return null

        val end = System.currentTimeMillis()
        val begin = end - FOREGROUND_LOOKBACK_MS
        val events = usageStatsManager.queryEvents(begin, end) ?: return null

        var foregroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundPackage = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (event.packageName == foregroundPackage) foregroundPackage = null
            }
        }
        return foregroundPackage
    }

    /**
     * Total foreground time per package over the last [sinceMillisAgo] (default: 24h), in
     * milliseconds. One batched query, not one per package - `queryUsageStats` can return
     * multiple bucketed entries per package, so they're summed.
     */
    fun totalTimeInForeground(sinceMillisAgo: Long = ONE_DAY_MS): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return emptyMap()

        val end = System.currentTimeMillis()
        val begin = end - sinceMillisAgo
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
            ?: return emptyMap()

        return stats
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sumOf { it.totalTimeInForeground } }
    }

    companion object {
        private const val FOREGROUND_LOOKBACK_MS = 60_000L
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
