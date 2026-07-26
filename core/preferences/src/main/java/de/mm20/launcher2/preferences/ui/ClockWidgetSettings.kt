package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.BatteryStatusVisibility
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ClockWidgetStyleEnum
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ClockWidgetParts(
    val date: Boolean,
    val music: Boolean = false,
    val battery: BatteryStatusVisibility = BatteryStatusVisibility.Hide,
    val alarm: Boolean = false,
)

class ClockWidgetSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val compact
        get() = launcherDataStore.data.map { it.clock.clockWidgetCompact }

    fun setCompact(compact: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetCompact = compact))
        }
    }

    val parts
        get() = launcherDataStore.data.map {
            ClockWidgetParts(
                date = it.clock.clockWidgetDatePart,
                music = it.clock.clockWidgetMusicPart,
                battery = it.clock.clockWidgetBatteryPart,
                alarm = it.clock.clockWidgetAlarmPart,
            )
        }.distinctUntilChanged()

    fun setDatePart(datePart: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetDatePart = datePart))
        }
    }

    fun setMusicPart(musicPart: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetMusicPart = musicPart))
        }
    }

    fun setBatteryPart(batteryPart: BatteryStatusVisibility) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetBatteryPart = batteryPart))
        }
    }

    fun setAlarmPart(alarmPart: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetAlarmPart = alarmPart))
        }
    }

    val fillHeight
        get() = launcherDataStore.data.map { it.clock.clockWidgetFillHeight || !it.home.homeScreenWidgets }

    fun setFillHeight(fillHeight: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetFillHeight = fillHeight))
        }
    }

    /** Whether the dock part should render below the clock: either the legacy preset-driven
     * auto-favorites dock is enabled, or the user has set up custom dock pages in settings. */
    val dock
        get() = launcherDataStore.data.map { it.home.homeScreenDock || it.home.homeScreenDockPages.isNotEmpty() }
            .distinctUntilChanged()

    val alignment
        get() = launcherDataStore.data.map { it.clock.clockWidgetAlignment }

    fun setAlignment(alignment: ClockWidgetAlignment) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetAlignment = alignment))
        }
    }

    val clockStyle: Flow<ClockWidgetStyle>
        get() = launcherDataStore.data.map {
            when (it.clock.clockWidgetStyle) {
                ClockWidgetStyleEnum.Digital1 -> it.clock.clockWidgetDigital1
                ClockWidgetStyleEnum.Digital2 -> ClockWidgetStyle.Digital2
                ClockWidgetStyleEnum.Orbit -> ClockWidgetStyle.Orbit
                ClockWidgetStyleEnum.Analog -> it.clock.clockWidgetAnalog
                ClockWidgetStyleEnum.Binary -> ClockWidgetStyle.Binary
                ClockWidgetStyleEnum.Segment -> ClockWidgetStyle.Segment
                ClockWidgetStyleEnum.Empty -> ClockWidgetStyle.Empty
                ClockWidgetStyleEnum.Custom -> it.clock.clockWidgetCustom
            }
        }

    val digital1: Flow<ClockWidgetStyle.Digital1>
        get() = launcherDataStore.data.map { it.clock.clockWidgetDigital1 }

    val analog: Flow<ClockWidgetStyle.Analog>
        get() = launcherDataStore.data.map { it.clock.clockWidgetAnalog }

    val custom: Flow<ClockWidgetStyle.Custom>
        get() = launcherDataStore.data.map { it.clock.clockWidgetCustom }

    fun setClockStyle(clockStyle: ClockWidgetStyle) {
        launcherDataStore.update {
            it.copy(
                clock = it.clock.copy(
                    clockWidgetStyle = clockStyle.enumValue,
                    clockWidgetDigital1 = clockStyle as? ClockWidgetStyle.Digital1 ?: it.clock.clockWidgetDigital1,
                    clockWidgetAnalog = clockStyle as? ClockWidgetStyle.Analog ?: it.clock.clockWidgetAnalog,
                    clockWidgetCustom = clockStyle as? ClockWidgetStyle.Custom ?: it.clock.clockWidgetCustom,
                )
            )
        }
    }

    val color
        get() = launcherDataStore.data.map { it.clock.clockWidgetColors }

    fun setColor(color: ClockWidgetColors) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetColors = color))
        }
    }

    val showSeconds
        get() = launcherDataStore.data.map { it.clock.clockWidgetShowSeconds }

    fun setShowSeconds(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetShowSeconds = enabled))
        }
    }

    val monospaced
        get() = launcherDataStore.data.map { it.clock.clockWidgetMonospaced }

    fun setMonospaced(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetMonospaced = enabled))
        }
    }

    val useThemeColor
        get() = launcherDataStore.data.map { it.clock.clockWidgetUseThemeColor }

    fun setUseThemeColor(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetUseThemeColor = enabled))
        }
    }

    val useSmartspacer
        get() = launcherDataStore.data.map { it.clock.clockWidgetSmartspacer }

    fun setUseSmartspacer(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(clock = it.clock.copy(clockWidgetSmartspacer = enabled))
        }
    }

}

internal val ClockWidgetStyle.enumValue
    get() = when (this) {
        is ClockWidgetStyle.Digital1 -> ClockWidgetStyleEnum.Digital1
        is ClockWidgetStyle.Digital2 -> ClockWidgetStyleEnum.Digital2
        is ClockWidgetStyle.Orbit -> ClockWidgetStyleEnum.Orbit
        is ClockWidgetStyle.Analog -> ClockWidgetStyleEnum.Analog
        is ClockWidgetStyle.Binary -> ClockWidgetStyleEnum.Binary
        is ClockWidgetStyle.Segment -> ClockWidgetStyleEnum.Segment
        is ClockWidgetStyle.Empty -> ClockWidgetStyleEnum.Empty
        is ClockWidgetStyle.Custom -> ClockWidgetStyleEnum.Custom
    }
