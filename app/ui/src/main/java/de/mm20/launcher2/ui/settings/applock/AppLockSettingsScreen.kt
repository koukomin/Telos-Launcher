package de.mm20.launcher2.ui.settings.applock

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.AppLockDetectionMode
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.applock.AppLockOverlayService
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable
import androidx.appcompat.app.AppCompatActivity

@Serializable
data object AppLockSettingsRoute : NavKey

/**
 * Master toggle, unlock method, the two detection permissions, and a link to
 * [AppLockAppsScreen] (piece 2's polished, searchable per-app picker) rather than the app list
 * itself - it doesn't belong mixed in with the rest of these settings.
 */
@Composable
fun AppLockSettingsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val viewModel: AppLockSettingsScreenVM = viewModel()

    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val lockMethod by viewModel.lockMethod.collectAsStateWithLifecycle()
    val detectionMode by viewModel.detectionMode.collectAsStateWithLifecycle()
    val lockedPackages by viewModel.lockedPackages.collectAsStateWithLifecycle()
    val defaultGracePeriodMs by viewModel.defaultGracePeriodMs.collectAsStateWithLifecycle()
    val usageAccessGranted by viewModel.usageAccessGranted.collectAsStateWithLifecycle()
    val accessibilityGranted by viewModel.accessibilityGranted.collectAsStateWithLifecycle()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()
    val lockWorkProfileToggle by viewModel.lockWorkProfileToggle.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_app_lock),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory {
                GuardedPreference(
                    locked = hasOverlayPermission == false,
                    description = stringResource(R.string.missing_permission_app_lock),
                    onUnlock = {
                        (context as? AppCompatActivity)?.let { viewModel.requestOverlayPermission(it) }
                    },
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_app_lock_enabled),
                        summary = stringResource(R.string.preference_app_lock_enabled_summary),
                        enabled = hasOverlayPermission != false,
                        value = enabled == true && hasOverlayPermission == true,
                        onValueChanged = {
                            viewModel.setEnabled(it)
                            val serviceIntent = Intent(context, AppLockOverlayService::class.java)
                            if (it) {
                                ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.stopService(serviceIntent)
                            }
                        },
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_app_lock_method),
                    items = listOf(
                        stringResource(R.string.settings_lock_method_device_credential) to SettingsLockMethod.DeviceCredential,
                        stringResource(R.string.settings_lock_method_biometrics_only) to SettingsLockMethod.BiometricsOnly,
                    ),
                    value = lockMethod ?: SettingsLockMethod.DeviceCredential,
                    onValueChanged = { viewModel.setLockMethod(it) },
                )
                ListPreference(
                    title = stringResource(R.string.preference_app_lock_detection_mode),
                    items = listOf(
                        stringResource(R.string.app_lock_detection_mode_hybrid) to AppLockDetectionMode.Hybrid,
                        stringResource(R.string.app_lock_detection_mode_usage_stats) to AppLockDetectionMode.UsageStats,
                        stringResource(R.string.app_lock_detection_mode_accessibility) to AppLockDetectionMode.Accessibility,
                    ),
                    value = detectionMode ?: AppLockDetectionMode.Hybrid,
                    summary = if (detectionMode == AppLockDetectionMode.Hybrid || detectionMode == null) {
                        stringResource(R.string.app_lock_detection_mode_hybrid_summary)
                    } else null,
                    onValueChanged = { viewModel.setDetectionMode(it) },
                )
                ListPreference(
                    title = stringResource(R.string.preference_app_lock_default_grace_period),
                    items = gracePeriodOptions(),
                    value = defaultGracePeriodMs ?: 0L,
                    onValueChanged = { viewModel.setDefaultGracePeriodMs(it) },
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_app_lock_usage_access),
                    summary = stringResource(
                        if (usageAccessGranted) R.string.preference_app_lock_usage_access_summary_granted
                        else R.string.preference_app_lock_usage_access_summary_not_granted
                    ),
                    onClick = {
                        (context as? AppCompatActivity)?.let { viewModel.requestUsageAccess(it) }
                    },
                )
                Preference(
                    title = stringResource(R.string.preference_app_lock_accessibility),
                    summary = stringResource(
                        if (accessibilityGranted) R.string.preference_app_lock_accessibility_summary_granted
                        else R.string.preference_app_lock_accessibility_summary_not_granted
                    ),
                    onClick = {
                        (context as? AppCompatActivity)?.let { viewModel.requestAccessibility(it) }
                    },
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.lock_24px,
                    title = stringResource(R.string.preference_category_app_lock_apps),
                    summary = pluralStringResource(
                        R.plurals.app_lock_apps_locked_count,
                        lockedPackages.size,
                        lockedPackages.size,
                    ),
                    onClick = { backStack.add(AppLockAppsRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_app_lock_work_profile)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_app_lock_work_profile_toggle),
                    summary = stringResource(R.string.preference_app_lock_work_profile_toggle_summary),
                    value = lockWorkProfileToggle == true,
                    onValueChanged = { viewModel.setLockWorkProfileToggle(it) },
                )
            }
        }
    }
}
