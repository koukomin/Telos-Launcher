package com.telos.desktop.theme

import com.telos.desktop.network.DesktopNetworkController
import java.net.HttpURLConnection
import java.net.URI
import java.time.ZoneId

/**
 * Opt-in one-shot public IP→geo assist for timezone / country discovery.
 *
 * Privacy rules:
 * - Default off. Never contacts the network unless both
 *   [theme.autoTimezone][SettingsSnapshot.autoTimezone] and
 *   [location.networkAssist][SettingsSnapshot.networkAssist] are true.
 * - Kill-switch / offline → [AssistStatus.PreferenceOnly] or [AssistStatus.Failed]; fall back to manual TZ.
 * - Stores only timezone id, country code, and optional lat/lon in vault props.
 * - Never logs the client IP (response `ip` field is discarded).
 * - No continuous GPS / tracking / cloud theme sync.
 */
object NetworkTimezoneAssist {
    const val DEFAULT_ENDPOINT = "https://ipapi.co/json/"

    enum class AssistStatus {
        /** Opt-in false, kill-switch, or preference-only path — no network claim. */
        PreferenceOnly,
        /** One-shot geo succeeded; vault props may be updated. */
        Applied,
        /** Network/parse failure; keep manual timezone. */
        Failed,
    }

    data class GeoHint(
        val timezoneId: String?,
        val countryCode: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    data class AssistResult(
        val status: AssistStatus,
        val hint: GeoHint? = null,
        val message: String = "",
    )

    data class SettingsSnapshot(
        val autoTimezone: Boolean,
        val networkAssist: Boolean,
        val killSwitchEnabled: Boolean = false,
    )

    fun interface JsonFetcher {
        /** Fetch JSON body; must not log response body (may contain IP). */
        fun get(url: String): Result<String>
    }

    val defaultFetcher: JsonFetcher = JsonFetcher { url ->
        runCatching {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TelosDesktop/day-night-tz (opt-in; no tracking)")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("HTTP $code")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Gate: network only when both opt-in flags are true and kill-switch is off.
     * Injectable [fetcher] for unit tests (mock geo).
     */
    fun discover(
        settings: SettingsSnapshot,
        endpoint: String = DEFAULT_ENDPOINT,
        fetcher: JsonFetcher = defaultFetcher,
        killSwitchReader: () -> Boolean = { DesktopNetworkController.readKillSwitchEnabled() },
    ): AssistResult {
        if (!settings.autoTimezone || !settings.networkAssist) {
            return AssistResult(
                AssistStatus.PreferenceOnly,
                message = "opt-in required (theme.autoTimezone + location.networkAssist)",
            )
        }
        val kill = settings.killSwitchEnabled || runCatching { killSwitchReader() }.getOrDefault(false)
        if (kill) {
            return AssistResult(
                AssistStatus.PreferenceOnly,
                message = "kill-switch active; using manual timezone",
            )
        }
        val body = fetcher.get(endpoint).getOrElse { err ->
            return AssistResult(
                AssistStatus.Failed,
                message = err.message ?: "network failed",
            )
        }
        val hint = parseGeoJson(body)
            ?: return AssistResult(AssistStatus.Failed, message = "unrecognized geo payload")
        if (hint.timezoneId.isNullOrBlank() && hint.countryCode.isNullOrBlank() &&
            hint.latitude == null && hint.longitude == null
        ) {
            return AssistResult(AssistStatus.Failed, message = "empty geo hint")
        }
        // Validate timezone without retaining IP.
        val tz = hint.timezoneId?.let { DayNightThemeEngine.parseZoneId(it) }
        val normalized = hint.copy(
            timezoneId = tz?.id ?: hint.timezoneId?.takeIf { it.isNotBlank() },
            countryCode = hint.countryCode?.trim()?.uppercase()?.takeIf { it.length in 2..3 },
        )
        return AssistResult(AssistStatus.Applied, normalized)
    }

    /**
     * Parse common IP→geo JSON shapes (ipapi.co / ip-api.com-like).
     * Explicitly ignores `ip`, `query`, and similar fields — never returned.
     */
    fun parseGeoJson(json: String): GeoHint? {
        if (json.isBlank()) return null
        // Lightweight key extraction — avoid pulling a JSON dependency into desktop-linux.
        fun stringField(vararg keys: String): String? {
            for (key in keys) {
                val patterns = listOf(
                    Regex(""""$key"\s*:\s*"([^"]*)""""),
                    Regex("""'$key'\s*:\s*'([^']*)'"""),
                )
                for (p in patterns) {
                    val m = p.find(json) ?: continue
                    val v = m.groupValues[1].trim()
                    if (v.isNotEmpty()) return v
                }
            }
            return null
        }

        fun numberField(vararg keys: String): Double? {
            for (key in keys) {
                val m = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json) ?: continue
                return m.groupValues[1].toDoubleOrNull()
            }
            return null
        }

        val tzRaw = stringField("timezone", "time_zone", "tz")
        val country = stringField("country_code", "countryCode", "country")
        val lat = numberField("latitude", "lat")
        val lon = numberField("longitude", "lon", "lng")
        // Discard IP-bearing keys by never reading them.
        if (tzRaw == null && country == null && lat == null && lon == null) return null
        val tz = tzRaw?.let {
            runCatching { ZoneId.of(it.trim()).id }.getOrNull() ?: it.trim().takeIf { s -> s.isNotEmpty() }
        }
        return GeoHint(tz, country, lat, lon)
    }

    /** True only when both vault opt-in flags allow a network call. */
    fun isNetworkAllowed(autoTimezone: Boolean, networkAssist: Boolean): Boolean =
        autoTimezone && networkAssist
}
