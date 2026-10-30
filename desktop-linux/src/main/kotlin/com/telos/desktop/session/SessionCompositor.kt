package com.telos.desktop.session

/** labwc is the default; niri is opt-in via TELOS_COMPOSITOR. */
object SessionCompositor {
    const val Labwc = "labwc"
    const val Niri = "niri"

    val fractionalScales: List<Double> = listOf(1.0, 1.25, 1.5, 1.75, 2.0)

    fun resolve(env: String?, hasLabwc: Boolean, hasNiri: Boolean): String {
        val want = env?.trim()?.lowercase().orEmpty().ifBlank { Labwc }
        return when (want) {
            Niri -> if (hasNiri) Niri else if (hasLabwc) Labwc else Niri
            else -> if (hasLabwc) Labwc else if (hasNiri) Niri else Labwc
        }
    }

    fun scaleLabel(scale: Double): String {
        val pct = (scale * 100.0).toInt()
        return "$pct%"
    }

    fun nearestScale(value: Double): Double =
        fractionalScales.minBy { kotlin.math.abs(it - value) }
}
