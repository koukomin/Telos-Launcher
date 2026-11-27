package de.mm20.launcher2.oem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Advanced OEM Haptic Engine for Telos Launcher.
 * Triggers precise mechanical feedback for Oppo O-Haptics,
 * Xiaomi Linear Motor, and Samsung Vibetonz.
 */
object OemHapticsEngine {

    fun performClickHaptic(view: View) {
        val flavor = OemIntegrationEngine.detectFlavor()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val effect = when (flavor) {
                    AndroidFlavor.ColorOS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    AndroidFlavor.HyperOS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    AndroidFlavor.OneUI -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(effect)
            }
            else -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}
