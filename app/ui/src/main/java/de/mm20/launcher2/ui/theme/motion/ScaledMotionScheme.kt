package de.mm20.launcher2.ui.theme.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import kotlin.math.roundToInt

/**
 * Wraps a [MotionScheme] to honor the user's Performance settings.
 *
 * - When [animationsEnabled] is false, every spec becomes [snap] (instant) - this covers
 *   anything routed through `MaterialTheme.motionScheme`, which is most, but not all,
 *   animation in the app; a handful of call sites use raw `tween()`/`animateFloatAsState`
 *   directly and are unaffected.
 * - [speedMultiplier] only rescales [TweenSpec] ("effects"/fade-style) results, matching the
 *   Android system's animator-duration-scale convention (values > 1 = slower). Material3's
 *   "spatial" specs are spring-based and don't have a linear duration to scale, so they pass
 *   through unchanged - only the on/off toggle affects them.
 */
private class ScaledMotionScheme(
    private val base: MotionScheme,
    private val animationsEnabled: Boolean,
    private val speedMultiplier: Float,
) : MotionScheme {
    private fun <T> scale(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> {
        if (!animationsEnabled) return snap()
        if (speedMultiplier == 1f || spec !is TweenSpec<T>) return spec
        return tween(
            durationMillis = (spec.durationMillis * speedMultiplier).roundToInt().coerceAtLeast(0),
            delayMillis = (spec.delay * speedMultiplier).roundToInt().coerceAtLeast(0),
            easing = spec.easing,
        )
    }

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        scale(base.defaultSpatialSpec())

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        scale(base.fastSpatialSpec())

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        scale(base.slowSpatialSpec())

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        scale(base.defaultEffectsSpec())

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        scale(base.fastEffectsSpec())

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        scale(base.slowEffectsSpec())
}

fun MotionScheme.scaledBy(animationsEnabled: Boolean, speedMultiplier: Float): MotionScheme {
    if (animationsEnabled && speedMultiplier == 1f) return this
    return ScaledMotionScheme(this, animationsEnabled, speedMultiplier)
}
