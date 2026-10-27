package de.mm20.launcher2.ui.network

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.api.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.firewall.NetAppIcon
import de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable data object NetworkStatsRoute : NavKey

@Composable
fun NetworkStatsScreen() {
    val logs: LogController = koinInject()
    val directory: AppDirectory = koinInject()
    val apps by directory.apps.collectAsState()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    var hours by de.mm20.launcher2.ui.component.rememberPersistentUiState("network.stats.hours", 24)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val start = now - hours.toLong() * 60 * 60 * 1000
    val totals by remember(logs, start) {
        logs.connections(LogFilter(sinceMs = start, limit = 100_000)).map { NetworkTrafficStats.aggregate(it, start, now) }.flowOn(Dispatchers.Default)
    }.collectAsState(NetworkTrafficTotals())
    val appIndex = remember(apps) { apps.associateBy { it.appId } }
    val format = remember(context) { { bytes: Long -> Formatter.formatShortFileSize(context, bytes) } }
    PreferenceScreen(title = stringResource(R.string.netstats_title), bottomBar = { NetworkNavigationBar(NetworkSection.Stats) }) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to R.string.netfw_temp_1h, 24 to R.string.netstats_day, 168 to R.string.intruder_photo_retention_7d).forEach { (value, label) ->
                    FilterChip(selected = hours == value, onClick = { hours = value; now = System.currentTimeMillis() }, label = { Text(stringResource(label)) })
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    @Composable fun Total(label: Int, bytes: Long) {
                        Column(Modifier.widthIn(min = 100.dp)) {
                            Text(format(bytes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Total(R.string.netstats_unmetered, totals.unmetered)
                    Total(R.string.netstats_metered, totals.metered)
                    Total(R.string.netstats_total, totals.bytes)
                    if (totals.unknownMetering > 0) Total(R.string.netstats_unknown, totals.unknownMetering)
                }
            }
        }
        item {
            Text(stringResource(R.string.netstats_note), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.netfw_l_stats, totals.connections - totals.blocked, totals.blocked), Modifier.padding(8.dp))
        }
        items(totals.apps.size, key = { totals.apps[it].appId }) { index ->
            val traffic = totals.apps[index]
            val app = appIndex[traffic.appId]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Preference(title = app?.label ?: directory.labelFor(traffic.appId),
                    summary = stringResource(R.string.nwg_traffic, format(traffic.received), format(traffic.sent)),
                    icon = { NetAppIcon(app?.packageName) },
                    onClick = { app?.packageName?.let { backStack.add(NetworkPackageFirewallRoute(it)) } })
                LinearProgressIndicator(progress = { if (totals.bytes <= 0) 0f else (traffic.bytes.toDouble() / totals.bytes).toFloat().coerceIn(0f,1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp))
            }
        }
    }
}
