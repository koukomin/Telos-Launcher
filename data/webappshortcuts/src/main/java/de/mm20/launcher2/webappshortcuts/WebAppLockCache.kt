package de.mm20.launcher2.webappshortcuts

import de.mm20.launcher2.preferences.applock.AppLockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [WebAppShortcutImpl.launch] needs a synchronous "is this locked" check, but
 * [AppLockSettings.lockedWebAppShortcuts] is DataStore-backed (Flow-only, nothing synchronous -
 * see [de.mm20.launcher2.preferences.LauncherDataStore]). Mirrors the plain-object-plus-updater
 * pattern used elsewhere in this codebase (e.g. HomeScreenPager's `CurrentHomeScreenPage`) rather
 * than injecting a live settings object into a data class.
 */
internal object WebAppLockCache {
    @Volatile
    var lockedKeys: Set<String> = emptySet()
}

/** Keeps [WebAppLockCache] in sync with [AppLockSettings.lockedWebAppShortcuts] for the whole
 * process lifetime - registered `createdAtStart = true` in [webAppShortcutsModule]. */
internal class WebAppLockCacheUpdater(appLockSettings: AppLockSettings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            appLockSettings.lockedWebAppShortcuts.collect {
                WebAppLockCache.lockedKeys = it
            }
        }
    }
}
