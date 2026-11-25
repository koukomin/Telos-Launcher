package de.mm20.launcher2.i18n

import android.text.format.Formatter
import android.content.Context
import java.text.NumberFormat
import java.util.Locale

/**
 * Single Source of Truth for Locale-Aware Number, Percentage and File Size Formatting.
 */
object TelosFormatter {

    /**
     * Formats bytes into human-readable localized file size strings (e.g. "1.5 MB", "4.2 GB").
     */
    fun formatFileSize(context: Context, sizeBytes: Long): String {
        return Formatter.formatFileSize(context, sizeBytes)
    }

    /**
     * Formats integer numbers according to current locale (e.g., 1,000 or 1.000).
     */
    fun formatNumber(number: Long, locale: Locale = Locale.getDefault()): String {
        return NumberFormat.getInstance(locale).format(number)
    }

    /**
     * Formats a floating point ratio (0.0 .. 1.0) into a percentage string (e.g., "85%").
     */
    fun formatPercentage(ratio: Float, locale: Locale = Locale.getDefault()): String {
        val percentFormat = NumberFormat.getPercentInstance(locale)
        percentFormat.maximumFractionDigits = 1
        return percentFormat.format(ratio.coerceIn(0f, 1f))
    }
}
