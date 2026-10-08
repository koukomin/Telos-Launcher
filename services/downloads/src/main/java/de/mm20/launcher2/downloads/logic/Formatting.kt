package de.mm20.launcher2.downloads.logic

import java.util.Locale

/** Sizes, speeds and times as short text. Locale independent digits, decimal point of the default locale. */
object Formatting {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 0) return "?"
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "$bytes B" else String.format(locale, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    fun speed(bytesPerSecond: Long, locale: Locale = Locale.getDefault()): String = size(bytesPerSecond, locale) + "/s"

    /** 75 -> "1:15", 3700 -> "1:01:40" */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}
