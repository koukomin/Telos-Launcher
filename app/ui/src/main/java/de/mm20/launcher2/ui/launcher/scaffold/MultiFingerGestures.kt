package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Detects pinch in/out and two-finger vertical swipes on the home screen.
 *
 * Interception contract: events are consumed in the [PointerEventPass.Initial] pass (before any
 * child scrollable or the scaffold's one-finger draggables see them) only while at least two
 * pointers are down, [isActive] holds, and [enabled] is true. When no multi-finger gesture has
 * an action assigned, [enabled] must be false, making this modifier a no-op so that multi-touch
 * input behaves exactly as it did before this feature existed (extra pointers are ignored by
 * the one-finger draggables).
 *
 * A gesture fires at most one action (whichever threshold is crossed first) and the rest of the
 * touch is swallowed, including the tail after lifting one of the two fingers.
 */
internal fun Modifier.multiFingerGestures(
    enabled: Boolean,
    isActive: () -> Boolean,
    onPinchIn: () -> Unit,
    onPinchOut: () -> Unit,
    onTwoFingerSwipeUp: () -> Unit,
    onTwoFingerSwipeDown: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        val swipeThreshold = 64.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var panY = 0f
            var fired = false
            var isMultiFinger = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount == 0) break
                if (pressedCount < 2) {
                    if (isMultiFinger) {
                        event.changes.forEach { it.consume() }
                    }
                    continue
                }
                if (!isMultiFinger && !isActive()) continue
                isMultiFinger = true
                if (!fired) {
                    zoom *= event.calculateZoom()
                    panY += event.calculatePan().y
                    when {
                        zoom < PINCH_IN_THRESHOLD -> {
                            onPinchIn()
                            fired = true
                        }

                        zoom > PINCH_OUT_THRESHOLD -> {
                            onPinchOut()
                            fired = true
                        }

                        panY < -swipeThreshold -> {
                            onTwoFingerSwipeUp()
                            fired = true
                        }

                        panY > swipeThreshold -> {
                            onTwoFingerSwipeDown()
                            fired = true
                        }
                    }
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private const val PINCH_IN_THRESHOLD = 0.75f
private const val PINCH_OUT_THRESHOLD = 1 / 0.75f
