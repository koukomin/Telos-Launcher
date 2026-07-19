package de.mm20.launcher2.freeze

import android.app.Notification
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.TrafficStats
import androidx.core.content.getSystemService
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Decides whether a freeze candidate must be left alone right now. Checked before every
 * auto-freeze trigger (screen-off, idle, battery saver) so none of them can bypass it.
 *
 * A per-app "never freeze" override ([FreezeSettings.neverFreezeApps]) always wins, regardless
 * of profile. Of the five rules below, only rule 3 (active media session) is ever affected by
 * [FreezeExclusionStrictness] - [FreezeProfile.UltraAggressive][de.mm20.launcher2.preferences.FreezeProfile]
 * is the only profile that relaxes it. Rules 1, 2, 4, and 5 are always enforced, for every
 * profile, with no exception - loosening them could freeze an app mid-call or mid-navigation.
 */
internal class FreezeExclusionChecker(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val usageStatsProvider: AppUsageStatsProvider,
    private val settings: FreezeSettings,
    private val profileManager: FreezeProfileManager,
) {

    suspend fun isExcluded(packageName: String): Boolean {
        if (settings.neverFreezeApps.first().contains(packageName)) return true // per-app override
        if (isAndroidAutoActive()) return true

        val notifications = notificationRepository.notifications.first()
            .filter { it.packageName == packageName && !it.isGroupSummary }

        // Rule 2 & 4: skip if app has an ongoing notification or foreground service
        if (notifications.any { it.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or Notification.FLAG_FOREGROUND_SERVICE) != 0 }) {
            return true
        }

        // Rule 3: active media session. Only strictness that's ever relaxed.
        val strictness = profileManager.resolvedSettings.first().exclusionStrictness
        if (strictness == FreezeExclusionStrictness.Strict &&
            notifications.any { it.mediaSessionToken != null }
        ) return true

        if (isForeground(packageName)) return true // rule 1

        if (settings.excludeMusic.first() && isMusicActive()) return true
        if (settings.excludeNetwork.first() && isNetworkActive(packageName)) return true

        return false
    }

    private fun isMusicActive(): Boolean {
        val audioManager = context.getSystemService<AudioManager>() ?: return false
        return audioManager.isMusicActive
    }

    private suspend fun isNetworkActive(packageName: String): Boolean {
        val uid = try {
            context.packageManager.getPackageUid(packageName, 0)
        } catch (e: Exception) {
            return false
        }
        val threshold = settings.networkThresholdKb.first() * 1024L
        if (threshold <= 0) return false

        val rx1 = TrafficStats.getUidRxBytes(uid)
        val tx1 = TrafficStats.getUidTxBytes(uid)
        if (rx1 == TrafficStats.UNSUPPORTED.toLong()) return false

        delay(500) // Short sample to detect active transfer

        val rx2 = TrafficStats.getUidRxBytes(uid)
        val tx2 = TrafficStats.getUidTxBytes(uid)

        return (rx2 - rx1) + (tx2 - tx1) > (threshold / 2) // Adjust for 0.5s sample
    }

    /**
     * Rule 1. Silently returns false (i.e. doesn't block freezing) if Usage Access hasn't been
     * granted, rather than treating "unknown" as "assume foreground" - otherwise auto-freeze
     * would silently do nothing at all for users who never grant that permission.
     */
    private fun isForeground(packageName: String): Boolean {
        return usageStatsProvider.currentForegroundPackage() == packageName
    }

    /** Rule 5 (partial): device-wide, not per-package. */
    private fun isAndroidAutoActive(): Boolean {
        val uiModeManager = context.getSystemService<UiModeManager>() ?: return false
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }
}
