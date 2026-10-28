package de.mm20.launcher2.ui.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.ContactQrImage
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable
import java.io.File
import java.net.NetworkInterface
import kotlin.math.sin

@Serializable
data object SystemDashboardRoute : NavKey

internal data class ProcRow(val pid: Int, val name: String, val ramKb: Long?)

@Composable
fun SystemDashboardScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var cpu by remember { mutableStateOf<Float?>(null) }
    var ramUsed by remember { mutableStateOf(0L) }
    var ramTotal by remember { mutableStateOf(0L) }
    var ips by remember { mutableStateOf(listOf<String>()) }
    var wifiSsid by remember { mutableStateOf<String?>(null) }
    var secureWifi by remember { mutableStateOf(false) }
    var privilegedWifi by remember { mutableStateOf(false) }
    var wifiPsk by remember { mutableStateOf<String?>(null) }
    var showPsk by remember { mutableStateOf(false) }
    var networks by remember { mutableStateOf<List<WifiEntry>>(emptyList()) }
    var processes by remember { mutableStateOf<List<ProcRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val uiSettings: UiSettings = koinInject()
    val comms: CommsSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val logs: LogController = koinInject()
    val dns: DnsController = koinInject()
    val systemWidgetOn by uiSettings.systemWidgetEnabled.collectAsState(false)
    val netState by engine.state.collectAsState()
    val netStats by logs.stats.collectAsState()
    val dnsServer by dns.selected.collectAsState()
    var liveRenderer by remember { mutableStateOf(HwuiRenderer.MODE_DEFAULT) }
    val storeVm: de.mm20.launcher2.ui.store.StoreViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var wifiReadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pulse = rememberInfiniteTransition(label = "sysPulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")

    de.mm20.launcher2.ui.component.PrivilegedConnectionEffect {
        scope.launch { privilegedWifi = PrivilegedWifiStore.detect(context).privilege != WifiPrivilege.None }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        scope.launch { privilegedWifi = PrivilegedWifiStore.detect(context).privilege != WifiPrivilege.None }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        wifiReadJob?.cancel()
        wifiPsk = null; showPsk = false; networks = emptyList()
    }
    LaunchedEffect(Unit) {
        var previousCpu: Pair<Long, Long>? = null
        while (true) {
            val currentCpu = withContext(Dispatchers.IO) { readCpu() }
            cpu = cpuUsage(previousCpu, currentCpu)
            previousCpu = currentCpu
            val am = context.getSystemService<ActivityManager>()
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            ramUsed = mi.totalMem - mi.availMem
            ramTotal = mi.totalMem
            ips = localIps()
            val connectedSsid = currentSsidSystemDash(context)
            if (connectedSsid != wifiSsid) { wifiPsk = null; showPsk = false; networks = emptyList() }
            wifiSsid = connectedSsid
            secureWifi = hasWifiPassword(context)

            processes = withContext(Dispatchers.IO) { runningProcs(context) }
            liveRenderer = HwuiRenderer.current()
            delay(1500)
        }
    }

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    var showFontDialog by remember { mutableStateOf(false) }
    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "system",
            appName = "Telos System",
            onDismiss = { showFontDialog = false }
        )
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.22f * MaterialTheme.transparency.surface), Color.Transparent)))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * (0.18f + 0.04f * sin(t * Math.PI * 2).toFloat())
            val c = Offset(size.width * 0.82f, size.height * 0.12f)
            drawCircle(scheme.primary.copy(alpha = 0.12f), r, c, style = Stroke(width = 6f))
            drawCircle(scheme.tertiary.copy(alpha = 0.10f), r * 1.4f, c, style = Stroke(width = 3f))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { backStack.removeLastOrNull() }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back))
                }
                Text(stringResource(R.string.telos_system_label), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showFontDialog = true }) {
                    Icon(painterResource(R.drawable.settings_24px), contentDescription = "Font Settings")
                }
            }
            androidx.compose.material3.TabRow(selectedTabIndex = currentTab, containerColor = Color.Transparent) {
                androidx.compose.material3.Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text(stringResource(R.string.telos_system_label)) }
                )
                androidx.compose.material3.Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text(stringResource(R.string.hc_apps)) }
                )
                androidx.compose.material3.Tab(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    text = { Text("Tweaks") }
                )
            }
            if (currentTab == 1) {
                SystemAppsTab(onOpenDetails = { pkg -> backStack.add(SystemAppDetailsRoute(pkg)) })
            } else if (currentTab == 2) {
                de.mm20.launcher2.ui.settings.tweaks.TweaksSettingsScreen()
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { de.mm20.launcher2.ui.component.PerAppFontScaleSetting(appKey = "system") }
                item { BatteryCard() }
                item {
                    GlassCard {
                        Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.titleMedium)
                        Text("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · ${Build.HARDWARE} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
                        Text(stringResource(R.string.telos_system_uptime, formatUptime(SystemClock.elapsedRealtime())))
                    }
                }
                item {
                    GlassCard {
                        Text(stringResource(R.string.hc_graphics_api), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.hc_graphics_current) + ": " + when (liveRenderer) {
                                HwuiRenderer.MODE_VULKAN -> stringResource(R.string.hc_graphics_vulkan)
                                HwuiRenderer.MODE_OPENGL -> stringResource(R.string.hc_graphics_opengl)
                                else -> stringResource(R.string.hc_graphics_default)
                            },
                            color = if (liveRenderer == HwuiRenderer.MODE_VULKAN) scheme.primary else scheme.primary,
                        )
                        Text(stringResource(R.string.hc_graphics_api_about_summary), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = liveRenderer == HwuiRenderer.MODE_VULKAN,
                                onClick = {
                                    scope.launch {
                                        comms.setHwuiRenderer(HwuiRenderer.MODE_VULKAN)
                                        HwuiRenderer.apply(HwuiRenderer.MODE_VULKAN)
                                        liveRenderer = HwuiRenderer.current()
                                    }
                                },
                                label = { Text(stringResource(R.string.hc_graphics_vulkan)) },
                            )
                            FilterChip(
                                selected = liveRenderer == HwuiRenderer.MODE_OPENGL,
                                onClick = {
                                    scope.launch {
                                        comms.setHwuiRenderer(HwuiRenderer.MODE_OPENGL)
                                        HwuiRenderer.apply(HwuiRenderer.MODE_OPENGL)
                                        liveRenderer = HwuiRenderer.current()
                                    }
                                },
                                label = { Text(stringResource(R.string.hc_graphics_opengl)) },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.hc_system_widget_enable), modifier = Modifier.weight(1f))
                            Switch(checked = systemWidgetOn, onCheckedChange = { uiSettings.setSystemWidgetEnabled(it) })
                        }
                        Text(stringResource(R.string.hc_system_widget_enable_summary), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    GlassCard {
                        Text(stringResource(R.string.telos_system_cpu), style = MaterialTheme.typography.labelLarge)
                        cpu?.let { usage ->
                            LinearProgressIndicator(progress = { usage }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))
                            Text("${(usage * 100).toInt()}%")
                        } ?: Text(stringResource(R.string.virtual_telemetry_unavailable))
                        Text(stringResource(R.string.telos_system_ram), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        val ramF = if (ramTotal == 0L) 0f else ramUsed.toFloat() / ramTotal
                        LinearProgressIndicator(progress = { ramF }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))
                        Text("${Formatter.formatFileSize(context, ramUsed)} / ${Formatter.formatFileSize(context, ramTotal)}")
                    }
                }
                item {
                    GlassCard {
                        Text(stringResource(R.string.telos_system_network), style = MaterialTheme.typography.titleMedium)
                        val vpnOn = netState is NetState.On
                        Text(
                            stringResource(R.string.net_hub_title) + " · " + stringResource(
                                if (vpnOn) R.string.net_hub_state_on else R.string.net_hub_state_off,
                            ),
                            color = if (vpnOn) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Text("${stringResource(R.string.net_hub_stat_dns)} ${dnsServer.name}")
                        Text("${stringResource(R.string.net_hub_stat_blocked_dns)} ${netStats.dnsBlocked}  ·  ${stringResource(R.string.net_hub_stat_blocked_conn)} ${netStats.connectionsBlocked}")
                        Text("SSID: ${wifiSsid ?: "—"}")
                        ips.forEach { Text(it) }
                        if (showPsk && wifiPsk != null) {
                            Text("PSK: $wifiPsk")
                            val payload = wifiQrPayload(wifiSsid.orEmpty(), wifiPsk.orEmpty())
                            ContactQrImage(payload, Modifier.padding(top = 8.dp))
                        }
                        if (wifiSsid != null && secureWifi) FilledTonalButton(enabled = privilegedWifi, onClick = {
                            wifiReadJob?.cancel()
                            wifiReadJob = scope.launch {
                                val act = activity ?: return@launch
                                val ok = AuthManager().authenticateNative(act, context.getString(R.string.telos_system_unlock_wifi))
                                if (!ok) return@launch
                                status = context.getString(R.string.telos_system_reading_wifi)
                                val list = try { withContext(Dispatchers.IO) { readWifiStore(context) } }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { status = context.getString(R.string.virtual_telemetry_unavailable); return@launch }
                                networks = list
                                wifiPsk = list.firstOrNull { it.ssid == wifiSsid }?.psk
                                showPsk = true
                                status = if (list.isEmpty()) context.getString(R.string.telos_system_wifi_need_root) else null
                            }
                        }) { Text(stringResource(R.string.telos_system_reveal_wifi)) }
                        status?.let { Text(it, color = scheme.error) }
                    }
                }
                item { SavedWifiPasswordsPreference() }
                if (networks.isNotEmpty()) {
                    item { Text(stringResource(R.string.telos_system_saved_wifi), style = MaterialTheme.typography.titleMedium) }
                    items(networks, key = { it.ssid }) { n ->
                        GlassCard {
                            Text(n.ssid, style = MaterialTheme.typography.titleSmall)
                            Text(n.psk ?: "—")
                        }
                    }
                }
                item { Text(stringResource(R.string.telos_system_processes), style = MaterialTheme.typography.titleMedium) }
                items(processes.take(40), key = { it.pid }) { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.pid}  ${p.name}", modifier = Modifier.weight(1f), maxLines = 1)
                        Text(p.ramKb?.let { Formatter.formatFileSize(context, it * 1024) } ?: "—")
                    }
                }
            }
            }
        }
    }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    de.mm20.launcher2.ui.component.LauncherCard(shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth().padding(de.mm20.launcher2.ui.layout.UiSpacing.Content),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return "%dh %dm".format(h, m)
}

private fun localIps(): List<String> = buildList {
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { ni ->
            if (!ni.isUp || ni.isLoopback) return@forEach
            ni.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress) add("${ni.displayName}: ${addr.hostAddress}")
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun currentSsidSystemDash(context: Context): String? {
    val cm = context.getSystemService<ConnectivityManager>() ?: return null
    if (cm.allNetworks.none { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }) return null
    val wm = context.getSystemService<WifiManager>() ?: return null
    val ssid = wm.connectionInfo?.ssid?.trim('"')
    return ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}

private fun readCpu(): Pair<Long, Long>? = runCatching {
    File("/proc/stat").bufferedReader().use { parseCpuStat(it.readLine()) }
}.getOrNull()

/** /proc/stat includes guest time in user/nice already; summing it twice inflates load. */
internal fun parseCpuStat(line: String?): Pair<Long, Long>? {
    val columns = line?.trim()?.split(Regex("\\s+")) ?: return null
    if (columns.firstOrNull() != "cpu") return null
    val counters = columns.drop(1).take(8).map { it.toLongOrNull() ?: return null }
    if (counters.size < 4 || counters.any { it < 0 }) return null
    return (counters[3] + (counters.getOrNull(4) ?: 0)) to counters.sum()
}

internal fun cpuUsage(previous: Pair<Long, Long>?, current: Pair<Long, Long>?): Float? {
    if (previous == null || current == null) return null
    val total = current.second - previous.second
    val idle = current.first - previous.first
    if (total <= 0 || idle < 0 || idle > total) return null
    return (1f - idle.toFloat() / total).coerceIn(0f, 1f)
}

internal fun runningProcs(context: Context): List<ProcRow> {
    val am = context.getSystemService<ActivityManager>() ?: return emptyList()
    val processes = runCatching { am.runningAppProcesses }.getOrNull().orEmpty()
    // Android restricts other UIDs; zero PSS from that restriction is not real zero RAM.
    val memory = runCatching { am.getProcessMemoryInfo(processes.map { it.pid }.toIntArray()) }.getOrNull()
    return processes.mapIndexed { index, process ->
        ProcRow(process.pid, process.processName, memory?.getOrNull(index)?.totalPss?.toLong()?.takeIf { it > 0 })
    }.sortedBy { it.name }
}

internal suspend fun readWifiStore(context: Context): List<WifiEntry> =
    PrivilegedWifiStore.load(context.applicationContext)

private fun hasWifiPassword(context: Context): Boolean = runCatching {
    if (currentSsid(context) == null || Build.VERSION.SDK_INT < 31) return false
    val info = context.getSystemService<WifiManager>()?.connectionInfo ?: return false
    info.currentSecurityType in setOf(
        android.net.wifi.WifiInfo.SECURITY_TYPE_PSK, android.net.wifi.WifiInfo.SECURITY_TYPE_SAE,
        android.net.wifi.WifiInfo.SECURITY_TYPE_WEP, android.net.wifi.WifiInfo.SECURITY_TYPE_WAPI_PSK,
    )
}.getOrDefault(false)

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    var batteryInfo by remember { mutableStateOf<BatteryStats?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

                val statusText = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }
                val healthText = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                    else -> "Unknown"
                }
                val pluggedText = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Unplugged"
                }

                batteryInfo = BatteryStats(pct, statusText, healthText, pluggedText, voltage, temp, tech)
            }
        }
    }

    batteryInfo?.let { info ->
        GlassCard {
            Row(
                Modifier.fillMaxWidth().clickable { showDetails = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Battery & Power Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${info.percent}% · ${info.status} (${info.plugged})", style = MaterialTheme.typography.bodyMedium)
                    Text("Health: ${info.health} · ${info.temp / 10f}°C · ${info.voltage} mV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CircularProgressIndicator(
                    progress = { info.percent / 100f },
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp,
                )
            }
        }

        if (showDetails) {
            ModalBottomSheet(onDismissRequest = { showDetails = false }, sheetState = rememberModalBottomSheetState()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Battery Details & Health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    InfoLine("Charge Level", "${info.percent}%")
                    InfoLine("Status", info.status)
                    InfoLine("Health", info.health)
                    InfoLine("Power Source", info.plugged)
                    InfoLine("Temperature", "${info.temp / 10f}°C (${(info.temp / 10f * 9/5 + 32)}°F)")
                    InfoLine("Voltage", "${info.voltage} mV")
                    InfoLine("Technology", info.tech)
                    Button(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private data class BatteryStats(
    val percent: Int,
    val status: String,
    val health: String,
    val plugged: String,
    val voltage: Int,
    val temp: Int,
    val tech: String,
)

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
