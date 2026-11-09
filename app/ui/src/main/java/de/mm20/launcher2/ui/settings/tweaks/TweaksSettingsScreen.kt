package de.mm20.launcher2.ui.settings.tweaks
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch

@Serializable
data object TweaksSettingsRoute : NavKey

@Composable
fun TweaksSettingsScreen() {
    val vm: TweaksSettingsScreenVM = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        scope.launch { vm.refreshBackendState() }
    }
    de.mm20.launcher2.ui.component.PrivilegedConnectionEffect {
        scope.launch { vm.refreshBackendState() }
    }

    val hasPrivilegedBackend = state.isShizukuAvailable || state.isRootAvailable
    val backendStatusSummary = when {
        state.isRootAvailable && state.isShizukuAvailable -> stringResource(I18nR.string.freeze_backend_root) + " · " + stringResource(I18nR.string.freeze_backend_shizuku)
        state.isRootAvailable -> stringResource(I18nR.string.freeze_backend_root)
        state.isShizukuAvailable -> stringResource(I18nR.string.freeze_backend_shizuku)
        else -> stringResource(I18nR.string.hf_freeze_disconnected)
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.tweaks_title)) },
    ) {
        item {
            Banner(
                text = stringResource(I18nR.string.tweaks_banner) + " " + backendStatusSummary,
                icon = BaseR.drawable.settings_24px,
            )
        }

        item {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(I18nR.string.tweaks_warning_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(I18nR.string.tweaks_warning_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_boot)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_persist_boot),
                    summary = stringResource(I18nR.string.tweaks_persist_boot_sum),
                    value = state.persistOnBoot,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.togglePersistOnBoot(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_display)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_clock_seconds),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_clock_seconds_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.clockSeconds,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleClockSeconds(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_hide_nav_pill),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleHideNavPill(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_peak_refresh),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_peak_refresh_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.peakRefresh120,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.togglePeakRefresh120(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_aod),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_aod_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.alwaysOnDisplay,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleAlwaysOnDisplay(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_sound)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_volume_30),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleVolume30Steps(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_disable_safe_volume),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_disable_safe_volume_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.disableSafeVolume,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleDisableSafeVolume(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_call_vibrate),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_call_vibrate_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.callVibrateConnect,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleCallVibrateConnect(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_notifications)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_flash_pulse),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleFlashPulseCall(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_network)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_mobile_data_always),
                    summary = if (hasPrivilegedBackend) stringResource(I18nR.string.tweaks_mobile_data_always_sum) else stringResource(I18nR.string.tweaks_requires_privileged),
                    value = state.mobileDataAlwaysOn,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleMobileDataAlwaysOn(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.tweaks_cat_battery)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_aggressive_doze),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleForceDoze(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_caffeinate),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleCaffeinate(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_pocket),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.togglePocketMode(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.tweaks_double_tap_sleep),
                    summary = stringResource(I18nR.string.virtual_telemetry_unavailable),
                    value = false,
                    enabled = false,
                    onValueChanged = { vm.toggleDoubleTapSleep(it) },
                )
            }
        }
    }
}
