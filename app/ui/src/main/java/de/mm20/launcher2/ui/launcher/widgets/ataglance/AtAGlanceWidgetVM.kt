package de.mm20.launcher2.ui.launcher.widgets.ataglance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

sealed class GlanceContent {
    data class Weather(val forecast: Forecast) : GlanceContent()
    data class Calendar(val event: CalendarEvent) : GlanceContent()
    data class Battery(val level: Int, val charging: Boolean, val fullIn: Long?) : GlanceContent()
}

/**
 * Combines weather, the next upcoming calendar event, and battery status into one widget,
 * showing whichever is currently most relevant - mirrors the ranking mechanic the clock widget's
 * [de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider]s use, scaled down to three
 * fixed sources instead of a pluggable list.
 */
class AtAGlanceWidgetVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val weatherRepository: WeatherRepository by inject()
    private val calendarRepository: CalendarRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val weatherFlow: Flow<GlanceContent.Weather?> = weatherRepository.getForecasts(limit = 24)
        .map { forecasts ->
            val now = System.currentTimeMillis()
            val current = forecasts.lastOrNull { it.timestamp <= now } ?: forecasts.firstOrNull()
            current?.let { GlanceContent.Weather(it) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val calendarFlow: Flow<GlanceContent.Calendar?> =
        permissionsManager.hasPermission(PermissionGroup.Calendar).flatMapLatest { granted ->
            if (!granted) return@flatMapLatest flowOf(null)
            calendarRepository.findMany(
                from = System.currentTimeMillis() - 60 * 60 * 1000L,
                to = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
            ).map { events ->
                val now = System.currentTimeMillis()
                events
                    .filter { it.endTime > now }
                    .minByOrNull { it.startTime ?: it.endTime }
                    ?.let { GlanceContent.Calendar(it) }
            }
        }

    /** Re-evaluates the scores once a minute, so an event moves up as its start time approaches. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    private val batteryFlow: Flow<GlanceContent.Battery> = callbackFlow {
        val batteryManager = context.getSystemService(BatteryManager::class.java) ?: run {
            close()
            return@callbackFlow
        }

        fun currentInfo(intent: Intent?) = GlanceContent.Battery(
            level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )?.let { it == BatteryManager.BATTERY_STATUS_CHARGING } ?: batteryManager.isCharging,
            fullIn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager.computeChargeTimeRemaining().takeIf { it > 0 }
            } else null,
        )

        trySendBlocking(currentInfo(null))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(currentInfo(intent))
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    val content = combine(weatherFlow, calendarFlow, batteryFlow, ticker) { weather, calendar, battery, _ ->
        val candidates = listOfNotNull(
            weather?.let { it to WEATHER_SCORE },
            batteryScore(battery)?.let { battery to it },
            calendar?.let { c -> calendarScore(c)?.let { c to it } },
        )
        candidates.maxByOrNull { it.second }?.first
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun batteryScore(battery: GlanceContent.Battery): Int? {
        return when {
            !battery.charging && battery.level <= LOW_BATTERY_THRESHOLD -> LOW_BATTERY_SCORE
            battery.charging -> CHARGING_SCORE
            else -> null
        }
    }

    private fun calendarScore(calendar: GlanceContent.Calendar): Int? {
        val start = calendar.event.startTime ?: calendar.event.endTime
        val minutesUntilStart = (start - System.currentTimeMillis()) / 60_000
        return when {
            minutesUntilStart <= SOON_THRESHOLD_MINUTES -> EVENT_SOON_SCORE
            minutesUntilStart <= UPCOMING_THRESHOLD_MINUTES -> EVENT_UPCOMING_SCORE
            else -> null
        }
    }

    companion object {
        private const val WEATHER_SCORE = 10
        private const val CHARGING_SCORE = 20
        private const val EVENT_UPCOMING_SCORE = 40
        private const val LOW_BATTERY_SCORE = 60
        private const val EVENT_SOON_SCORE = 70
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val SOON_THRESHOLD_MINUTES = 30
        private const val UPCOMING_THRESHOLD_MINUTES = 120
    }
}
