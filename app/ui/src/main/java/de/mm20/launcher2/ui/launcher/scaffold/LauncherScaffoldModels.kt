package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.ui.launcher.scaffold.components.ScaffoldComponent
import de.mm20.launcher2.ui.launcher.scaffold.components.SearchComponent

enum class ScaffoldAnimation {
    Rubberband,
    Push,
    ZoomIn,
}

internal data class ScaffoldGesture(
    val component: ScaffoldComponent,
    val animation: ScaffoldAnimation,
)

enum class SearchBarPosition {
    Top,
    Bottom,
}

internal data class ScaffoldConfiguration(
    /**
     * The main component
     */
    val homeComponent: ScaffoldComponent,
    /**
     * Search component that is activated when the search bar is tapped.
     */
    val searchComponent: SearchComponent,
    val swipeUp: ScaffoldGesture? = null,
    val swipeDown: ScaffoldGesture? = null,
    val swipeLeft: ScaffoldGesture? = null,
    val swipeRight: ScaffoldGesture? = null,
    val doubleTap: ScaffoldGesture? = null,
    val longPress: ScaffoldGesture? = null,
    val homeButton: ScaffoldGesture? = null,
    val pinchIn: ScaffoldGesture? = null,
    val pinchOut: ScaffoldGesture? = null,
    val twoFingerSwipeUp: ScaffoldGesture? = null,
    val twoFingerSwipeDown: ScaffoldGesture? = null,
    /**
     * Position of the search bar
     */
    val searchBarPosition: SearchBarPosition = SearchBarPosition.Top,
    val searchBarStyle: SearchBarStyle = SearchBarStyle.Hidden,
    /**
     * If true, the search bar does not scroll out of view
     */
    val fixedSearchBar: Boolean = false,
    /**
     * The color that is used as background for secondary pages.
     */
    val backgroundColor: Color,
    /**
     * Wallpaper blur radius. 0 to disable.
     */
    val wallpaperBlurRadius: Dp = 32.dp,
    /**
     * Show the navigation bar
     */
    val showNavBar: Boolean = true,
    val darkNavBarIcons: Boolean = false,
    /**
     * Show the status bar
     */
    val showStatusBar: Boolean = true,
    val darkStatusBarIcons: Boolean = false,
    /**
     * Finishes the activity when back is pressed while on home component.
     * Used for assistant mode.
     */
    val finishOnBack: Boolean = false,
    val darkSearchBar: Boolean = false,
    val darkSearchBarDrawer: Boolean? = null,
) {
    val searchBarTap = ScaffoldGesture(
        component = searchComponent,
        animation = ScaffoldAnimation.ZoomIn,
    )

    /**
     * Returns true if the given config prevents the user from accessing search or settings,
     * so that the user is locked out and the launcher is soft-bricked
     */
    fun isUseless(): Boolean {
        return searchBarStyle == SearchBarStyle.Hidden &&
                listOfNotNull(
                    swipeUp,
                    swipeDown,
                    swipeLeft,
                    swipeRight,
                    doubleTap,
                    longPress,
                    homeButton,
                    pinchIn,
                    pinchOut,
                    twoFingerSwipeUp,
                    twoFingerSwipeDown,
                ).none { it.component.showSearchBar }
    }
}

internal operator fun ScaffoldConfiguration.get(gesture: Gesture): ScaffoldGesture? {
    return when (gesture) {
        Gesture.SwipeUp -> swipeUp
        Gesture.SwipeDown -> swipeDown
        Gesture.SwipeLeft -> swipeLeft
        Gesture.SwipeRight -> swipeRight
        Gesture.DoubleTap -> doubleTap
        Gesture.LongPress -> longPress
        Gesture.HomeButton -> homeButton
        Gesture.TapSearchBar -> searchBarTap
        Gesture.PinchIn -> pinchIn
        Gesture.PinchOut -> pinchOut
        Gesture.TwoFingerSwipeUp -> twoFingerSwipeUp
        Gesture.TwoFingerSwipeDown -> twoFingerSwipeDown
    }
}

enum class Gesture(val orientation: Orientation?) {
    SwipeUp(Orientation.Vertical),
    SwipeDown(Orientation.Vertical),
    SwipeLeft(Orientation.Horizontal),
    SwipeRight(Orientation.Horizontal),
    DoubleTap(null),
    LongPress(null),
    TapSearchBar(null),
    HomeButton(null),

    // Multi-finger gestures fire like tap gestures (no page drag), hence orientation = null.
    PinchIn(null),
    PinchOut(null),
    TwoFingerSwipeUp(null),
    TwoFingerSwipeDown(null),
}
