package de.mm20.launcher2.searchactions.actions

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Calendar

class ScreenTimeAction(
    override val label: String,
    val screenTimeText: String,
) : SearchAction {

    override val icon: SearchActionIcon = SearchActionIcon.StatsSearch
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    companion object {
        fun getTodayScreenTime(context: Context): String {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return "Screen Time: Usage access required"
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startTime = calendar.timeInMillis

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            var totalTimeMs = 0L
            if (stats != null) {
                for (usage in stats) {
                    totalTimeMs += usage.totalTimeInForeground
                }
            }

            val hours = totalTimeMs / 3600000L
            val minutes = (totalTimeMs % 3600000L) / 60000L

            return if (totalTimeMs > 0) {
                "Screen Time: ${hours}h ${minutes}m today"
            } else {
                "Screen Time: Tap to enable Usage Access"
            }
        }
    }
}
