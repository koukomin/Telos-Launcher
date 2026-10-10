package de.mm20.launcher2.ui.launcher.widgets.clock

/**
 * Below this height (dp) the area available to the clock plus its dynamic zone (date, weather,
 * ...) is too small for the tall stacked/large clock faces (largest: orbit, 192dp + date).
 */
internal const val ClockStackedMinHeightDp = 300f

/**
 * Decides whether the clock must use its compact (single line, ~56dp) variant to fit.
 * Pure function so the decision is easy to reason about.
 *
 * @param availableHeightDp height available for clock + dynamic zone; infinite/NaN/<=0 means
 * unconstrained (scrolling container), in which case the large layout is kept.
 */
internal fun shouldUseCompactClock(availableHeightDp: Float): Boolean {
    if (availableHeightDp.isNaN() || availableHeightDp.isInfinite() || availableHeightDp <= 0f) return false
    return availableHeightDp < ClockStackedMinHeightDp
}
