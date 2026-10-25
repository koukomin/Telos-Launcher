package de.mm20.launcher2.comms.media

/** Android LoudnessEnhancer uses millibels; 200% amplitude is +6.02dB, not +20dB. */
fun volumeGainMilliBel(ratio: Float): Int = (2000.0 * kotlin.math.log10(ratio.coerceIn(1f, 2f).toDouble())).toInt()
