package com.telos.desktop.theme

import com.telos.desktop.settings.TelosDesktopSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Flips Paper Ops / Quiet Ops at day/night boundaries without blocking the UI thread.
 * Network geo assist runs only when both opt-in prefs are set (one-shot per enable / refresh).
 */
object DayNightThemeScheduler {
    private val job = AtomicReference<Job?>(null)
    private var lastAssistStatus: NetworkTimezoneAssist.AssistStatus =
        NetworkTimezoneAssist.AssistStatus.PreferenceOnly

    fun lastAssistStatus(): NetworkTimezoneAssist.AssistStatus = lastAssistStatus

    fun start(
        scope: CoroutineScope,
        settings: TelosDesktopSettings,
        fetcher: NetworkTimezoneAssist.JsonFetcher = NetworkTimezoneAssist.defaultFetcher,
    ) {
        job.getAndSet(null)?.cancel()
        val started = scope.launch(Dispatchers.Default) {
            var didAssistThisSession = false
            while (isActive) {
                tick(
                    settings = settings,
                    allowNetworkAssist = !didAssistThisSession,
                    fetcher = fetcher,
                ).also { didNetwork ->
                    if (didNetwork) didAssistThisSession = true
                }
                val zone = DayNightThemeEngine.parseZoneId(settings.themeTimezone)
                val wait = DayNightThemeEngine.millisUntilNextBoundary(
                    instant = Instant.now(),
                    zoneId = zone,
                    latitude = settings.themeLatitude,
                    longitude = settings.themeLongitude,
                )
                delay(wait)
            }
        }
        job.set(started)
    }

    fun stop() {
        job.getAndSet(null)?.cancel()
    }

    /**
     * One evaluation pass. Returns true if a network geo call was attempted successfully.
     * Disk/network work stays on [Dispatchers.IO]; palette apply is main-safe Compose state.
     */
    suspend fun tick(
        settings: TelosDesktopSettings,
        allowNetworkAssist: Boolean = true,
        fetcher: NetworkTimezoneAssist.JsonFetcher = NetworkTimezoneAssist.defaultFetcher,
        now: Instant = Instant.now(),
    ): Boolean {
        var usedNetwork = false
        withContext(Dispatchers.IO) {
            if (allowNetworkAssist &&
                NetworkTimezoneAssist.isNetworkAllowed(
                    settings.themeAutoTimezone,
                    settings.locationNetworkAssist,
                )
            ) {
                val result = NetworkTimezoneAssist.discover(
                    NetworkTimezoneAssist.SettingsSnapshot(
                        autoTimezone = settings.themeAutoTimezone,
                        networkAssist = settings.locationNetworkAssist,
                    ),
                    fetcher = fetcher,
                )
                lastAssistStatus = result.status
                when (result.status) {
                    NetworkTimezoneAssist.AssistStatus.Applied -> {
                        usedNetwork = true
                        val hint = result.hint
                        if (hint != null) {
                            hint.timezoneId?.let { settings.themeTimezone = it }
                            hint.countryCode?.let { settings.locationCountryCode = it }
                            if (hint.latitude != null && hint.longitude != null) {
                                settings.themeLatitude = hint.latitude
                                settings.themeLongitude = hint.longitude
                            }
                            settings.save()
                        }
                    }
                    NetworkTimezoneAssist.AssistStatus.Failed,
                    NetworkTimezoneAssist.AssistStatus.PreferenceOnly,
                    -> {
                        // Honest fallback: keep manual / JVM timezone.
                    }
                }
            } else if (!NetworkTimezoneAssist.isNetworkAllowed(
                    settings.themeAutoTimezone,
                    settings.locationNetworkAssist,
                )
            ) {
                lastAssistStatus = NetworkTimezoneAssist.AssistStatus.PreferenceOnly
            }
        }

        val mode = DayNightMode.parse(settings.themeDayNight)
        if (mode == DayNightMode.Off) {
            return usedNetwork
        }
        val zone = DayNightThemeEngine.parseZoneId(settings.themeTimezone)
        val phase = DayNightThemeEngine.resolvePhase(
            mode = mode,
            instant = now,
            zoneId = zone,
            latitude = settings.themeLatitude,
            longitude = settings.themeLongitude,
        )
        DayNightPalette.apply(phase)
        val intensity = when (phase) {
            DayNightPhase.PaperOps -> ThemeIntensity.PaperOps
            DayNightPhase.QuietOps -> ThemeIntensity.QuietOps
        }
        if (ThemeIntensity.parse(settings.themeIntensity) != intensity) {
            settings.applyThemeIntensity(intensity)
            withContext(Dispatchers.IO) { settings.save() }
        }
        return usedNetwork
    }

    /** Immediate apply for Settings toggles (IO for save, no network unless requested). */
    suspend fun applyNow(
        settings: TelosDesktopSettings,
        requestNetworkAssist: Boolean = false,
        fetcher: NetworkTimezoneAssist.JsonFetcher = NetworkTimezoneAssist.defaultFetcher,
    ) {
        tick(settings, allowNetworkAssist = requestNetworkAssist, fetcher = fetcher)
    }
}
