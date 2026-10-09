package de.mm20.launcher2.applock

import de.mm20.launcher2.freeze.AppUsageStatsProvider
import de.mm20.launcher2.globalactions.AccessibilityForegroundBridge
import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

/**
 * A single stream of "the foreground app just changed to this package" events, combining both
 * detection paths the user can opt into (see [AppLockDetectionMode]):
 * - [AccessibilityForegroundBridge]'s real-time WINDOW_STATE_CHANGED events, when the launcher's
 *   accessibility service is actually running - effectively instant.
 * - [AppUsageStatsProvider] polling, which works as soon as Usage Access is granted with no extra
 *   service to enable, but only resolves the foreground app a few hundred milliseconds after it
 *   actually changed.
 *
 * In [AppLockDetectionMode.Hybrid] (the default) both run at once: whichever notices a change
 * first wins for that instant, and the other keeps the gate honest if the accessibility service
 * isn't enabled or momentarily disconnects.
 */
class AppLockForegroundMonitor(
    private val settings: AppLockSettings,
    private val usageStatsProvider: AppUsageStatsProvider,
    private val accessibilityBridge: AccessibilityForegroundBridge,
) {
    // Re-evaluated whenever the detection mode setting changes, so switching it applies live.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun foregroundPackageChanges(): Flow<String> =
        settings.detectionMode.distinctUntilChanged().flatMapLatest { mode -> sourcesFor(mode) }

    private fun sourcesFor(mode: AppLockDetectionMode): Flow<String> = flow {
        val sources = buildList {
            if (mode == AppLockDetectionMode.Accessibility || mode == AppLockDetectionMode.Hybrid) {
                add(accessibilityBridge.foregroundPackageChanges)
            }
            if (mode == AppLockDetectionMode.UsageStats || mode == AppLockDetectionMode.Hybrid) {
                add(pollingFlow())
            }
        }
        emitAll(merge(*sources.toTypedArray()).distinctUntilChanged())
    }

    private fun pollingFlow(): Flow<String> = flow {
        while (currentCoroutineContext().isActive) {
            usageStatsProvider.currentForegroundPackage()?.let { emit(it) }
            delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 400L
    }
}
