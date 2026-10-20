package de.mm20.launcher2.ui.theme

import kotlin.math.pow

/** WCAG relative luminance. Pure arithmetic so the policy can be tested without Android. */
internal fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb ushr shift) and 255) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

internal data class ContrastChoice(val darkText: Boolean, val scrimAlpha: Float)

/** Use the worst sampled pixel, not the average: a white patch can hide white text. */
internal fun chooseContrast(
    minimum: Double? = null,
    maximum: Double? = null,
    forcedDark: Boolean? = null,
): ContrastChoice {
    val blackContrast = minimum?.let { (it + 0.05) / 0.05 } ?: 0.0
    val whiteContrast = maximum?.let { 1.05 / (it + 0.05) } ?: 0.0
    val dark = forcedDark ?: (blackContrast > whiteContrast)
    val contrast = if (dark) blackContrast else whiteContrast
    // Unknown or mixed pixels get a backing that meets 4.5:1 even over the opposite extreme.
    return ContrastChoice(dark, if (contrast >= 4.5) 0f else if (dark) 0.80f else 0.65f)
}
