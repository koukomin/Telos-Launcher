package de.mm20.launcher2.freeze

import android.app.Notification
import android.app.UiModeManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.getSystemService
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.flow.first

/**
 * Decides whether a freeze candidate must be left alone right now. Checked before every
 * auto-freeze trigger (screen-off, idle, battery saver) so none of them can bypass it.
 *
 * Deliberately does *not* look at downloads/uploads/network usage - that's a separate,
 * not-yet-built rule.
 *
 * Wear OS: there is no general-purpose, permission-free API for "is this specific app being
 * actively used from a paired watch right now". In practice, meaningful background work for a
 * Wear companion app requires a foreground service (Android's background execution limits
 * enforce this since API 26), so [hasForegroundService] already covers the realistic case.
 * We do not claim a dedicated Wear OS check because there isn't an honest one to build.
 */
internal class FreezeExclusionChecker(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val permissionsManager: PermissionsManager,
) {

    suspend fun isExcluded(packageName: String): Boolean {
        if (isAndroidAutoActive()) return true

        val notifications = notificationRepository.notifications.first()
            .filter { it.packageName == packageName }

        if (notifications.isNotEmpty()) return true // rule 2: active notification
        if (notifications.any { it.mediaSessionToken != null }) return true // rule 3: media session
        if (notifications.any { it.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 }) return true // rule 4

        return isForeground(packageName) // rule 1
    }

    /**
     * Rule 1. Silently returns false (i.e. doesn't block freezing) if Usage Access hasn't been
     * granted, rather than treating "unknown" as "assume foreground" - otherwise auto-freeze
     * would silently do nothing at all for users who never grant that permission.
     */
    private fun isForeground(packageName: String): Boolean {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.UsageAccess)) return false
        val usageStatsManager = context.getSystemService<UsageStatsManager>() ?: return false

        val end = System.currentTimeMillis()
        val begin = end - FOREGROUND_LOOKBACK_MS
        val events = usageStatsManager.queryEvents(begin, end) ?: return false

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
        return foregroundPackage == packageName
    }

    /** Rule 5 (partial): device-wide, not per-package - see class doc for the Wear OS gap. */
    private fun isAndroidAutoActive(): Boolean {
        val uiModeManager = context.getSystemService<UiModeManager>() ?: return false
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    companion object {
        private const val FOREGROUND_LOOKBACK_MS = 60_000L
    }
}
