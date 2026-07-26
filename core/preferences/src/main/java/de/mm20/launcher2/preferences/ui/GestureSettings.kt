package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class GestureSettingsData(
    val swipeDown: GestureAction,
    val swipeLeft: GestureAction,
    val swipeRight: GestureAction,
    val swipeUp: GestureAction,
    val doubleTap: GestureAction,
    val longPress: GestureAction,
    val homeButton: GestureAction,
    val pinchIn: GestureAction,
    val pinchOut: GestureAction,
    val twoFingerSwipeUp: GestureAction,
    val twoFingerSwipeDown: GestureAction,
)

class GestureSettings internal constructor(
    private val dataStore: LauncherDataStore,
): Flow<GestureSettingsData> by (
    dataStore.data.map {
        GestureSettingsData(
            swipeDown = it.gestures.gesturesSwipeDown,
            swipeLeft = it.gestures.gesturesSwipeLeft,
            swipeRight = it.gestures.gesturesSwipeRight,
            swipeUp = it.gestures.gesturesSwipeUp,
            doubleTap = it.gestures.gesturesDoubleTap,
            longPress = it.gestures.gesturesLongPress,
            homeButton = it.gestures.gesturesHomeButton,
            pinchIn = it.gestures.gesturesPinchIn,
            pinchOut = it.gestures.gesturesPinchOut,
            twoFingerSwipeUp = it.gestures.gesturesTwoFingerSwipeUp,
            twoFingerSwipeDown = it.gestures.gesturesTwoFingerSwipeDown,
        )
    }.distinctUntilChanged()
) {
    val swipeDown: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesSwipeDown }
        .distinctUntilChanged()

    val swipeLeft: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesSwipeLeft }
        .distinctUntilChanged()

    val swipeRight: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesSwipeRight }
        .distinctUntilChanged()

    val swipeUp: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesSwipeUp }
        .distinctUntilChanged()

    val doubleTap: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesDoubleTap }
        .distinctUntilChanged()

    val longPress: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesLongPress }
        .distinctUntilChanged()

    val homeButton: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesHomeButton }
        .distinctUntilChanged()

    fun setSwipeDown(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesSwipeDown = action))
        }
    }

    fun setSwipeLeft(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesSwipeLeft = action))
        }
    }

    fun setSwipeRight(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesSwipeRight = action))
        }
    }

    fun setSwipeUp(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesSwipeUp = action))
        }
    }

    fun setDoubleTap(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesDoubleTap = action))
        }
    }

    fun setLongPress(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesLongPress = action))
        }
    }

    fun setHomeButton(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesHomeButton = action))
        }
    }

    val pinchIn: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesPinchIn }
        .distinctUntilChanged()

    val pinchOut: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesPinchOut }
        .distinctUntilChanged()

    val twoFingerSwipeUp: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesTwoFingerSwipeUp }
        .distinctUntilChanged()

    val twoFingerSwipeDown: Flow<GestureAction> = dataStore.data.map { it.gestures.gesturesTwoFingerSwipeDown }
        .distinctUntilChanged()

    fun setPinchIn(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesPinchIn = action))
        }
    }

    fun setPinchOut(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesPinchOut = action))
        }
    }

    fun setTwoFingerSwipeUp(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesTwoFingerSwipeUp = action))
        }
    }

    fun setTwoFingerSwipeDown(action: GestureAction) {
        dataStore.update {
            it.copy(gestures = it.gestures.copy(gesturesTwoFingerSwipeDown = action))
        }
    }
}