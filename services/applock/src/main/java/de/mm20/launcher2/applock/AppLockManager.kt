package de.mm20.launcher2.applock

import android.content.Context
import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides, from the raw foreground-package stream in [AppLockForegroundMonitor], when the App
 * Lock gate actually needs to show - i.e. the foreground app changed to one the user locked, and
 * it isn't within its grace period after last being unlocked.
 *
 * "Unlocked" is time-boxed by [AppLockSettings.gracePeriodMsFor] rather than reset the instant
 * the foreground app changes (mirroring [de.mm20.launcher2.ui.launcher.lock.LauncherLockGate]'s
 * ON_STOP pattern would mean always re-prompting, which is what piece 1/2 did): the grace clock
 * only starts counting the moment the user actually *leaves* the unlocked app for something else,
 * not while it's continuously in the foreground - so returning to it quickly (switching to check
 * a notification, for instance) doesn't always demand a fresh prompt, but leaving it long enough
 * does, and switching to a *different* locked app is never covered by another app's grace period.
 *
 * [pendingLock] - not a one-shot event stream - is deliberate: Android's background-activity-
 * launch restrictions block a plain `startActivity()` from here (confirmed on-device via a
 * StrictMode BackgroundActivityLaunchViolation), so nothing in this module ever launches an
 * Activity directly. Instead [de.mm20.launcher2.ui.applock.AppLockOverlayService] observes this
 * state to show/hide a SYSTEM_ALERT_WINDOW overlay (not subject to that restriction), and only
 * that overlay's own "Unlock" button - a direct user tap on a window the app already owns, which
 * *is* a recognized exemption - ever starts the actual biometric Activity.
 *
 * Lives for the whole process lifetime (`createdAtStart = true` in the Koin module) but only
 * actually watches for foreground changes while [AppLockSettings.enabled] is true, so there's no
 * polling/listening cost while the feature is off.
 */
class AppLockManager(
    context: Context,
    private val settings: AppLockSettings,
    private val monitor: AppLockForegroundMonitor,
) {
    private val ownPackageName = context.packageName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var unlockedPackage: String? = null

    /** When we last saw [unlockedPackage] leave the foreground, or null while it's still there
     * (or there's no unlocked package at all). The grace period is measured from this instant. */
    @Volatile
    private var unlockedLeftAt: Long? = null

    private val _pendingLock = MutableStateFlow<String?>(null)

    /** The package the gate should currently be shown for, or null if none is pending. */
    val pendingLock: StateFlow<String?> = _pendingLock

    init {
        scope.launch {
            settings.enabled.collectLatest { enabled ->
                if (!enabled) {
                    _pendingLock.value = null
                    return@collectLatest
                }
                monitor.foregroundPackageChanges().collectLatest { packageName ->
                    if (packageName == ownPackageName) return@collectLatest

                    if (packageName == unlockedPackage) {
                        val leftAt = unlockedLeftAt
                        if (leftAt != null &&
                            System.currentTimeMillis() - leftAt > settings.gracePeriodMsFor(packageName)
                        ) {
                            // Grace period ran out while we were away - treat as freshly locked.
                            unlockedPackage = null
                            unlockedLeftAt = null
                            gateIfLocked(packageName)
                        } else {
                            // Still within (or never left) the grace window.
                            unlockedLeftAt = null
                        }
                        return@collectLatest
                    }

                    if (unlockedPackage != null && unlockedLeftAt == null) {
                        unlockedLeftAt = System.currentTimeMillis()
                    }
                    gateIfLocked(packageName)
                }
            }
        }
    }

    private suspend fun gateIfLocked(packageName: String) {
        val locked = settings.lockedPackages.first().contains(packageName)
        _pendingLock.value = if (locked) packageName else null
    }

    /** Called by the gate after a successful authentication for [packageName]. */
    fun reportUnlocked(packageName: String) {
        unlockedPackage = packageName
        unlockedLeftAt = null
        _pendingLock.value = null
    }

    /** Called when the user dismisses the gate without authenticating. */
    fun dismiss() {
        _pendingLock.value = null
    }
}
