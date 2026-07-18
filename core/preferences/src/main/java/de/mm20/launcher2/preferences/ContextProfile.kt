package de.mm20.launcher2.preferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A named bundle of overrides (gestures, freeze profile, widget page) that can be applied
 * automatically based on a trigger, or activated manually. Distinct from the Android
 * work/private-profile concept in `core:profiles` - this is purely a launcher-side preset.
 */
@Serializable
data class ContextProfile(
    val id: String,
    val name: String,
    val icon: ContextProfileIcon = ContextProfileIcon.Custom,
    val trigger: ContextProfileTrigger = ContextProfileTrigger.Manual,
    val gestureOverrides: ContextProfileGestureOverrides = ContextProfileGestureOverrides(),
    val freezeProfileOverride: FreezeProfile? = null,
    val widgetScreenTargetOverride: WidgetScreenTarget? = null,
    /** While active: `true` forces Do Not Disturb on, `false` forces it off, `null` doesn't override it. */
    val doNotDisturbOverride: Boolean? = null,
    /** While active, forces screen brightness to this percentage (0-100). `null` doesn't override it. */
    val brightnessOverride: Int? = null,
    /** Key of a searchable (usually an app) to launch once whenever this profile becomes active. */
    val launchAppOverride: String? = null,
)

@Serializable
enum class ContextProfileIcon {
    @SerialName("home") Home,
    @SerialName("work") Work,
    @SerialName("car") Car,
    @SerialName("gaming") Gaming,
    @SerialName("battery_saver") BatterySaver,
    @SerialName("sleep") Sleep,
    @SerialName("custom") Custom,
}

/**
 * Per-slot gesture overrides. Any slot left `null` falls back to the user's normal gesture
 * setting for that slot - a profile doesn't have to override every gesture.
 */
@Serializable
data class ContextProfileGestureOverrides(
    val swipeDown: GestureAction? = null,
    val swipeLeft: GestureAction? = null,
    val swipeRight: GestureAction? = null,
    val swipeUp: GestureAction? = null,
    val doubleTap: GestureAction? = null,
    val longPress: GestureAction? = null,
) {
    val isEmpty: Boolean
        get() = swipeDown == null && swipeLeft == null && swipeRight == null &&
                swipeUp == null && doubleTap == null && longPress == null
}

/**
 * How a [ContextProfile] gets activated. Evaluated only while the launcher is in the
 * foreground (on resume, and periodically while visible) - there is no persistent background
 * service polling for triggers, so a profile change can lag behind the actual trigger condition
 * by up to the evaluation interval, and Bluetooth/time-window triggers only take effect while
 * the launcher process is alive.
 */
@Serializable
sealed interface ContextProfileTrigger {
    @Serializable
    @SerialName("manual")
    data object Manual : ContextProfileTrigger

    @Serializable
    @SerialName("time")
    data class TimeWindow(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
    ) : ContextProfileTrigger

    /** Active while connected to any of [ssids]. Requires location permission to read the SSID. */
    @Serializable
    @SerialName("wifi")
    data class Wifi(val ssids: Set<String> = emptySet()) : ContextProfileTrigger

    /** Active while any of [deviceNames] is connected. Requires the Bluetooth connect permission. */
    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(val deviceNames: Set<String> = emptySet()) : ContextProfileTrigger

    @Serializable
    @SerialName("battery_saver")
    data object BatterySaver : ContextProfileTrigger

    /**
     * Active while the device is charging. Android doesn't expose the identity of a specific
     * charger/USB device, only the connection type it reports - [ChargingType] is as specific as
     * this can get.
     */
    @Serializable
    @SerialName("charging")
    data class Charging(val type: ChargingType = ChargingType.Any) : ContextProfileTrigger
}

@Serializable
enum class ChargingType {
    @SerialName("any") Any,
    @SerialName("usb") Usb,
    @SerialName("ac") Ac,
    @SerialName("wireless") Wireless,
}
