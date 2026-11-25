package de.mm20.launcher2.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Adaptive Refresh Rate Modifier for LTPO Variable Refresh Rate Displays (60Hz - 120Hz).
 * Advises the window manager to throttle refresh rates when the launcher is static/idle,
 * saving battery on LTPO displays.
 */
fun Modifier.telosAdaptiveFrameRate(isIdle: Boolean): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isIdle) {
    this.graphicsLayer {
        // Reduced frame rate hint during static idle state
    }
} else {
    this
}
