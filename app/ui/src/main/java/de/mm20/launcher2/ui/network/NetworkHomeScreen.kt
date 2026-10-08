package de.mm20.launcher2.ui.network

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsRoute
import de.mm20.launcher2.ui.network.dns.NetworkDnsRoute
import de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesRoute
import de.mm20.launcher2.ui.network.firewall.NetworkFirewallRoute
import de.mm20.launcher2.ui.network.firewall.NetworkUniversalRulesRoute
import de.mm20.launcher2.ui.network.logs.NetworkLogsRoute
import de.mm20.launcher2.ui.network.wireguard.NetworkWireguardAppsRoute
import de.mm20.launcher2.ui.network.wireguard.NetworkWireguardRoute
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.text.NumberFormat

private typealias IconsNetworkHomeScreen = de.mm20.launcher2.base.R.drawable

/** The hub of Telos Network: on/off switch, a short live summary and links to all areas. */
@Serializable
data object NetworkRoute : NavKey

@Composable
fun NetworkHomeScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val engine: NetworkEngine = koinInject()
    val dnsController: DnsController = koinInject()
    val wireguard: WireguardController = koinInject()
    val logs: LogController = koinInject()

    val state by engine.state.collectAsState()
    val selectedDns by dnsController.selected.collectAsState()
    val wgConfigs by wireguard.configs.collectAsState()
    val wgStatus by wireguard.status.collectAsState()
    val stats by logs.stats.collectAsState()

    // The consent dialog is only shown after the user pressed the button, never because an old
    // NeedsPermission state is still around when the screen opens.
    var requested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            engine.start()
        } else {
            engine.acknowledgeError()
            Toast.makeText(context, R.string.net_hub_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(state, requested) {
        val current = state
        if (current is NetState.NeedsPermission && requested) {
            requested = false
            permissionLauncher.launch(current.intent)
        } else if (current is NetState.Off || current is NetState.Error || current is NetState.On) {
            requested = false
        }
    }

    val number = remember { NumberFormat.getInstance() }
    val wgUp = wgConfigs.filter { wgStatus[it.id] == WgStatus.Up }.joinToString(", ") { it.name }

    PreferenceScreen(
        title = { Text(stringResource(R.string.net_hub_title), modifier = Modifier.padding(horizontal = 16.dp)) },
        topBarActions = {
            IconButton(onClick = { backStack.add(NetworkSettingsRoute) }) {
                Icon(painterResource(IconsNetworkHomeScreen.settings_24px), contentDescription = stringResource(R.string.net_hub_settings))
            }
        },
    ) {
        item {
            StatusCard(
                state = state,
                onTurnOn = {
                    requested = true
                    engine.start()
                },
                onTurnOff = { engine.stop() },
                onDismiss = { engine.acknowledgeError() },
            )
        }
        item {
            Banner(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.net_hub_safety),
                icon = IconsNetworkHomeScreen.info_24px,
            )
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_hub_status)) {
                Preference(
                    title = stringResource(R.string.net_hub_stat_dns),
                    summary = selectedDns.name,
                    icon = IconsNetworkHomeScreen.language_24px,
                    onClick = { backStack.add(NetworkDnsRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_wireguard),
                    summary = wgUp.ifEmpty { stringResource(R.string.net_hub_stat_wg_off) },
                    icon = IconsNetworkHomeScreen.lock_24px,
                    onClick = { backStack.add(NetworkWireguardRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_stat_blocked_dns),
                    summary = number.format(stats.dnsBlocked),
                    icon = IconsNetworkHomeScreen.visibility_off_24px,
                    onClick = { backStack.add(NetworkLogsRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_stat_blocked_conn),
                    summary = number.format(stats.connectionsBlocked) + "\n" + stringResource(R.string.net_hub_stats_note),
                    icon = IconsNetworkHomeScreen.error_24px,
                    onClick = { backStack.add(NetworkLogsRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_hub_cat_dns)) {
                Preference(
                    title = stringResource(R.string.net_hub_dns),
                    summary = stringResource(R.string.net_hub_dns_sum),
                    icon = IconsNetworkHomeScreen.language_24px,
                    onClick = { backStack.add(NetworkDnsRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_wireguard),
                    summary = stringResource(R.string.net_hub_wg_sum),
                    icon = IconsNetworkHomeScreen.lock_24px,
                    onClick = { backStack.add(NetworkWireguardRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_wg_apps),
                    summary = stringResource(R.string.net_hub_wg_apps_sum),
                    icon = IconsNetworkHomeScreen.apps_24px,
                    onClick = { backStack.add(NetworkWireguardAppsRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_hub_cat_firewall)) {
                Preference(
                    title = stringResource(R.string.net_hub_fw_apps),
                    summary = stringResource(R.string.net_hub_fw_apps_sum),
                    icon = IconsNetworkHomeScreen.apps_24px,
                    onClick = { backStack.add(NetworkFirewallRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_fw_universal),
                    summary = stringResource(R.string.net_hub_fw_universal_sum),
                    icon = IconsNetworkHomeScreen.rule_settings_24px,
                    onClick = { backStack.add(NetworkUniversalRulesRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_fw_custom),
                    summary = stringResource(R.string.net_hub_fw_custom_sum),
                    icon = IconsNetworkHomeScreen.filter_alt_24px,
                    onClick = { backStack.add(NetworkCustomRulesRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.net_hub_cat_tools)) {
                Preference(
                    title = stringResource(R.string.net_hub_blocklists),
                    summary = stringResource(R.string.net_hub_blocklists_sum),
                    icon = IconsNetworkHomeScreen.visibility_off_24px,
                    onClick = { backStack.add(NetworkBlocklistsRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_logs),
                    summary = stringResource(R.string.net_hub_logs_sum),
                    icon = IconsNetworkHomeScreen.manage_search_24px,
                    onClick = { backStack.add(NetworkLogsRoute) },
                )
                Preference(
                    title = stringResource(R.string.net_hub_settings),
                    summary = stringResource(R.string.net_hub_settings_sum),
                    icon = IconsNetworkHomeScreen.settings_24px,
                    onClick = { backStack.add(NetworkSettingsRoute) },
                )
            }
        }
        item {
            Banner(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.net_hub_early),
                icon = IconsNetworkHomeScreen.warning_24px,
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: NetState,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = when (state) {
        is NetState.On -> MaterialTheme.colorScheme.primaryContainer
        is NetState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (state) {
        is NetState.On -> MaterialTheme.colorScheme.onPrimaryContainer
        is NetState.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val stateLabel = when (state) {
        is NetState.Off -> R.string.net_hub_state_off
        is NetState.Starting -> R.string.net_hub_state_starting
        is NetState.On -> R.string.net_hub_state_on
        is NetState.Error -> R.string.net_hub_state_error
        is NetState.NeedsPermission -> R.string.net_hub_state_permission
    }
    val summary = when (state) {
        is NetState.Off -> stringResource(R.string.net_hub_summary_off)
        is NetState.Starting -> stringResource(R.string.net_hub_summary_starting)
        is NetState.On -> stringResource(R.string.net_hub_summary_on)
        is NetState.Error -> state.message
        is NetState.NeedsPermission -> stringResource(R.string.net_hub_summary_permission)
    }
    val running = state is NetState.On || state is NetState.Starting

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(IconsNetworkHomeScreen.ic_glyph_network),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = stringResource(R.string.net_hub_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(stateLabel),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is NetState.Error) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.net_hub_dismiss)) }
                }
                if (running) {
                    FilledTonalButton(onClick = onTurnOff) { Text(stringResource(R.string.net_hub_turn_off)) }
                } else {
                    Button(onClick = onTurnOn) { Text(stringResource(R.string.net_hub_turn_on)) }
                }
            }
        }
    }
}
