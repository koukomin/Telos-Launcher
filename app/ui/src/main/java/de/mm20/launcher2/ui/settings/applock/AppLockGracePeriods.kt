package de.mm20.launcher2.ui.settings.applock

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem

/**
 * The fixed set of auto-lock grace periods offered everywhere App Lock asks for one - the
 * default in [AppLockSettingsScreen] and each app's override in [AppLockAppsScreen]. In
 * milliseconds, matching [de.mm20.launcher2.preferences.applock.AppLockSettings.defaultGracePeriodMs].
 */
@Composable
fun gracePeriodOptions(): List<ListPreferenceItem<Long>> = listOf(
    stringResource(R.string.app_lock_grace_period_immediately) to 0L,
    stringResource(R.string.app_lock_grace_period_30s) to 30_000L,
    stringResource(R.string.app_lock_grace_period_1m) to 60_000L,
    stringResource(R.string.app_lock_grace_period_5m) to 300_000L,
    stringResource(R.string.app_lock_grace_period_30m) to 1_800_000L,
)

/** Same options as [gracePeriodOptions], plus a leading "use default" entry (null) for per-app
 * overrides in [AppLockAppsScreen]. */
@Composable
fun gracePeriodOverrideOptions(): List<ListPreferenceItem<Long?>> = listOf(
    stringResource(R.string.app_lock_grace_period_use_default) to null,
) + gracePeriodOptions()
