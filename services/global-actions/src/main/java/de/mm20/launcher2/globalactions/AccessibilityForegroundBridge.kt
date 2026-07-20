package de.mm20.launcher2.globalactions

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Relays [LauncherAccessibilityService]'s WINDOW_STATE_CHANGED events (package name only - the
 * service never requests window content) to whichever feature needs near-real-time foreground-app
 * changes, without those features needing to depend on the accessibility service itself, which
 * only exists while the user has actually enabled it in system Accessibility settings.
 */
class AccessibilityForegroundBridge {
    private val _foregroundPackageChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val foregroundPackageChanges: SharedFlow<String> = _foregroundPackageChanges.asSharedFlow()

    fun onForegroundPackageChanged(packageName: String) {
        _foregroundPackageChanges.tryEmit(packageName)
    }
}
