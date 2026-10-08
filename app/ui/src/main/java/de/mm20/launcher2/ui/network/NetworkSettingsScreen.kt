package de.mm20.launcher2.ui.network

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.IpMode
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.NotificationDetail
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias IconsNetworkSettingsScreen = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkSettingsRoute : NavKey

@Composable
fun NetworkSettingsScreen() {
    val context = LocalContext.current
    val settings: NetworkSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val s by settings.values.collectAsState()
    val state by engine.state.collectAsState()

    fun open(action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // no settings screen for this on the device
        }
    }

    val mtuLabelAuto = stringResource(R.string.net_settings_mtu_auto)

    PreferenceScreen(title = { Text(stringResource(R.string.net_settings_title), modifier = Modifier.padding(horizontal = 16.dp)) }) {
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_cat_start)) {
                SwitchPreference(
                    title = stringResource(R.string.net_settings_boot),
                    summary = stringResource(R.string.net_settings_boot_sum),
                    icon = IconsNetworkSettingsScreen.power_settings_new_24px,
                    value = s.startOnBoot,
                    onValueChanged = { settings.setStartOnBoot(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_cat_network)) {
                ListPreference(
                    title = stringResource(R.string.net_settings_ip_mode),
                    icon = IconsNetworkSettingsScreen.language_24px,
                    items = listOf(
                        stringResource(R.string.net_settings_ip_v4) to IpMode.V4,
                        stringResource(R.string.net_settings_ip_v6) to IpMode.V6,
                        stringResource(R.string.net_settings_ip_v46) to IpMode.V46,
                    ),
                    value = s.ipMode,
                    onValueChanged = { settings.setIpMode(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_lan),
                    summary = stringResource(R.string.net_settings_lan_sum),
                    icon = IconsNetworkSettingsScreen.lan_24px,
                    value = s.routeLan,
                    onValueChanged = { settings.setRouteLan(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_exclude_self),
                    summary = stringResource(R.string.net_settings_exclude_self_sum),
                    value = s.excludeSelf,
                    onValueChanged = { settings.setExcludeSelf(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_bypass),
                    summary = stringResource(R.string.net_settings_bypass_sum),
                    value = s.allowBypass,
                    onValueChanged = { settings.setAllowBypass(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_metered),
                    summary = stringResource(R.string.net_settings_metered_sum),
                    value = s.vpnMetered,
                    onValueChanged = { settings.setVpnMetered(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_stall),
                    summary = stringResource(R.string.net_settings_stall_sum),
                    value = s.stallOnNoNetwork,
                    onValueChanged = { settings.setStallOnNoNetwork(it) },
                )
                ListPreference(
                    title = stringResource(R.string.net_settings_mtu),
                    items = listOf(0, 1280, 1380, 1420, 1500).map { (if (it == 0) mtuLabelAuto else it.toString()) to it },
                    value = s.mtu,
                    summary = if (s.mtu == 0) mtuLabelAuto else s.mtu.toString(),
                    onValueChanged = { settings.setMtu(it) },
                )
                Preference(
                    title = stringResource(R.string.net_settings_apply),
                    summary = stringResource(R.string.net_settings_apply_sum),
                    enabled = state is NetState.On,
                    onClick = { engine.restart() },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_cat_notif)) {
                ListPreference(
                    title = stringResource(R.string.net_settings_notif_detail),
                    icon = IconsNetworkSettingsScreen.notifications_24px,
                    items = listOf(
                        stringResource(R.string.net_settings_notif_minimal) to NotificationDetail.Minimal,
                        stringResource(R.string.net_settings_notif_counters) to NotificationDetail.WithCounters,
                    ),
                    value = s.notificationDetail,
                    onValueChanged = { settings.setNotificationDetail(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_notif_lock),
                    summary = stringResource(R.string.net_settings_notif_lock_sum),
                    value = s.notificationHideOnLockScreen,
                    onValueChanged = { settings.setNotificationHideOnLockScreen(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_notif_fail),
                    summary = stringResource(R.string.net_settings_notif_fail_sum),
                    value = s.notifyOnFailure,
                    onValueChanged = { settings.setNotifyOnFailure(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_cat_logs)) {
                SwitchPreference(
                    title = stringResource(R.string.net_settings_log_conn),
                    icon = IconsNetworkSettingsScreen.manage_search_24px,
                    value = s.logConnections,
                    onValueChanged = { settings.setLogConnections(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.net_settings_log_dns),
                    value = s.logDns,
                    onValueChanged = { settings.setLogDns(it) },
                )
                ListPreference(
                    title = stringResource(R.string.net_settings_log_max),
                    items = listOf(1000, 5000, 20000, 100000).map { it.toString() to it },
                    value = s.logMaxEntries,
                    summary = s.logMaxEntries.toString(),
                    onValueChanged = { settings.setLogMaxEntries(it) },
                )
                val untilLimit = stringResource(R.string.net_settings_log_days_limit)
                ListPreference(
                    title = stringResource(R.string.net_settings_log_days),
                    items = listOf(0, 1, 3, 7, 30, 90).map { (if (it == 0) untilLimit else it.toString()) to it },
                    value = s.logRetentionDays,
                    summary = if (s.logRetentionDays == 0) untilLimit else s.logRetentionDays.toString(),
                    onValueChanged = { settings.setLogRetentionDays(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_cat_android)) {
                Preference(
                    title = stringResource(R.string.net_settings_always_on),
                    summary = stringResource(R.string.net_settings_always_on_sum),
                    icon = IconsNetworkSettingsScreen.open_in_new_24px,
                    onClick = { open(Settings.ACTION_VPN_SETTINGS) },
                )
                Preference(
                    title = stringResource(R.string.net_settings_battery),
                    summary = stringResource(R.string.net_settings_battery_sum),
                    icon = IconsNetworkSettingsScreen.battery_full_24px,
                    onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_settings_not_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    listOf(
                        R.string.net_settings_not_1, R.string.net_settings_not_2, R.string.net_settings_not_3,
                        R.string.net_settings_not_4, R.string.net_settings_not_5, R.string.net_settings_not_6,
                    ).forEach {
                        Text(
                            text = "• " + stringResource(it),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
