package de.mm20.launcher2.ui.launcher

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.contextprofiles.ContextProfileEffectsApplier
import de.mm20.launcher2.contextprofiles.ContextProfileManager
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {

    private val uiSettings: UiSettings by inject()
    private val wallpaperSettings: WallpaperSettings by inject()
    private val gestureSettings: GestureSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val contextProfileManager: ContextProfileManager by inject()
    private val contextProfileEffectsApplier: ContextProfileEffectsApplier by inject()

    init {
        viewModelScope.launch {
            contextProfileEffectsApplier.start()
        }
    }

    private var isSystemInDarkMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        wallpaperSettings.dimWallpaper,
        uiSettings.colorScheme,
        isSystemInDarkMode,
        uiSettings.colorSchemeNightStart,
        uiSettings.colorSchemeDayStart,
    ) { dim, theme, systemDarkMode, nightStart, dayStart ->
        val isDark = when (theme) {
            ColorScheme.Dark -> true
            ColorScheme.Light -> false
            ColorScheme.System -> systemDarkMode
            ColorScheme.Time -> {
                val hour = java.time.LocalTime.now().hour
                nightStart != dayStart &&
                    ((hour - nightStart) + 24) % 24 < ((dayStart - nightStart) + 24) % 24
            }
        }
        dim && isDark
    }
    val dimBackground = dimBackgroundState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val statusBarColor = uiSettings.statusBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val navBarColor = uiSettings.navigationBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val chargingAnimation = uiSettings.chargingAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hideNavBar = uiSettings.hideNavigationBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val hideStatusBar = uiSettings.hideStatusBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val activeContextProfile = contextProfileManager.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSystemInDarkMode(darkMode: Boolean) {
        isSystemInDarkMode.value = darkMode
    }

    val bottomSearchBar = uiSettings.bottomSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val reverseSearchResults = uiSettings.reverseSearchResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedSearchBar = uiSettings.fixedSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedRotation = uiSettings.orientation
        .map { it != ScreenOrientation.Auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val widgetsOnHomeScreen = uiSettings.homeScreenWidgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val autoFocusSearch = uiSettings.openKeyboardOnSearch

    val wallpaperBlur = wallpaperSettings.blurWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val wallpaperBlurRadius = wallpaperSettings.wallpaperBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 32)


    val fillClockHeight = uiSettings.clockFillScreen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val searchBarColor = uiSettings.searchBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SearchBarColors.Auto)
    val searchBarColorDrawer = uiSettings.searchBarColorDrawer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val searchBarStyle = uiSettings.searchBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SearchBarStyle.Transparent)

    val gestureState: StateFlow<GestureState?> = combine(
        gestureSettings,
        contextProfileManager.activeProfile,
    ) { settings, activeProfile ->
            val overrides = activeProfile?.gestureOverrides
            val swipeLeftAction = overrides?.swipeLeft ?: settings.swipeLeft
            val swipeRightAction = overrides?.swipeRight ?: settings.swipeRight
            val swipeDownAction = overrides?.swipeDown ?: settings.swipeDown
            val swipeUpAction = overrides?.swipeUp ?: settings.swipeUp
            val longPressAction = overrides?.longPress ?: settings.longPress
            val doubleTapAction = overrides?.doubleTap ?: settings.doubleTap
            val homeButtonAction = settings.homeButton
            val pinchInAction = settings.pinchIn
            val pinchOutAction = settings.pinchOut
            val twoFingerSwipeUpAction = settings.twoFingerSwipeUp
            val twoFingerSwipeDownAction = settings.twoFingerSwipeDown

            val swipeLeftAppKey = (swipeLeftAction as? GestureAction.Launch)?.key
            val swipeRightAppKey = (swipeRightAction as? GestureAction.Launch)?.key
            val swipeDownAppKey = (swipeDownAction as? GestureAction.Launch)?.key
            val swipeUpAppKey = (swipeUpAction as? GestureAction.Launch)?.key
            val longPressAppKey = (longPressAction as? GestureAction.Launch)?.key
            val doubleTapAppKey = (doubleTapAction as? GestureAction.Launch)?.key
            val homeButtonAppKey = (homeButtonAction as? GestureAction.Launch)?.key
            val pinchInAppKey = (pinchInAction as? GestureAction.Launch)?.key
            val pinchOutAppKey = (pinchOutAction as? GestureAction.Launch)?.key
            val twoFingerSwipeUpAppKey = (twoFingerSwipeUpAction as? GestureAction.Launch)?.key
            val twoFingerSwipeDownAppKey = (twoFingerSwipeDownAction as? GestureAction.Launch)?.key
            val apps = listOfNotNull(
                swipeLeftAppKey,
                swipeRightAppKey,
                swipeDownAppKey,
                swipeUpAppKey,
                longPressAppKey,
                doubleTapAppKey,
                homeButtonAppKey,
                pinchInAppKey,
                pinchOutAppKey,
                twoFingerSwipeUpAppKey,
                twoFingerSwipeDownAppKey,
            ).let { searchableRepository.getByKeys(it).first() }

            GestureState(
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                swipeDownAction = swipeDownAction,
                swipeUpAction = swipeUpAction,
                longPressAction = longPressAction,
                doubleTapAction = doubleTapAction,
                homeButtonAction = homeButtonAction,
                pinchInAction = pinchInAction,
                pinchOutAction = pinchOutAction,
                twoFingerSwipeUpAction = twoFingerSwipeUpAction,
                twoFingerSwipeDownAction = twoFingerSwipeDownAction,
                swipeLeftApp = apps.find { it.key == swipeLeftAppKey },
                swipeRightApp = apps.find { it.key == swipeRightAppKey },
                swipeDownApp = apps.find { it.key == swipeDownAppKey },
                swipeUpApp = apps.find { it.key == swipeUpAppKey },
                longPressApp = apps.find { it.key == longPressAppKey },
                doubleTapApp = apps.find { it.key == doubleTapAppKey },
                homeButtonApp = apps.find { it.key == homeButtonAppKey },
                pinchInApp = apps.find { it.key == pinchInAppKey },
                pinchOutApp = apps.find { it.key == pinchOutAppKey },
                twoFingerSwipeUpApp = apps.find { it.key == twoFingerSwipeUpAppKey },
                twoFingerSwipeDownApp = apps.find { it.key == twoFingerSwipeDownAppKey },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

data class GestureState(
    val swipeLeftAction: GestureAction = GestureAction.NoAction,
    val swipeRightAction: GestureAction = GestureAction.NoAction,
    val swipeDownAction: GestureAction = GestureAction.NoAction,
    val swipeUpAction: GestureAction = GestureAction.NoAction,
    val longPressAction: GestureAction = GestureAction.NoAction,
    val doubleTapAction: GestureAction = GestureAction.NoAction,
    val homeButtonAction: GestureAction = GestureAction.NoAction,
    val pinchInAction: GestureAction = GestureAction.NoAction,
    val pinchOutAction: GestureAction = GestureAction.NoAction,
    val twoFingerSwipeUpAction: GestureAction = GestureAction.NoAction,
    val twoFingerSwipeDownAction: GestureAction = GestureAction.NoAction,
    val swipeLeftApp: SavableSearchable? = null,
    val swipeRightApp: SavableSearchable? = null,
    val swipeDownApp: SavableSearchable? = null,
    val swipeUpApp: SavableSearchable? = null,
    val longPressApp: SavableSearchable? = null,
    val doubleTapApp: SavableSearchable? = null,
    val homeButtonApp: SavableSearchable? = null,
    val pinchInApp: SavableSearchable? = null,
    val pinchOutApp: SavableSearchable? = null,
    val twoFingerSwipeUpApp: SavableSearchable? = null,
    val twoFingerSwipeDownApp: SavableSearchable? = null,
)

