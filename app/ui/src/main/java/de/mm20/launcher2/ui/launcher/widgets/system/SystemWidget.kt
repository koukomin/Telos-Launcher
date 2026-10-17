package de.mm20.launcher2.ui.launcher.widgets.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.util.TrafficRate
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.SystemWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.NumberFormat

private data class SystemStats(
    val ramUsed: Long = 0L,
    val ramTotal: Long = 0L,
    val storageUsed: Long = 0L,
    val storageTotal: Long = 0L,
)

@Composable
fun SystemWidget(widget: SystemWidget) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scheme = MaterialTheme.colorScheme
    val comms: CommsSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val logs: LogController = koinInject()
    val dns: DnsController = koinInject()
    val allowSwitch by comms.hwuiWidgetSwitch.collectAsState(false)
    val savedRenderer by comms.hwuiRenderer.collectAsState("default")
    val netState by engine.state.collectAsState()
    val statsLog by logs.stats.collectAsState()
    val server by dns.selected.collectAsState()
    var stats by remember { mutableStateOf(SystemStats()) }
    var rx by remember { mutableStateOf<Long?>(null) }
    var tx by remember { mutableStateOf<Long?>(null) }
    var transport by remember { mutableIntStateOf(R.string.network_widget_disconnected) }
    var liveRenderer by remember { mutableStateOf(HwuiRenderer.MODE_DEFAULT) }
    val scope = rememberCoroutineScope()
    val number = remember { NumberFormat.getIntegerInstance() }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousRx = TrafficStats.getTotalRxBytes()
            var previousTx = TrafficStats.getTotalTxBytes()
            var previousTime = SystemClock.elapsedRealtime()
            while (isActive) {
                stats = withContext(Dispatchers.IO) { readSystemStats(context) }
                liveRenderer = HwuiRenderer.current()
                val manager = context.getSystemService<ConnectivityManager>()
                val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
                transport = when {
                    capabilities == null -> R.string.network_widget_disconnected
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.network_widget_wifi
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.network_widget_cellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.network_widget_ethernet
                    else -> R.string.network_widget_connected
                }
                delay(2000)
                val now = SystemClock.elapsedRealtime()
                val nextRx = TrafficStats.getTotalRxBytes()
                val nextTx = TrafficStats.getTotalTxBytes()
                rx = TrafficRate.bytesPerSecond(previousRx, nextRx, now - previousTime)
                tx = TrafficRate.bytesPerSecond(previousTx, nextTx, now - previousTime)
                previousRx = nextRx
                previousTx = nextTx
                previousTime = now
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "sysW")
    val ring by pulse.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val vk = liveRenderer == HwuiRenderer.MODE_VULKAN
    val accent = if (vk) Color(0xFF00E5A8) else scheme.primary
    val vpnOn = netState is NetState.On

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), scheme.surface.copy(alpha = 0.55f)),
                ),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        accent.copy(alpha = (1f - ring) * 0.4f),
                        radius = size.minDimension / 2f * ring,
                        style = Stroke(width = 3f),
                    )
                    drawCircle(accent, radius = size.minDimension / 5f, center = Offset(size.width / 2f, size.height / 2f))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.widget_name_system), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (vk) R.string.hc_graphics_vulkan else if (liveRenderer == HwuiRenderer.MODE_OPENGL) R.string.hc_graphics_opengl else R.string.hc_graphics_default),
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(onClick = {
                context.tryStartActivity(
                    Intent(context, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SYSTEM),
                )
            }) {
                Icon(painterResource(R.drawable.settings_24px), stringResource(R.string.telos_system_label))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = liveRenderer == HwuiRenderer.MODE_VULKAN || savedRenderer == HwuiRenderer.MODE_VULKAN,
                onClick = {
                    if (!allowSwitch) {
                        Toast.makeText(context, R.string.hc_graphics_widget_locked, Toast.LENGTH_SHORT).show()
                        return@FilterChip
                    }
                    scope.launch {
                        comms.setHwuiRenderer(HwuiRenderer.MODE_VULKAN)
                        val ok = HwuiRenderer.apply(HwuiRenderer.MODE_VULKAN)
                        liveRenderer = HwuiRenderer.current()
                        Toast.makeText(
                            context,
                            if (ok) R.string.hc_graphics_applied else R.string.hc_graphics_priv_missing,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = allowSwitch,
                label = { Text(stringResource(R.string.hc_graphics_vulkan)) },
            )
            FilterChip(
                selected = liveRenderer == HwuiRenderer.MODE_OPENGL || savedRenderer == HwuiRenderer.MODE_OPENGL,
                onClick = {
                    if (!allowSwitch) {
                        Toast.makeText(context, R.string.hc_graphics_widget_locked, Toast.LENGTH_SHORT).show()
                        return@FilterChip
                    }
                    scope.launch {
                        comms.setHwuiRenderer(HwuiRenderer.MODE_OPENGL)
                        val ok = HwuiRenderer.apply(HwuiRenderer.MODE_OPENGL)
                        liveRenderer = HwuiRenderer.current()
                        Toast.makeText(
                            context,
                            if (ok) R.string.hc_graphics_applied else R.string.hc_graphics_priv_missing,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = allowSwitch,
                label = { Text(stringResource(R.string.hc_graphics_opengl)) },
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TechStat(
                Modifier.weight(1f),
                stringResource(R.string.system_widget_ram),
                if (stats.ramTotal == 0L) 0f else stats.ramUsed.toFloat() / stats.ramTotal,
                Formatter.formatShortFileSize(context, stats.ramUsed),
            )
            TechStat(
                Modifier.weight(1f),
                stringResource(R.string.system_widget_storage),
                if (stats.storageTotal == 0L) 0f else stats.storageUsed.toFloat() / stats.storageTotal,
                Formatter.formatShortFileSize(context, stats.storageUsed),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.7f))
                .clickable {
                    context.tryStartActivity(
                        Intent(context, SettingsActivity::class.java)
                            .putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_NETWORK),
                    )
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (vpnOn) Color(0xFF00E5A8) else scheme.outline),
                )
                Text(
                    stringResource(R.string.net_hub_title) + " · " + stringResource(
                        if (vpnOn) R.string.net_hub_state_on else R.string.net_hub_state_off,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (vpnOn) Color(0xFF00E5A8) else scheme.onSurfaceVariant,
                )
            }
            Text(stringResource(transport), style = MaterialTheme.typography.labelMedium)
            Text("↓ ${formatRate(rx)}    ↑ ${formatRate(tx)}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${stringResource(R.string.net_hub_stat_dns)} ${server.name}  ·  ${number.format(statsLog.dnsBlocked)} DNS  ·  ${number.format(statsLog.connectionsBlocked)} conn",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TechStat(modifier: Modifier, label: String, progress: Float, value: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 6.dp).clip(RoundedCornerShape(4.dp)),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun readSystemStats(context: Context): SystemStats {
    val memoryInfo = ActivityManager.MemoryInfo()
    context.getSystemService<ActivityManager>()?.getMemoryInfo(memoryInfo)
    val statFs = StatFs(Environment.getDataDirectory().path)
    return SystemStats(
        ramUsed = memoryInfo.totalMem - memoryInfo.availMem,
        ramTotal = memoryInfo.totalMem,
        storageUsed = statFs.totalBytes - statFs.availableBytes,
        storageTotal = statFs.totalBytes,
    )
}

private fun formatRate(bytes: Long?): String = when {
    bytes == null -> "—"
    bytes >= 1_048_576 -> String.format(java.util.Locale.getDefault(), "%.1f MiB/s", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KiB/s", bytes / 1024.0)
    else -> "$bytes B/s"
}
