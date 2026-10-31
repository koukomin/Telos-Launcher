package de.mm20.launcher2.ui.network
import de.mm20.launcher2.i18n.R as I18nR

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.WgStatus
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsRoute
import de.mm20.launcher2.ui.network.dns.NetworkDnsRoute
import de.mm20.launcher2.ui.network.firewall.NetworkFirewallRoute
import de.mm20.launcher2.ui.network.logs.NetworkLogsRoute
import de.mm20.launcher2.ui.network.wireguard.NetworkWireguardRoute
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.text.NumberFormat

private typealias Icons = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkHomeScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val engine: NetworkEngine = koinInject()
    val dnsController: DnsController = koinInject()
    val wireguard: WireguardController = koinInject()
    val logs: LogController = koinInject()
    val firewall: FirewallController = koinInject()
    val blocklists: BlocklistController = koinInject()

    val state by engine.state.collectAsState()
    val selectedDns by dnsController.selected.collectAsState()
    val wgConfigs by wireguard.configs.collectAsState()
    val wgStatus by wireguard.status.collectAsState()
    val stats by logs.stats.collectAsState()
    val appRules by firewall.appRules.collectAsState()
    val listsOn by blocklists.enabled.collectAsState()

    var requested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) engine.start()
        else {
            engine.acknowledgeError()
            Toast.makeText(context, I18nR.string.net_hub_permission_denied, Toast.LENGTH_LONG).show()
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
    val wgUp = wgConfigs.count { wgStatus[it.id] == WgStatus.Up }
    val on = state is NetState.On
    val t = MaterialTheme.transparency
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val bar = MaterialTheme.colorScheme.surfaceContainer
    val ring by animateColorAsState(
        when (state) {
            is NetState.On -> MaterialTheme.colorScheme.primary
            is NetState.Starting -> MaterialTheme.colorScheme.tertiary
            is NetState.Error, is NetState.NeedsPermission -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "ring",
    )

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NetworkNavigationBar(NetworkSection.Home)
            },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(I18nR.string.net_hub_title), fontWeight = FontWeight.Medium) },
                    actions = {
                        IconButton(onClick = { backStack.add(NetworkSettingsRoute) }) {
                            Icon(painterResource(Icons.settings_24px), contentDescription = stringResource(I18nR.string.net_hub_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bar),
                )
            },
        ) { pad ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale),
                modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Rethink DNS Status Banner: Vibrant Neon status text
                        Text(
                            text = if (on) "ΠΡΟΣΤΑΤΕΥΕΤΑΙ" else "ΔΕΝ ΠΡΟΣΤΑΤΕΥΕΤΑΙ",
                            color = if (on) Color(0xFF00F2FE) else Color(0xFFFF007F),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )

                        // Prominent Rethink DNS Capsule START / STOP Button
                        Button(
                            onClick = {
                                if (on) engine.stop() else { requested = true; engine.start() }
                            },
                            enabled = state !is NetState.Starting,
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (on) MaterialTheme.colorScheme.errorContainer else Color(0xFF38BDF8),
                                contentColor = if (on) MaterialTheme.colorScheme.onErrorContainer else Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = if (on) "STOP" else "START",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }

                        if (state is NetState.Error) {
                            Text((state as NetState.Error).message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                            TextButton(onClick = { engine.acknowledgeError() }) { Text(stringResource(I18nR.string.net_hub_dismiss)) }
                        }

                        val sampleHistory = remember {
                            listOf(
                                10L to 5L, 45L to 18L, 120L to 35L, 85L to 40L,
                                210L to 90L, 160L to 75L, 340L to 110L, 290L to 95L,
                                420L to 180L, 380L to 150L, 510L to 210L, 460L to 190L
                            )
                        }
                        de.mm20.launcher2.ui.component.CyberWaveformChart(
                            downloadKb = stats.dnsAllowed + stats.connectionsAllowed,
                            uploadKb = stats.dnsBlocked + stats.connectionsBlocked,
                            history = sampleHistory,
                            modifier = Modifier.padding(top = 12.dp)
                        )

                        FlowRow(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatChip(stringResource(I18nR.string.net_hub_stat_blocked_dns), number.format(stats.dnsBlocked), card) {
                                backStack.add(NetworkLogsRoute)
                            }
                            StatChip(stringResource(I18nR.string.net_hub_stat_blocked_conn), number.format(stats.connectionsBlocked), card) {
                                backStack.add(NetworkLogsRoute)
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { NetworkRouteChooser() }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.nsp_title),
                        value = stringResource(I18nR.string.nsp_intro),
                        icon = Icons.speed_24px,
                        card = card,
                        onClick = { backStack.add(NetworkSpeedTestRoute) },
                    )
                }
                item { HubTile(title = "NextDNS", value = stringResource(I18nR.string.nd_title),
                    icon = Icons.edit_24px, card = card, onClick = { backStack.add(de.mm20.launcher2.ui.network.nextdns.NextDnsRoute) }) }
                item { HubTile(title = "Tor", value = stringResource(I18nR.string.nt_onion),
                    icon = Icons.visibility_off_24px, card = card, onClick = { backStack.add(NetworkPrivacyRoute) }) }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_dns),
                        value = selectedDns.name,
                        icon = Icons.language_24px,
                        card = card,
                        onClick = { backStack.add(NetworkDnsRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_dns_system),
                        value = stringResource(I18nR.string.net_dns_group_system),
                        icon = Icons.language_24px,
                        card = card,
                        onClick = { backStack.add(PrivateDnsRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_cat_firewall),
                        value = stringResource(I18nR.string.net_hub_fw_apps_n, appRules.size),
                        icon = Icons.apps_24px,
                        card = card,
                        onClick = { backStack.add(NetworkFirewallRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_wireguard),
                        value = if (wgUp == 0) stringResource(I18nR.string.net_hub_stat_wg_off)
                        else stringResource(I18nR.string.net_hub_wg_up, wgUp),
                        icon = Icons.lock_24px,
                        card = card,
                        onClick = { backStack.add(NetworkWireguardRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_blocklists),
                        value = stringResource(I18nR.string.net_hub_lists_n, listsOn.size),
                        icon = Icons.visibility_off_24px,
                        card = card,
                        onClick = { backStack.add(NetworkBlocklistsRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_logs),
                        value = stringResource(I18nR.string.net_hub_logs_sum_short),
                        icon = Icons.manage_search_24px,
                        card = card,
                        onClick = { backStack.add(NetworkLogsRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_scan_title),
                        value = stringResource(I18nR.string.net_scan_subnet),
                        icon = Icons.manage_search_24px,
                        card = card,
                        onClick = { backStack.add(de.mm20.launcher2.ui.network.NetworkScanRoute) },
                    )
                }
                item {
                    HubTile(
                        title = stringResource(I18nR.string.net_hub_settings),
                        value = stringResource(I18nR.string.net_hub_settings_sum_short),
                        icon = Icons.settings_24px,
                        card = card,
                        onClick = { backStack.add(NetworkSettingsRoute) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(I18nR.string.net_hub_safety),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun StatChip(label: String, value: String, card: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(card)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HubTile(title: String, value: String, icon: Int, card: Color, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
