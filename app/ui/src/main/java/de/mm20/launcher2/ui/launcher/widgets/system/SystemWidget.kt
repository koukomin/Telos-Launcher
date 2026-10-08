package de.mm20.launcher2.ui.launcher.widgets.system

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.SystemWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class SystemStats(
    val ramUsed: Long = 0L,
    val ramTotal: Long = 0L,
    val storageUsed: Long = 0L,
    val storageTotal: Long = 0L,
)

@Composable
fun SystemWidget(widget: SystemWidget) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(SystemStats()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Only poll while the launcher is visible, not for as long as the composition exists
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = withContext(Dispatchers.IO) { readSystemStats(context) }
                delay(5000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        SystemStatRow(
            icon = R.drawable.memory_24px,
            label = stringResource(R.string.system_widget_ram),
            used = stats.ramUsed,
            total = stats.ramTotal,
            context = context,
        )
        SystemStatRow(
            modifier = Modifier.padding(top = 16.dp),
            icon = R.drawable.storage_24px,
            label = stringResource(R.string.system_widget_storage),
            used = stats.storageUsed,
            total = stats.storageTotal,
            context = context,
        )
    }
}

@Composable
private fun SystemStatRow(
    icon: Int,
    label: String,
    used: Long,
    total: Long,
    context: Context,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LinearProgressIndicator(
            progress = { if (total > 0) used.toFloat() / total.toFloat() else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                R.string.system_widget_used_of_total,
                Formatter.formatShortFileSize(context, used),
                Formatter.formatShortFileSize(context, total),
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
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
