package de.mm20.launcher2.ktx

import android.graphics.Bitmap
import android.os.Build

/**
 * Bitmap extension helpers for safe rendering across Telos Launcher custom Canvas views and PDF viewers.
 */

/**
 * Ensures that a Bitmap is in Software Config (ARGB_8888) format, preventing
 * "Cannot draw a hardware bitmap" crashes on custom software Canvas drawers.
 */
fun Bitmap.ensureSoftwareCopy(): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.config == Bitmap.Config.HARDWARE) {
        return this.copy(Bitmap.Config.ARGB_8888, false) ?: this
    }
    return this
}
