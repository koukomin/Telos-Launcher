package de.mm20.launcher2.ui.launcher.widgets.network

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.util.TrafficRate
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.NetworkWidget
import kotlinx.coroutines.*
import org.koin.compose.koinInject
import java.net.URL
import java.text.NumberFormat
import javax.net.ssl.HttpsURLConnection

/** Uses actual device counters; no traffic inspection or remote lookup until the WAN button is tapped. */
@Composable
fun NetworkWidget(widget: NetworkWidget) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val engine: NetworkEngine = koinInject()
    val logs: LogController = koinInject()
    val dns: DnsController = koinInject()
    val state by engine.state.collectAsState()
    val stats by logs.stats.collectAsState()
    val server by dns.selected.collectAsState()
    var rx by remember { mutableStateOf<Long?>(null) }
    var tx by remember { mutableStateOf<Long?>(null) }
    var lan by remember { mutableStateOf("—") }
    var transport by remember { mutableIntStateOf(R.string.network_widget_disconnected) }
    var wan by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var wanFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val number = remember { NumberFormat.getIntegerInstance() }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            rx = null
            tx = null
            wan = null
            wanFailed = false
            var previousNetwork: android.net.Network? = null
            var previousRx = TrafficStats.getTotalRxBytes()
            var previousTx = TrafficStats.getTotalTxBytes()
            var previousTime = SystemClock.elapsedRealtime()
            while (isActive) {
                val manager = context.getSystemService<ConnectivityManager>()
                val network = manager?.activeNetwork
                if (network != previousNetwork) {
                    wan = null
                    wanFailed = false
                    previousNetwork = network
                }
                val capabilities = manager?.getNetworkCapabilities(network)
                transport = when {
                    capabilities == null -> R.string.network_widget_disconnected
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.network_widget_wifi
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.network_widget_cellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.network_widget_ethernet
                    else -> R.string.network_widget_connected
                }
                // Report the underlying LAN, not the synthetic address assigned to the VPN TUN.
                lan = manager?.allNetworks?.filter {
                    manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
                }?.flatMap { manager.getLinkProperties(it)?.linkAddresses.orEmpty() }
                    ?.map { it.address }?.filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    ?.mapNotNull { it.hostAddress }?.distinct()?.joinToString(" · ")?.ifBlank { "—" } ?: "—"
                delay(2000)
                val now = SystemClock.elapsedRealtime()
                val nextRx = TrafficStats.getTotalRxBytes()
                val nextTx = TrafficStats.getTotalTxBytes()
                rx = TrafficRate.bytesPerSecond(previousRx, nextRx, now - previousTime)
                tx = TrafficRate.bytesPerSecond(previousTx, nextTx, now - previousTime)
                previousRx = nextRx; previousTx = nextTx; previousTime = now
            }
        }
    }
    val status = when (state) {
        NetState.On -> R.string.net_hub_state_on
        NetState.Starting -> R.string.net_hub_state_starting
        is NetState.Error -> R.string.net_hub_state_error
        is NetState.NeedsPermission -> R.string.net_hub_state_permission
        else -> R.string.net_hub_state_off
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.language_24px), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.net_hub_title), style = MaterialTheme.typography.titleMedium)
                Text("VPN · ${stringResource(status)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = {
                context.tryStartActivity(Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_NETWORK))
            }) { Icon(painterResource(R.drawable.settings_24px), stringResource(R.string.net_hub_settings)) }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(transport), style = MaterialTheme.typography.labelLarge)
                Text("↓ ${formatRate(rx)}    ↑ ${formatRate(tx)}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.network_widget_device_traffic), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Metric(stringResource(R.string.net_hub_stat_dns), server.name)
        Metric(stringResource(R.string.net_hub_stat_blocked_dns), number.format(stats.dnsBlocked))
        Metric(stringResource(R.string.net_hub_stat_blocked_conn), number.format(stats.connectionsBlocked))
        Text(stringResource(R.string.net_hub_stats_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Metric("LAN IP", lan)
        HorizontalDivider()
        // Telos is excluded from its own VPN to prevent loops. Never label this as the tunnel exit.
        Text("Telos → api.ipify.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(enabled = !checking, onClick = {
            checking = true
            wanFailed = false
            scope.launch {
                wan = try {
                    withContext(Dispatchers.IO) {
                        val connection = URL("https://api.ipify.org").openConnection() as HttpsURLConnection
                        try {
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            connection.instanceFollowRedirects = false
                            check(connection.responseCode in 200..299)
                            connection.inputStream.bufferedReader(Charsets.US_ASCII).use { reader ->
                                val buffer = CharArray(64)
                                var count = 0
                                while (count < buffer.size) {
                                    val read = reader.read(buffer, count, buffer.size - count)
                                    if (read < 0) break
                                    count += read
                                }
                                // No valid unscoped IPv4/IPv6 literal needs this much space.
                                check(count < buffer.size)
                                String(buffer, 0, count).trim().takeIf(::isNumericIp)
                            }
                        } finally { connection.disconnect() }
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                wanFailed = wan == null
                checking = false
            }
        }) { Text("WAN IP · ${if (checking) "…" else if (wanFailed) stringResource(R.string.hc_failed_try_again) else wan ?: "↻"}") }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatRate(bytes: Long?): String = when {
    bytes == null -> "—"
    bytes >= 1_048_576 -> String.format(java.util.Locale.getDefault(), "%.1f MiB/s", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KiB/s", bytes / 1024.0)
    else -> "$bytes B/s"
}

/** The IPv6 branch contains a colon and only numeric-literal characters, so it cannot do DNS. */
private fun isNumericIp(value: String): Boolean {
    if (':' in value) {
        if (!value.matches(Regex("[0-9a-fA-F:.]+"))) return false
        return try { java.net.InetAddress.getByName(value) is java.net.Inet6Address } catch (_: Exception) { false }
    }
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } && (part.toIntOrNull() ?: -1) in 0..255
    }
}
