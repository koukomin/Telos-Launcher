package de.mm20.launcher2.i18n

import android.content.Context
import android.text.format.DateUtils
import java.util.Locale

/**
 * Single Source of Truth for Locale-Aware Date & Time Range Formatting (Android DateUtils).
 * Formats ranges across 35 languages including LTR and RTL scripts (Arabic/Hebrew).
 */
object DateIntervalFormatter {

    /**
     * Formats a start and end timestamp into a localized date range string.
     */
    fun formatDateRange(
        context: Context,
        startMs: Long,
        endMs: Long,
        flags: Int = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
    ): String {
        return DateUtils.formatDateRange(context, startMs, endMs, flags)
    }

    /**
     * Formats a start and end timestamp into a localized time-only range string.
     */
    fun formatTimeRange(
        context: Context,
        startMs: Long,
        endMs: Long
    ): String {
        return DateUtils.formatDateRange(
            context,
            startMs,
            endMs,
            DateUtils.FORMAT_SHOW_TIME
        )
    }
}
