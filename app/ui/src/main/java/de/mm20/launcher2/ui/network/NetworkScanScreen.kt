package de.mm20.launcher2.ui.network
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.scanner.DiscoveredHost
import de.mm20.launcher2.network.scanner.NetworkScannerClient
import de.mm20.launcher2.network.scanner.PingResult
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object NetworkScanRoute : NavKey

@Composable
fun NetworkScanScreen() {
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NetworkScannerClient() }

    var bypassVpnForLan by rememberSaveable { mutableStateOf(true) }

    // Subnet Scan State
    var isScanningSubnet by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableStateOf(0) }
    var totalToScan by remember { mutableStateOf(254) }
    var foundHosts by remember { mutableStateOf(emptyList<DiscoveredHost>()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    // Ping State
    var pingTarget by rememberSaveable { mutableStateOf("") }
    var isPinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<PingResult?>(null) }
    var pingError by remember { mutableStateOf<String?>(null) }

    PreferenceScreen(title = {
        Text(stringResource(I18nR.string.net_scan_title), Modifier.padding(horizontal = 16.dp))
    }) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Intro Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painterResource(de.mm20.launcher2.base.R.drawable.ic_app_network_fg),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                stringResource(I18nR.string.net_scan_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            stringResource(I18nR.string.net_scan_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Bypass VPN Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(I18nR.string.net_scan_bypass_vpn),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(I18nR.string.net_scan_bypass_vpn_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = bypassVpnForLan,
                            onCheckedChange = { bypassVpnForLan = it },
                        )
                    }
                }

                // Subnet Scan Controls
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(I18nR.string.net_scan_subnet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    if (isScanningSubnet) {
                                        scanJob?.cancel()
                                        isScanningSubnet = false
                                    } else {
                                        isScanningSubnet = true
                                        scannedCount = 0
                                        foundHosts = emptyList()
                                        scanJob = scope.launch {
                                            val results = scanner.scanSubnet(
                                                onProgress = { sc, tot, _ ->
                                                    scannedCount = sc
                                                    totalToScan = tot
                                                },
                                                onHostFound = { host ->
                                                    foundHosts = (foundHosts + host).distinctBy { it.ip }
                                                },
                                                bypassVpn = bypassVpnForLan,
                                            )
                                            foundHosts = results
                                            isScanningSubnet = false
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(if (isScanningSubnet) I18nR.string.net_scan_stop else I18nR.string.net_scan_start)
                                )
                            }
                        }

                        if (isScanningSubnet) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { if (totalToScan > 0) scannedCount.toFloat() / totalToScan else 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(I18nR.string.net_scan_scanning, scannedCount, totalToScan, foundHosts.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Discovered Hosts List
                if (foundHosts.isNotEmpty()) {
                    Text(
                        stringResource(I18nR.string.net_scan_found_devices, foundHosts.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    foundHosts.forEach { host ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        host.ip,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (host.isGateway) {
                                        Text(
                                            stringResource(I18nR.string.net_scan_gateway),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (host.hostname.isNotBlank()) {
                                    Text(
                                        host.hostname,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "Latency: ${host.latencyMs}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                host.macAddress?.let { mac ->
                                    val vendorStr = host.vendor?.let { " ($it)" }.orEmpty()
                                    Text(
                                        "MAC: $mac$vendorStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                if (host.openPorts.isNotEmpty()) {
                                    Text(
                                        stringResource(I18nR.string.net_scan_open_ports, host.openPorts.joinToString()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Ping Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(I18nR.string.net_ping_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        OutlinedTextField(
                            value = pingTarget,
                            onValueChange = { pingTarget = it },
                            label = { Text(stringResource(I18nR.string.net_ping_target_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = {
                                if (pingTarget.isNotBlank() && !isPinging) {
                                    isPinging = true
                                    pingError = null
                                    pingResult = null
                                    scope.launch {
                                        val res = scanner.ping(pingTarget, count = 4, bypassVpn = bypassVpnForLan)
                                        pingResult = res
                                        if (res.packetsReceived == 0) {
                                            pingError = pingTarget
                                        }
                                        isPinging = false
                                    }
                                }
                            },
                            enabled = pingTarget.isNotBlank() && !isPinging,
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(I18nR.string.net_ping_start))
                            }
                        }

                        pingResult?.let { res ->
                            val resolved = res.resolvedIp ?: res.target
                            if (res.packetsReceived > 0) {
                                Text(
                                    stringResource(
                                        I18nR.string.net_ping_result,
                                        res.target,
                                        resolved,
                                        res.packetsReceived,
                                        res.packetsSent,
                                        res.minLatencyMs ?: 0,
                                        res.avgLatencyMs ?: 0,
                                        res.maxLatencyMs ?: 0,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        pingError?.let { err ->
                            Text(
                                stringResource(I18nR.string.net_ping_failed, err),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
