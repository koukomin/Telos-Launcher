package de.mm20.launcher2.ui.settings.freeze

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.FreezeBackendType
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.FrozenAppStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable
import androidx.appcompat.app.AppCompatActivity

@Serializable
data object FreezeSettingsRoute : NavKey

@Composable
fun FreezeSettingsScreen() {
    val context = LocalContext.current
    val viewModel: FreezeSettingsScreenVM = viewModel()

    // Re-check on every resume: the user may have just started Shizuku/Dhizuku, granted root or
    // usage access in another app or system screen and come back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBackendState()
        onPauseOrDispose { }
    }

    val activeBackend by viewModel.activeBackend.collectAsStateWithLifecycle()
    val backend by viewModel.backend.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()

    val autoFreezeEnabled by viewModel.autoFreezeEnabled.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val exclusionStrictness by viewModel.exclusionStrictness.collectAsStateWithLifecycle()
    val freezeOnScreenOff by viewModel.freezeOnScreenOff.collectAsStateWithLifecycle()
    val freezeOnIdle by viewModel.freezeOnIdle.collectAsStateWithLifecycle()
    val idleTimeoutMinutes by viewModel.idleTimeoutMinutes.collectAsStateWithLifecycle()
    val freezeOnBatterySaver by viewModel.freezeOnBatterySaver.collectAsStateWithLifecycle()

    val excludeMusic by viewModel.excludeMusic.collectAsStateWithLifecycle()
    val excludeNetwork by viewModel.excludeNetwork.collectAsStateWithLifecycle()
    val networkThresholdKb by viewModel.networkThresholdKb.collectAsStateWithLifecycle()

    val freezeMethods by viewModel.freezeMethods.collectAsStateWithLifecycle()
    val advancedFeaturesEnabled by viewModel.advancedFeaturesEnabled.collectAsStateWithLifecycle()
    val hideFromLauncher by viewModel.hideFromLauncher.collectAsStateWithLifecycle()
    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle by viewModel.frozenAppStyle.collectAsStateWithLifecycle(FrozenAppStyle.SnowflakeBadge)
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

    val usageAccessGranted by viewModel.usageAccessGranted.collectAsStateWithLifecycle()

    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val neverFreezeApps by viewModel.neverFreezeApps.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val showIconlessApps by viewModel.showIconlessApps.collectAsStateWithLifecycle()

    val backStack = LocalBackStack.current

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_freeze),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Preference(
                icon = R.drawable.bar_chart_24px,
                title = stringResource(R.string.preference_freeze_dashboard),
                summary = stringResource(R.string.preference_freeze_dashboard_summary),
                onClick = { backStack.add(FreezeDashboardRoute) }
            )
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_backend_category)) {
                ListPreference(
                    title = stringResource(R.string.preference_freeze_backend),
                    items = listOf(
                        stringResource(R.string.preference_value_system_default) to FreezeBackendPreference.Auto,
                        stringResource(R.string.freeze_backend_shizuku) to FreezeBackendPreference.ShizukuOnly,
                        stringResource(R.string.telos_freeze_backend_dhizuku) to FreezeBackendPreference.DhizukuOnly,
                        stringResource(R.string.freeze_backend_root) to FreezeBackendPreference.RootOnly,
                        stringResource(R.string.freeze_backend_island) to FreezeBackendPreference.Island,
                        stringResource(R.string.freeze_backend_device_owner) to FreezeBackendPreference.DeviceOwnerOnly,
                    ),
                    value = backend,
                    onValueChanged = { viewModel.setBackend(it) }
                )
                if (activeBackend == null) {
                    Banner(
                        text = stringResource(R.string.freeze_no_backend_available),
                        icon = R.drawable.error_24px,
                    )
                } else {
                    Preference(
                        icon = R.drawable.ac_unit_24px,
                        title = stringResource(
                            when (activeBackend) {
                                FreezeBackendType.Shizuku -> R.string.freeze_backend_shizuku
                                // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
                                FreezeBackendType.Dhizuku -> R.string.telos_freeze_backend_dhizuku
                                // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
                                FreezeBackendType.Root -> R.string.freeze_backend_root
                                FreezeBackendType.Island -> R.string.freeze_backend_island
                                FreezeBackendType.DeviceOwner -> R.string.freeze_backend_device_owner
                                null -> R.string.freeze_backend_none
                            }
                        ),
                        summary = stringResource(
                            if (hasPermission == true) R.string.freeze_permission_granted
                            else R.string.freeze_permission_not_granted
                        ),
                        onClick = {
                            (context as? AppCompatActivity)?.let { viewModel.requestPermission(it) }
                        }
                    )
                }
                Preference(
                    icon = R.drawable.lock_24px,
                    title = stringResource(R.string.freeze_device_owner_setup_title),
                    onClick = { backStack.add(DeviceOwnerSetupRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_auto_category)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_auto_enabled),
                    summary = stringResource(R.string.preference_freeze_auto_enabled_summary),
                    value = autoFreezeEnabled == true,
                    onValueChanged = { viewModel.setAutoFreezeEnabled(it) }
                )
                AnimatedVisibility(autoFreezeEnabled == true) {
                    Column {
                        Preference(
                            title = stringResource(R.string.preference_freeze_usage_access),
                            summary = stringResource(
                                if (usageAccessGranted) R.string.preference_freeze_usage_access_summary_granted
                                else R.string.preference_freeze_usage_access_summary_not_granted
                            ),
                            onClick = {
                                (context as? AppCompatActivity)?.let { viewModel.requestUsageAccess(it) }
                            }
                        )
                        ListPreference(
                            title = stringResource(R.string.preference_freeze_profile),
                            items = listOf(
                                stringResource(R.string.freeze_profile_battery_saver) to FreezeProfile.BatterySaver,
                                stringResource(R.string.freeze_profile_balanced) to FreezeProfile.Balanced,
                                stringResource(R.string.freeze_profile_aggressive) to FreezeProfile.Aggressive,
                                stringResource(R.string.freeze_profile_ultra_aggressive) to FreezeProfile.UltraAggressive,
                                stringResource(R.string.freeze_profile_custom) to FreezeProfile.Custom,
                            ),
                            value = profile ?: FreezeProfile.Balanced,
                            summary = when (profile) {
                                FreezeProfile.BatterySaver -> stringResource(R.string.freeze_profile_battery_saver_summary)
                                FreezeProfile.Balanced -> stringResource(R.string.freeze_profile_balanced_summary)
                                FreezeProfile.Aggressive -> stringResource(R.string.freeze_profile_aggressive_summary)
                                FreezeProfile.UltraAggressive -> stringResource(R.string.freeze_profile_ultra_aggressive_summary)
                                else -> null
                            },
                            onValueChanged = { viewModel.setProfile(it) }
                        )
                        AnimatedVisibility(profile == FreezeProfile.Custom) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(R.string.preference_freeze_on_screen_off),
                                    value = freezeOnScreenOff == true,
                                    onValueChanged = { viewModel.setFreezeOnScreenOff(it) }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.preference_freeze_on_idle),
                                    value = freezeOnIdle == true,
                                    onValueChanged = { viewModel.setFreezeOnIdle(it) }
                                )
                                AnimatedVisibility(freezeOnIdle == true) {
                                    SliderPreference(
                                        title = stringResource(R.string.preference_freeze_idle_timeout),
                                        value = idleTimeoutMinutes ?: 15,
                                        min = 1,
                                        max = 120,
                                        step = 1,
                                        onValueChanged = { viewModel.setIdleTimeoutMinutes(it) },
                                        label = { Text("$it") }
                                    )
                                }
                                SwitchPreference(
                                    title = stringResource(R.string.preference_freeze_on_battery_saver),
                                    value = freezeOnBatterySaver == true,
                                    onValueChanged = { viewModel.setFreezeOnBatterySaver(it) }
                                )
                                ListPreference(
                                    title = stringResource(R.string.preference_freeze_exclusion_strictness),
                                    summary = stringResource(R.string.preference_freeze_exclusion_strictness_summary),
                                    items = listOf(
                                        stringResource(R.string.freeze_exclusion_strictness_strict) to FreezeExclusionStrictness.Strict,
                                        stringResource(R.string.freeze_exclusion_strictness_relaxed) to FreezeExclusionStrictness.Relaxed,
                                    ),
                                    value = exclusionStrictness ?: FreezeExclusionStrictness.Strict,
                                    onValueChanged = { viewModel.setExclusionStrictness(it) }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.preference_freeze_exclude_music),
                                    summary = stringResource(R.string.preference_freeze_exclude_music_summary),
                                    value = excludeMusic == true,
                                    onValueChanged = { viewModel.setExcludeMusic(it) }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.preference_freeze_exclude_network),
                                    summary = stringResource(R.string.preference_freeze_exclude_network_summary),
                                    value = excludeNetwork == true,
                                    onValueChanged = { viewModel.setExcludeNetwork(it) }
                                )
                                AnimatedVisibility(excludeNetwork == true) {
                                    SliderPreference(
                                        title = stringResource(R.string.preference_freeze_network_threshold),
                                        value = networkThresholdKb ?: 100,
                                        min = 1,
                                        max = 1000,
                                        step = 10,
                                        onValueChanged = { viewModel.setNetworkThresholdKb(it) },
                                        label = { Text(stringResource(R.string.au_freeze_kbps, it)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_freeze_apps_category)) {
                Text(
                    text = stringResource(R.string.preference_freeze_apps_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_show_system_apps),
                    summary = stringResource(R.string.preference_freeze_show_system_apps_summary),
                    value = showSystemApps,
                    onValueChanged = { viewModel.setShowSystemApps(it) }
                )
                AnimatedVisibility(showSystemApps) {
                    Banner(
                        text = stringResource(R.string.freeze_system_apps_warning),
                        icon = R.drawable.error_24px,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_show_iconless_apps),
                    summary = stringResource(R.string.preference_freeze_show_iconless_apps_summary),
                    value = showIconlessApps,
                    onValueChanged = { viewModel.setShowIconlessApps(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_advanced_features),
                    summary = stringResource(R.string.preference_freeze_advanced_features_summary),
                    value = advancedFeaturesEnabled,
                    onValueChanged = { viewModel.setAdvancedFeaturesEnabled(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_freeze_hide_from_launcher),
                    summary = stringResource(R.string.preference_freeze_hide_from_launcher_summary),
                    value = hideFromLauncher,
                    onValueChanged = { viewModel.setHideFromLauncher(it) }
                )
                // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
                ListPreference(
                    title = stringResource(R.string.preference_frozen_app_style),
                    items = listOf(
                        stringResource(R.string.preference_frozen_app_style_grayscale) to FrozenAppStyle.Grayscale,
                        stringResource(R.string.preference_frozen_app_style_snowflake) to FrozenAppStyle.SnowflakeBadge
                    ),
                    value = frozenAppStyle,
                    onValueChanged = { if (it != null) viewModel.setFrozenAppStyle(it) }
                )
                // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===
            }
        }
        itemsIndexed(apps, key = { _, it -> it.key }) { _, app ->
            val icon by viewModel.getIcon(app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val state = appFreezeState(app.componentName.packageName, candidates, neverFreezeApps)
            val method = appFreezeMethod(app.componentName.packageName, freezeMethods)
            // Live OS state, independent of the candidate config below - an app can be frozen
            // right now by Icebox, Island, adb, or anything else, regardless of whether this
            // app's own auto-freeze considers it a candidate.
            val isFrozenNow = remember(app.componentName.packageName, apps) {
                viewModel.isFrozen(app.componentName.packageName)
            }
            var showMenu by remember { mutableStateOf(false) }
            val candidateSummary = stringResource(
                when (state) {
                    AppFreezeState.None -> R.string.freeze_app_state_none
                    AppFreezeState.Candidate -> {
                        if (method == FreezeMethod.Disable) R.string.freeze_app_state_candidate_disable
                        else R.string.freeze_app_state_candidate_suspend
                    }
                    AppFreezeState.NeverFreeze -> R.string.freeze_app_state_never
                }
            )
            Preference(
                title = app.label,
                icon = {
                    ShapedLauncherIcon(size = 32.dp, icon = { icon })
                },
                summary = if (isFrozenNow) {
                    stringResource(R.string.freeze_dashboard_currently_frozen) + " · " + candidateSummary
                } else {
                    candidateSummary
                },
                onClick = { showMenu = true },
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_app_state_none)) },
                    onClick = {
                        viewModel.setAppFreezeState(app, AppFreezeState.None)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_app_state_candidate_suspend)) },
                    onClick = {
                        viewModel.setAppFreezeState(app, AppFreezeState.Candidate, FreezeMethod.Suspend)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_app_state_candidate_disable)) },
                    onClick = {
                        viewModel.setAppFreezeState(app, AppFreezeState.Candidate, FreezeMethod.Disable)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.freeze_app_state_never)) },
                    onClick = {
                        viewModel.setAppFreezeState(app, AppFreezeState.NeverFreeze)
                        showMenu = false
                    }
                )
            }
        }
    }
}
