package de.mm20.launcher2.preferences

import android.content.Context
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.serialization.ColorIntAsHexSerializer
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

@Serializable
@ConsistentCopyVisibility
data class LauncherSettingsData internal constructor(
    val schemaVersion: Int = 9,

    val uiColorScheme: ColorScheme = ColorScheme.System,
    /** Hour of day [0,23] when the dark scheme starts, used only by [ColorScheme.Time]. */
    val uiColorSchemeNightStart: Int = 20,
    /** Hour of day [0,23] when the light scheme starts, used only by [ColorScheme.Time]. */
    val uiColorSchemeDayStart: Int = 7,
    @Serializable(with = UUIDSerializer::class)
    val uiColorsId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiShapesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTransparenciesId: UUID = UUID(0L, 0L),
    @Serializable(with = UUIDSerializer::class)
    val uiTypographyId: UUID = UUID(0L, 0L),

    val uiCompatModeColors: Boolean = false,
    @Deprecated("No longer in use, only used for migration")
    val uiBaseLayout: BaseLayout = BaseLayout.PullDown,
    val uiOrientation: ScreenOrientation = ScreenOrientation.Auto,

    val wallpaperDim: Boolean = false,
    val wallpaperBlur: Boolean = true,
    val wallpaperBlurRadius: Int = 32,

    val mediaAllowList: Set<String> = emptySet(),
    val mediaDenyList: Set<String> = emptySet(),

    val clockWidgetCompact: Boolean = false,
    val clockWidgetSmartspacer: Boolean = false,

    @Deprecated("")
    @SerialName("clockWidgetStyle")
    val _clockWidgetStyle: ClockWidgetStyle = ClockWidgetStyle.Digital1(),
    @SerialName("clockWidgetStyle2")
    internal val clockWidgetStyle: ClockWidgetStyleEnum = ClockWidgetStyleEnum.Digital1,
    val clockWidgetDigital1: ClockWidgetStyle.Digital1 = ClockWidgetStyle.Digital1(),
    val clockWidgetAnalog: ClockWidgetStyle.Analog = ClockWidgetStyle.Analog(),
    val clockWidgetCustom: ClockWidgetStyle.Custom = ClockWidgetStyle.Custom(),
    val clockWidgetColors: ClockWidgetColors = ClockWidgetColors.Auto,
    val clockWidgetShowSeconds: Boolean = false,
    val clockWidgetMonospaced: Boolean = false,
    val clockWidgetUseThemeColor: Boolean = false,
    val clockWidgetAlarmPart: Boolean = true,
    @Deprecated("")
    @SerialName("clockWidgetBatteryPart")
    val _clockWidgetBatteryPart: Boolean = true,
    @SerialName("clockWidgetBatteryPart2")
    val clockWidgetBatteryPart: BatteryStatusVisibility = BatteryStatusVisibility.Show,
    val clockWidgetMusicPart: Boolean = true,
    val clockWidgetDatePart: Boolean = true,
    val clockWidgetFillHeight: Boolean = false,
    val clockWidgetAlignment: ClockWidgetAlignment = ClockWidgetAlignment.Bottom,

    val homeScreenDock: Boolean = false,
    val homeScreenDockRows: Int = 1,
    val homeScreenWidgets: Boolean = false,
    val widgetsTutorialShown: Boolean = false,
    /** 1 = the single, original home screen (default, matches all prior behavior). Up to 9
     * enables extra swipeable pages to the right, each an independent widget area. */
    val homeScreenPageCount: Int = 1,

    val favoritesEnabled: Boolean = true,
    val favoritesFrequentlyUsed: Boolean = true,
    val favoritesFrequentlyUsedRows: Int = 1,
    val favoritesEditButton: Boolean = true,
    val favoritesCompactTags: Boolean = false,

    val searchAllApps: Boolean = true,
    val appsShowDetails: Boolean = true,

    val fileSearchProviders: Set<String> = setOf("local"),
    val fileSearchDocuments: Boolean = true,
    val fileSearchImages: Boolean = true,
    val fileSearchVideos: Boolean = true,
    val fileSearchMusic: Boolean = true,
    val fileSearchOther: Boolean = true,
    val fileSearchExcludedFolders: Set<String> = emptySet(),

    @Deprecated("Use contactSearchProviders `local` instead")
    val contactSearchEnabled: Boolean = true,
    val contactSearchProviders: Set<String> = setOf("local"),
    val contactSearchCallOnTap: Boolean = false,

    @Deprecated("Use calendarSearchProviders `local` instead")
    val calendarSearchEnabled: Boolean = true,
    val calendarSearchProviders: Set<String> = setOf("local"),
    val calendarSearchExcludedCalendars: Set<String> = setOf(),

    val shortcutSearchEnabled: Boolean = true,

    val calculatorEnabled: Boolean = true,

    val unitConverterEnabled: Boolean = true,
    val unitConverterCurrencies: Boolean = true,

    val wikipediaSearchEnabled: Boolean = true,
    val wikipediaSearchImages: Boolean = true,
    val wikipediaCustomUrl: String? = null,

    val websiteSearchEnabled: Boolean = true,

    val badgesNotifications: Boolean = true,
    val badgesSuspendedApps: Boolean = true,
    val badgesCloudFiles: Boolean = true,
    val badgesShortcuts: Boolean = true,
    val badgesPlugins: Boolean = true,
    val badgesNotificationStyle: NotificationBadgeStyle = NotificationBadgeStyle.Dot,
    /** Null = follow the theme's tertiary color (the pre-existing default look). */
    @Serializable(with = ColorIntAsHexSerializer::class)
    val badgesNotificationColor: Int? = null,

    val gridColumnCount: Int = 5,
    val gridIconSize: Int = 48,
    val gridLabels: Boolean = true,
    val gridList: Boolean = false,
    val gridListIcons: Boolean = true,

    val searchBarStyle: SearchBarStyle = SearchBarStyle.Transparent,
    val searchBarColors: SearchBarColors = SearchBarColors.Auto,
    val searchBarKeyboard: Boolean = true,
    val searchLaunchOnEnter: Boolean = true,
    val searchBarBottom: Boolean = false,
    val searchBarFixed: Boolean = false,

    val searchResultsReversed: Boolean = false,
    val separateWorkProfile: Boolean = true,

    val rankingWeightFactor: WeightFactor = WeightFactor.Default,

    val hiddenItemsShowButton: Boolean = false,

    val iconsShape: IconShape = IconShape.PlatformDefault,
    val iconsAdaptify: Boolean = false,
    val iconsThemed: Boolean = false,
    val iconsForceThemed: Boolean = false,
    val iconsPack: String? = null,
    @Deprecated("Use iconsThemed instead")
    val iconsPackThemed: Boolean = false,

    val easterEgg: Boolean = false,

    val systemBarsHideStatus: Boolean = false,
    val systemBarsHideNav: Boolean = false,
    val systemBarsStatusColors: SystemBarColors = SystemBarColors.Auto,
    val systemBarsNavColors: SystemBarColors = SystemBarColors.Auto,

    val surfacesOpacity: Float = 1f,
    @Deprecated("Replaces with shape schemes")
    val surfacesRadius: Int = 24,
    val surfacesBorderWidth: Int = 0,
    @Deprecated("Replaces with shape schemes")
    val surfacesShape: SurfaceShape = SurfaceShape.Rounded,

    val widgetsEditButton: Boolean = true,
    val widgetScreenCount: Int = 1,

    val gesturesSwipeDown: GestureAction = GestureAction.Search,
    val gesturesSwipeLeft: GestureAction = GestureAction.NoAction,
    val gesturesSwipeRight: GestureAction = GestureAction.NoAction,
    val gesturesSwipeUp: GestureAction = GestureAction.Widgets(),
    val gesturesDoubleTap: GestureAction = GestureAction.ScreenLock,
    val gesturesLongPress: GestureAction = GestureAction.NoAction,
    val gesturesHomeButton: GestureAction = GestureAction.NoAction,
    val gesturesPinchIn: GestureAction = GestureAction.NoAction,
    val gesturesPinchOut: GestureAction = GestureAction.NoAction,
    val gesturesTwoFingerSwipeUp: GestureAction = GestureAction.NoAction,
    val gesturesTwoFingerSwipeDown: GestureAction = GestureAction.NoAction,

    val videoWallpaperPauseOnBatterySaver: Boolean = true,
    val videoWallpaperPauseOnThermalThrottling: Boolean = true,
    val videoWallpaperScalingMode: VideoWallpaperScalingMode = VideoWallpaperScalingMode.Fill,
    val videoWallpaperZoom: Float = 1f,
    val videoWallpaperPositionX: Float = 0f,
    val videoWallpaperPositionY: Float = 0f,
    val videoWallpaperBrightness: Float = 1f,
    val videoWallpaperSpeed: Float = 1f,
    val videoWallpaperStartBehavior: VideoWallpaperStartBehavior = VideoWallpaperStartBehavior.Resume,
    val videoWallpaperParallax: Boolean = false,
    val videoWallpaperParallaxStrength: Float = 0.2f,
    val videoWallpaperThemeColors: Boolean = true,
    /**
     * Off by default: the video wallpaper's WallpaperService is tied to the default display and
     * physically cannot render on the external display desktop mode uses, but the phone screen
     * itself keeps showing it while desktop mode is active on an external display, so pausing it
     * is a resource-saving choice the user should opt into rather than something sprung on them.
     */
    val videoWallpaperPauseOnDesktopMode: Boolean = false,

    val performanceReduceAnimations: Boolean = false,
    /** Multiplies the duration of tween-based (fade/effects) motion specs. 1.0 = default. */
    val performanceAnimationSpeed: Float = 1f,
    /** 0 = no debounce (search fires on every keystroke, the historical behavior). */
    val performanceSearchDebounceMs: Int = 0,
    val performanceIconCacheSize: Int = 200,

    val shuttersEnabled: Boolean = false,
    val shutterWidgets: Map<String, ShutterWidgetRef> = emptyMap(),

    val animationsCharging: Boolean = true,

    val stateTagsMultiline: Boolean = false,

    val weatherProvider: String = "openmeteo",
    val weatherAutoLocation: Boolean = true,
    val weatherLocation: LatLon? = null,
    val weatherLocationName: String? = null,
    val weatherLastLocation: LatLon? = null,
    val weatherLastUpdate: Long = 0L,
    val weatherProviderSettings: Map<String, ProviderSettings> = emptyMap(),

    @Deprecated("Use locationSearchProviders instead")
    val locationSearchEnabled: Boolean = false,
    val locationSearchProviders: Set<String> = setOf("openstreetmaps"),
    val locationSearchRadius: Int = 1500,
    val locationSearchHideUncategorized: Boolean = true,
    val locationSearchOverpassUrl: String? = null,
    val locationSearchTileServer: String? = null,
    val locationSearchShowMap: Boolean = true,
    val locationSearchShowPositionOnMap: Boolean = false,
    val locationSearchThemeMap: Boolean = true,

    val searchFilter: SearchFilters = SearchFilters(),
    val searchFilterBar: Boolean = true,
    val searchFilterBarItems: List<KeyboardFilterBarItem> = listOf(
        KeyboardFilterBarItem.Apps,
        KeyboardFilterBarItem.Files,
        KeyboardFilterBarItem.Contacts,
        KeyboardFilterBarItem.OnlineResults,
        KeyboardFilterBarItem.Shortcuts,
        KeyboardFilterBarItem.Events,
        KeyboardFilterBarItem.Reminders,
        KeyboardFilterBarItem.Documents,
        KeyboardFilterBarItem.Images,
        KeyboardFilterBarItem.Video,
        KeyboardFilterBarItem.Music,
        KeyboardFilterBarItem.Articles,
        KeyboardFilterBarItem.Websites,
        KeyboardFilterBarItem.Places,
        KeyboardFilterBarItem.Tools,
        KeyboardFilterBarItem.HiddenResults,
    ),


    @JsonNames("clockWidgetTimeFormat")
    val localeTimeFormat: TimeFormat = TimeFormat.System,
    val localeMeasurementSystem: MeasurementSystem = MeasurementSystem.System,
    /**
     * The ID of the transliterator to use. The empty string means to pick a transliterator
     * automatically. null disables the transliterator.
     */
    val localeTransliterator: String? = "",

    /**
     * The ICU id of the primary calendar. `null` to use the default.
     */
    val localePrimaryCalendar: String? = null,

    /**
     * The ICU id of the secondary calendar. `null` to disable.
     */
    val localeSecondaryCalendar: String? = null,

    /**
     * Preferred currencies. These currencies are listed first in the currency converters.
     * ISO 4217 codes, e.g. "USD" for US Dollar, "EUR" for Euro, etc.
     * If empty, the default order is determined by the system locale.
     */
    val localeCurrencies: List<String> = emptyList(),

    val feedProviderPackage: String? = null,

    val freezeBackend: FreezeBackendPreference = FreezeBackendPreference.Auto,
    val freezeAutoFreezeEnabled: Boolean = false,
    val freezeOnScreenOff: Boolean = false,
    val freezeOnIdle: Boolean = false,
    val freezeIdleTimeoutMinutes: Int = 15,
    val freezeOnBatterySaver: Boolean = false,
    val freezeCandidates: Set<String> = emptySet(),
    val freezeProfile: FreezeProfile = FreezeProfile.Balanced,
    val freezeExclusionStrictness: FreezeExclusionStrictness = FreezeExclusionStrictness.Strict,
    val freezeNeverFreezeApps: Set<String> = emptySet(),
    val freezeStats: Map<String, FreezeAppStats> = emptyMap(),
    val freezeExcludeMusic: Boolean = true,
    val freezeExcludeNetwork: Boolean = true,
    val freezeNetworkThresholdKb: Int = 100,
    val freezeMethods: Map<String, FreezeMethod> = emptyMap(),
    val freezeAdvancedFeaturesEnabled: Boolean = false,

    val protectionLockSensitiveSettings: Boolean = false,
    val protectionLockMethod: SettingsLockMethod = SettingsLockMethod.DeviceCredential,
    val protectionUseCustomLock: Boolean = false,
    val protectionCustomLockHashed: String? = null,
    val protectionLockLauncher: Boolean = false,

    val floatingLauncherEnabled: Boolean = false,
    val floatingLauncherEdge: FloatingLauncherEdge = FloatingLauncherEdge.Right,
    val floatingLauncherPosition: Float = 0.5f,
    val floatingLauncherThickness: Int = 24,
    @Serializable(with = ColorIntAsHexSerializer::class)
    val floatingLauncherColor: Int = 0xFF6750A4.toInt(),
    val floatingLauncherAlpha: Float = 0.6f,

    val contextProfilesEnabled: Boolean = false,
    val contextProfiles: List<ContextProfile> = emptyList(),
    /** If set, this profile is force-active regardless of trigger evaluation. */
    val contextProfileManualOverrideId: String? = null,

    val desktopModeEnabled: Boolean = false,
    /** True once the user has explicitly touched the enable toggle (either direction), or
     * auto-enable has already fired once - prevents auto-enable from firing repeatedly or
     * fighting a deliberate opt-out. */
    val desktopModeUserConfigured: Boolean = false,
    val desktopModeOrientation: DesktopModeOrientation = DesktopModeOrientation.Auto,
    /**
     * User's intent for freeform windowing, separate from the OS-level `enable_freeform_support`
     * setting this drives - the two can drift apart (e.g. another app changed the system setting)
     * so this is only ever the last state the user explicitly chose via the toggle.
     */
    val desktopModeFreeformEnabled: Boolean = false,
    /** Separate from the phone's wallpaper - the phone's video wallpaper can't render on the
     * external display, and a phone-shaped video wouldn't fit a landscape monitor anyway. */
    val desktopWallpaperMode: DesktopWallpaperMode = DesktopWallpaperMode.SolidColor,
    val desktopWallpaperImageUri: String? = null,
    /** Independent of the phone's own gridIconSize - external displays are bigger and viewed
     * from farther away, so the same icon size wouldn't make sense on both. */
    val desktopGridIconSize: Int = 48,

    ) {
    constructor(
        context: Context,
    ) : this(
        gridColumnCount = context.resources.getInteger(R.integer.config_columnCount),
    )
}

@Serializable
enum class ColorScheme {
    Light,
    Dark,
    System,
    /** Switches between light and dark automatically based on the time of day. */
    Time,
}

internal enum class ClockWidgetStyleEnum {
    Digital1,
    Digital2,
    Orbit,
    Analog,
    Binary,
    Segment,
    Empty,
    Custom,
}

@Serializable
sealed interface ClockWidgetStyle {
    @Serializable
    @SerialName("digital1")
    data class Digital1(
        val outlined: Boolean = false,
        @Deprecated("Variant.MDY has been replaced with LauncherSettingsData.clockWidgetUseThemeColor")
        val variant: Variant = Variant.Default,
    ) : ClockWidgetStyle {
        @Serializable
        @Deprecated("No longer in use")
        enum class Variant {
            Default,
            MDY,
        }
    }

    @Serializable
    @SerialName("digital2")
    data object Digital2 : ClockWidgetStyle

    @Serializable
    @SerialName("orbit")
    data object Orbit : ClockWidgetStyle

    @Serializable
    @SerialName("analog")
    data class Analog(
        val showTicks: Boolean = false
    ) : ClockWidgetStyle

    @Serializable
    @SerialName("binary")
    data object Binary : ClockWidgetStyle

    @Serializable
    @SerialName("segment")
    data object Segment : ClockWidgetStyle

    @Serializable
    @SerialName("empty")
    data object Empty : ClockWidgetStyle

    @Serializable
    @SerialName("custom")
    data class Custom(
        val widgetId: Int? = null,
        val width: Int? = null,
        val height: Int = 200,
    ) : ClockWidgetStyle
}

@Serializable
enum class ClockWidgetColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class ClockWidgetAlignment {
    Top,
    Center,
    Bottom,
}

@Serializable
enum class SearchBarStyle {
    Transparent,
    Solid,
    Hidden,
}

@Serializable
enum class SearchBarColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class IconShape {
    PlatformDefault,
    Circle,
    Square,
    RoundedSquare,
    Triangle,
    Squircle,
    Hexagon,
    Pentagon,
    Teardrop,
    Pebble,
    EasterEgg,
}

@Serializable
enum class SystemBarColors {
    Auto,
    Light,
    Dark,
}

@Serializable
enum class SurfaceShape {
    Rounded,
    Cut,
}

@Serializable
enum class BaseLayout {
    PullDown,
    Pager,
    PagerReversed,
}

@Serializable
enum class ScreenOrientation {
    Auto,
    Portrait,
    Landscape,
}

@Serializable
sealed interface GestureAction {
    @Serializable
    @SerialName("no_action")
    data object NoAction : GestureAction

    @Serializable
    @SerialName("notifications")
    data object Notifications : GestureAction

    @Serializable
    @SerialName("quick_settings")
    data object QuickSettings : GestureAction

    @Serializable
    @SerialName("screen_lock")
    data object ScreenLock : GestureAction

    @Serializable
    @SerialName("search")
    data object Search : GestureAction

    @Serializable
    @SerialName("widgets")
    data class Widgets(val target: WidgetScreenTarget = WidgetScreenTarget.Default) : GestureAction

    @Serializable
    @SerialName("power_menu")
    data object PowerMenu : GestureAction

    @Serializable
    @SerialName("recents")
    data object Recents : GestureAction

    @Serializable
    @SerialName("launch_searchable")
    data class Launch(val key: String?) : GestureAction

    @Serializable
    @SerialName("feed")
    data object Feed : GestureAction

    @Serializable
    @SerialName("launcher_settings")
    data object LauncherSettings : GestureAction

    /** Invokes the action with id [actionId] on the GestureAction plugin at [authority]. */
    @Serializable
    @SerialName("plugin_action")
    data class Plugin(val authority: String, val actionId: String) : GestureAction
}


@Serializable
enum class WeightFactor {
    Default,
    Low,
    High,
}

@Serializable
data class LatLon(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class ProviderSettings(
    val locationId: String? = null,
    val locationName: String? = null,
    val managedLocation: Boolean = false,
)

@Serializable
enum class KeyboardFilterBarItem {
    @SerialName("online") OnlineResults,
    @SerialName("apps") Apps,
    @SerialName("websites") Websites,
    @SerialName("articles") Articles,
    @SerialName("places") Places,
    @SerialName("files") Files,
    @SerialName("documents") Documents,
    @SerialName("images") Images,
    @SerialName("video") Video,
    @SerialName("music") Music,
    @SerialName("shortcuts") Shortcuts,
    @SerialName("contacts") Contacts,
    @SerialName("events") Events,
    @SerialName("reminders") Reminders,
    @SerialName("tools") Tools,
    @SerialName("hidden") HiddenResults,
}

@Serializable
enum class TimeFormat {
    @SerialName("system") System,
    @SerialName("12h") TwelveHour,
    @SerialName("24h") TwentyFourHour
}


@Serializable
enum class MeasurementSystem {
    @SerialName("system") System,
    @SerialName("metric") Metric,
    @SerialName("uk") UnitedKingdom,
    @SerialName("us") UnitedStates,
}

@Serializable
enum class BatteryStatusVisibility {
    @SerialName("hide") Hide,
    @SerialName("show") Show,
    @SerialName("always") Always
}

@Serializable
enum class FreezeBackendPreference {
    @SerialName("auto") Auto,
    @SerialName("shizuku") ShizukuOnly,
    @SerialName("root") RootOnly,
    @SerialName("island") Island,
}

/**
 * How protected settings screens are unlocked. Both modes go through the system's
 * BiometricPrompt - the launcher never stores or verifies credentials itself.
 */
@Serializable
enum class SettingsLockMethod {
    /** System biometrics, falling back to the device PIN/pattern/password. */
    @SerialName("device_credential") DeviceCredential,

    /** Biometrics only: the device PIN/pattern is deliberately not accepted. */
    @SerialName("biometrics_only") BiometricsOnly,
}

/** Which screen edge the floating quick launcher's collapsed tab attaches to. */
@Serializable
enum class FloatingLauncherEdge {
    @SerialName("left") Left,
    @SerialName("right") Right,
}

/** Requested orientation for the desktop shell activity on the external display. */
@Serializable
enum class DesktopModeOrientation {
    @SerialName("auto") Auto,
    @SerialName("portrait") Portrait,
    @SerialName("landscape") Landscape,
}

enum class DesktopWallpaperMode {
    /** Solid theme-color background - the default, since it needs no picked image. */
    @SerialName("solid_color") SolidColor,
    /** A separate static image, picked independently of the phone's wallpaper. */
    @SerialName("static_image") StaticImage,
}

@Serializable
enum class VideoWallpaperScalingMode {
    /** Letterbox: whole video visible, black bars if aspect ratios differ. */
    @SerialName("fit") Fit,
    /** Crop to cover the whole screen. */
    @SerialName("fill") Fill,
    /** Distort to exactly match the screen. */
    @SerialName("stretch") Stretch,
}

/**
 * Just enough to re-look-up an already-bound app widget's [android.appwidget.AppWidgetProviderInfo]
 * via [android.appwidget.AppWidgetManager] at render time. Deliberately independent of the
 * Room-backed home-screen widget system (WidgetRepository) - a shutter is a per-app, opt-in,
 * transient popup, not a home-screen widget.
 */
@Serializable
data class ShutterWidgetRef(
    val widgetId: Int,
    val providerPackage: String,
    val providerClassName: String,
)

@Serializable
enum class NotificationBadgeStyle {
    /** A plain colored dot, no number. This is the pre-existing look most users already see,
     *  since most apps never set a meaningful Notification.number, so summed counts were
     *  usually 0 anyway. */
    @SerialName("dot") Dot,
    /** The number of active (non-summary) notifications from the app. */
    @SerialName("count") Count,
}

@Serializable
enum class VideoWallpaperStartBehavior {
    @SerialName("resume") Resume,
    @SerialName("restart") Restart,
    @SerialName("random") Random,
}


@Serializable
enum class FreezeProfile {
    @SerialName("battery_saver") BatterySaver,
    @SerialName("balanced") Balanced,
    @SerialName("aggressive") Aggressive,
    @SerialName("ultra_aggressive") UltraAggressive,
    @SerialName("custom") Custom,
}

@Serializable
enum class FreezeExclusionStrictness {
    @SerialName("strict") Strict,
    @SerialName("relaxed") Relaxed,
}

@Serializable
enum class FreezeMethod {
    @SerialName("suspend") Suspend,
    @SerialName("disable") Disable,
}

@Serializable
data class FreezeAppStats(
    val freezeCount: Int = 0,
    val unfreezeCount: Int = 0,
    val lastFrozenAt: Long? = null,
    val lastUnfrozenAt: Long? = null,
)
