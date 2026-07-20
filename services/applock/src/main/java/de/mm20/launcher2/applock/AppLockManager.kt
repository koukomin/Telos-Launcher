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
 * it isn't the one currently unlocked.
 *
 * "Unlocked" is intentionally not persisted or time-boxed (mirrors
 * [de.mm20.launcher2.ui.launcher.lock.LauncherLockGate]'s ON_STOP pattern, just triggered by
 * foreground-app changes instead of an Activity lifecycle event): the moment the foreground app
 * becomes anything other than the currently-unlocked package, that unlock is forgotten, so
 * switching away and back to a locked app always re-prompts. Per-app auto-lock grace periods are
 * a later piece, not this one.
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
                    if (packageName != unlockedPackage) {
                        unlockedPackage = null
                        val locked = settings.lockedPackages.first().contains(packageName)
                        _pendingLock.value = if (locked) packageName else null
                    }
                }
            }
        }
    }

    /** Called by the gate after a successful authentication for [packageName]. */
    fun reportUnlocked(packageName: String) {
        unlockedPackage = packageName
        _pendingLock.value = null
    }

    /** Called when the user dismisses the gate without authenticating. */
    fun dismiss() {
        _pendingLock.value = null
    }
}
