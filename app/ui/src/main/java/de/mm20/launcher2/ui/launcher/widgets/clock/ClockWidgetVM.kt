package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.AlarmPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.BatteryPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.DatePartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.FavoritesPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.MusicPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.SmartspacerPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.WeatherPartProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()

    private val partProviders = settings.parts.combine(settings.useSmartspacer) { p, s ->
        p to s
    }.map { (parts, smartspacer) ->
        if (smartspacer && isAtLeastApiLevel(29)) {
            return@map listOf(SmartspacerPartProvider())
        }

        val providers = mutableListOf<PartProvider>()
        if (parts.date) providers += DatePartProvider()
        if (parts.weather) providers += WeatherPartProvider()
        if (parts.music) providers += MusicPartProvider()
        providers += BatteryPartProvider(parts.battery)
        if (parts.alarm) providers += AlarmPartProvider()
        providers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun getActivePart(context: Context): Flow<PartProvider?> = channelFlow {
        partProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(null)
                return@collectLatest
            }
            val rankings = providers.map { it.getRanking(context).map { r -> r to it } }
            combine(rankings) { r ->
                r.filter { it.first > 0 }.maxByOrNull { it.first }?.second
            }.collectLatest {
                send(it)
            }
        }
    }

    // Seeded with the same defaults LauncherSettingsData itself uses (rather than null) so the
    // clock renders its real, final layout from the very first frame - a null initial value here
    // previously meant neither the compact/vertical branch in ClockWidget's `when` matched on
    // first composition, and the eventual real value could differ (wrong style/alignment) from
    // whatever null-driven branch briefly showed, reading as a startup glitch.
    val compactLayout = settings.compact
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val clockStyle = settings.clockStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ClockWidgetStyle.Digital1())

    val color = settings.color
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ClockWidgetColors.Auto)

    val alignment = settings.alignment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ClockWidgetAlignment.Bottom)

    val dockProvider = settings.dock
        .map { if (it) FavoritesPartProvider() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun updateTime(time: Long) {
        partProviders.value.forEach { it.setTime(time) }
    }

    fun launchClockApp(context: Context) {
        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
