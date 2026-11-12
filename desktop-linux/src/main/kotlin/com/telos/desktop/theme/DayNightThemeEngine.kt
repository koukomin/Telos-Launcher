package com.telos.desktop.theme

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure day/night decision logic (no IO, no network).
 *
 * Sunrise/sunset use lat/lon only when the user set coordinates or a one-shot
 * opt-in geo assist returned them. Otherwise approximate: dark 20:00–06:00 local.
 */
object DayNightThemeEngine {
    /** Local hour when approximate night begins (inclusive). */
    const val APPROX_NIGHT_START_HOUR = 20

    /** Local hour when approximate night ends (exclusive). */
    const val APPROX_NIGHT_END_HOUR = 6

    /**
     * Resolve effective chrome phase for [mode] at [instant] in [zoneId].
     * [latitude]/[longitude] optional; both required for solar calculation.
     */
    fun resolvePhase(
        mode: DayNightMode,
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        latitude: Double? = null,
        longitude: Double? = null,
    ): DayNightPhase = when (mode) {
        DayNightMode.Off -> DayNightPhase.QuietOps
        DayNightMode.Light -> DayNightPhase.PaperOps
        DayNightMode.Dark -> DayNightPhase.QuietOps
        DayNightMode.Auto -> if (isNight(instant, zoneId, latitude, longitude)) {
            DayNightPhase.QuietOps
        } else {
            DayNightPhase.PaperOps
        }
    }

    fun isNight(
        instant: Instant,
        zoneId: ZoneId,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Boolean {
        val zoned = instant.atZone(zoneId)
        val lat = latitude
        val lon = longitude
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0
        ) {
            val window = solarWindow(zoned, lat, lon)
            if (window != null) {
                val t = zoned.toLocalTime()
                return !isBetween(t, window.sunrise, window.sunset)
            }
        }
        return isApproximateNight(zoned.toLocalTime())
    }

    fun isApproximateNight(localTime: LocalTime): Boolean {
        val hour = localTime.hour
        return hour >= APPROX_NIGHT_START_HOUR || hour < APPROX_NIGHT_END_HOUR
    }

    /**
     * Parse an IANA timezone id; blank/invalid → JVM default.
     * Never throws for UI/settings round-trips.
     */
    fun parseZoneId(raw: String?, fallback: ZoneId = ZoneId.systemDefault()): ZoneId {
        val id = raw?.trim().orEmpty()
        if (id.isEmpty()) return fallback
        return runCatching { ZoneId.of(id) }.getOrDefault(fallback)
    }

    /** Milliseconds until the next day/night boundary (approx or solar). Bounded for schedulers. */
    fun millisUntilNextBoundary(
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        latitude: Double? = null,
        longitude: Double? = null,
        minMs: Long = 30_000L,
        maxMs: Long = 15 * 60_000L,
    ): Long {
        val zoned = instant.atZone(zoneId)
        val lat = latitude
        val lon = longitude
        val next: ZonedDateTime = if (lat != null && lon != null &&
            lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0
        ) {
            val window = solarWindow(zoned, lat, lon)
            if (window != null) {
                val todayRise = zoned.toLocalDate().atTime(window.sunrise).atZone(zoneId)
                val todaySet = zoned.toLocalDate().atTime(window.sunset).atZone(zoneId)
                when {
                    zoned.isBefore(todayRise) -> todayRise
                    zoned.isBefore(todaySet) -> todaySet
                    else -> zoned.toLocalDate().plusDays(1).atTime(window.sunrise).atZone(zoneId)
                }
            } else {
                nextApproxBoundary(zoned)
            }
        } else {
            nextApproxBoundary(zoned)
        }
        val delta = next.toInstant().toEpochMilli() - instant.toEpochMilli()
        return delta.coerceIn(minMs, maxMs)
    }

    private fun nextApproxBoundary(zoned: ZonedDateTime): ZonedDateTime {
        val date = zoned.toLocalDate()
        val six = date.atTime(APPROX_NIGHT_END_HOUR, 0).atZone(zoned.zone)
        val twenty = date.atTime(APPROX_NIGHT_START_HOUR, 0).atZone(zoned.zone)
        return when {
            zoned.isBefore(six) -> six
            zoned.isBefore(twenty) -> twenty
            else -> date.plusDays(1).atTime(APPROX_NIGHT_END_HOUR, 0).atZone(zoned.zone)
        }
    }

    data class SolarWindow(val sunrise: LocalTime, val sunset: LocalTime)

    /**
     * Approximate sunrise/sunset (civil-ish) for [zoned]'s calendar day.
     * Returns null when the sun does not rise/set (polar day/night).
     */
    fun solarWindow(zoned: ZonedDateTime, latitude: Double, longitude: Double): SolarWindow? {
        val dayOfYear = zoned.dayOfYear
        val zenith = 90.833 // official sunrise/sunset
        val latRad = Math.toRadians(latitude)

        fun hourFor(rising: Boolean): Double? {
            val lngHour = longitude / 15.0
            val t = if (rising) {
                dayOfYear + ((6.0 - lngHour) / 24.0)
            } else {
                dayOfYear + ((18.0 - lngHour) / 24.0)
            }
            val m = (0.9856 * t) - 3.289
            var l = m + (1.916 * sin(Math.toRadians(m))) +
                (0.020 * sin(Math.toRadians(2 * m))) + 282.634
            l = (l % 360.0 + 360.0) % 360.0
            var ra = Math.toDegrees(atanApprox(0.91764 * tan(Math.toRadians(l))))
            ra = (ra % 360.0 + 360.0) % 360.0
            val lQuad = (kotlin.math.floor(l / 90.0)) * 90.0
            val raQuad = (kotlin.math.floor(ra / 90.0)) * 90.0
            ra = ra + (lQuad - raQuad)
            ra /= 15.0
            val sinDec = 0.39782 * sin(Math.toRadians(l))
            val cosDec = cos(asin(sinDec))
            val cosH = (cos(Math.toRadians(zenith)) - (sinDec * sin(latRad))) /
                (cosDec * cos(latRad))
            if (cosH > 1.0 || cosH < -1.0) return null
            var h = if (rising) {
                360.0 - Math.toDegrees(acos(cosH))
            } else {
                Math.toDegrees(acos(cosH))
            }
            h /= 15.0
            val ut = h + ra - (0.06571 * t) - 6.622
            var local = ut - lngHour + (zoned.offset.totalSeconds / 3600.0)
            local = ((local % 24.0) + 24.0) % 24.0
            return local
        }

        val riseH = hourFor(rising = true) ?: return null
        val setH = hourFor(rising = false) ?: return null
        return SolarWindow(hoursToLocalTime(riseH), hoursToLocalTime(setH))
    }

    private fun hoursToLocalTime(hours: Double): LocalTime {
        val totalSec = ((hours * 3600.0).toLong()).coerceIn(0L, 24L * 3600L - 1L)
        return LocalTime.ofSecondOfDay(totalSec)
    }

    private fun isBetween(t: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start <= end) {
            !t.isBefore(start) && t.isBefore(end)
        } else {
            !t.isBefore(start) || t.isBefore(end)
        }
    }

    private fun atanApprox(x: Double): Double = kotlin.math.atan(x)
}
