package com.telos.desktop.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.linux.Sys
import com.telos.desktop.monitor.LinuxSystemTelemetryEngine
import com.telos.desktop.monitor.ProcessActions
import com.telos.desktop.monitor.SystemSnapshot
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.vault.PhantomVault
import kotlinx.coroutines.delay

@Composable
fun TelosSystemOptimizer(engine: LinuxSystemTelemetryEngine) {
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = engine.poll()
            delay(1500)
        }
    }
    val s = snap
    Column(Modifier.padding(16.dp)) {
        Text("System Optimizer", color = TelosColors.secondary, fontSize = 22.sp)
        if (s != null) {
            Text("CPU ${"%.0f".format(s.cpu.totalPercent)}%   RAM ${s.mem.usedKb / 1024} MiB", color = TelosColors.onSurface)
            LinearProgressIndicator(progress = { (s.cpu.totalPercent / 100.0).toFloat() }, modifier = Modifier.padding(vertical = 8.dp))
            Text("Top apps", color = TelosColors.primary)
            s.apps.take(8).forEach {
                Text("${it.name}  ${"%.1f".format(it.cpuPercent)}%", color = TelosColors.onSurface, fontSize = 12.sp)
            }
        }
        Button(onClick = {
            snap?.apps?.drop(3)?.forEach { g ->
                if (g.cpuPercent < 1.0) ProcessActions.termGroup(g.pids)
            }
        }) { Text("Trim idle background groups") }
        Button(onClick = { Sys.run("sync"); runCatching { PhantomVault.engine.compact() } }) { Text("Sync + fstrim vault") }
        Button(onClick = { Sys.run("pkexec", "fstrim", "-av", timeoutSec = 60) }) { Text("Trim all filesystems") }
    }
}
