package com.telos.desktop.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.linux.Sys
import com.telos.desktop.overlay.TelosPolkitAgent
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.vault.PhantomVaultEngine
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection


enum class MonitorPage { Overview, Applications, Processes, History, Services }

object TelosSystemMonitor {
    var page by mutableStateOf(MonitorPage.Overview)
    var pollMs by mutableStateOf(1000L)
    var filter by mutableStateOf("all")
    var tree by mutableStateOf(false)
    var search by mutableStateOf("")
    var confirmKill by mutableStateOf<ProcRow?>(null)
}

@Composable
fun TelosSystemMonitorWindow(engine: LinuxSystemTelemetryEngine, vault: PhantomVaultEngine) {
    var snap by remember { mutableStateOf<SystemSnapshot?>(null) }
    LaunchedEffect(TelosSystemMonitor.pollMs) {
        while (true) {
            snap = engine.poll()
            delay(TelosSystemMonitor.pollMs)
        }
    }
    val s = snap
    Row(Modifier.fillMaxSize().background(TelosColors.surface)) {
        Column(Modifier.width(180.dp).fillMaxHeight().background(TelosColors.surfaceContainer).padding(8.dp)) {
            Text("System Monitor", color = TelosColors.secondary, modifier = Modifier.padding(8.dp))
            MonitorPage.entries.forEach { p ->
                Text(
                    p.name,
                    color = if (TelosSystemMonitor.page == p) TelosColors.primary else TelosColors.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { TelosSystemMonitor.page = p }.padding(10.dp),
                )
            }
            Text("500ms", fontSize = 11.sp, modifier = Modifier.clickable { TelosSystemMonitor.pollMs = 500; engine.intervalMs = 500 }.padding(8.dp), color = TelosColors.onSurface)
            Text("1s", fontSize = 11.sp, modifier = Modifier.clickable { TelosSystemMonitor.pollMs = 1000; engine.intervalMs = 1000 }.padding(8.dp), color = TelosColors.onSurface)
            Text("2s", fontSize = 11.sp, modifier = Modifier.clickable { TelosSystemMonitor.pollMs = 2000; engine.intervalMs = 2000 }.padding(8.dp), color = TelosColors.onSurface)
        }
        if (s != null) {
            Box(Modifier.weight(1f).padding(12.dp)) {
                when (TelosSystemMonitor.page) {
                    MonitorPage.Overview -> OverviewPage(s, engine, vault)
                    MonitorPage.Applications -> AppsPage(s)
                    MonitorPage.Processes -> ProcessesPage(s)
                    MonitorPage.History -> HistoryPage(s, engine)
                    MonitorPage.Services -> ServicesPage()
                }
            }
        } else Text("Reading sensors…", color = TelosColors.onSurface)
    }
    TelosSystemMonitor.confirmKill?.let { proc ->
        Column(Modifier.padding(24.dp).background(TelosColors.surfaceContainer).padding(16.dp)) {
            Text("Kill ${proc.name} (${proc.pid})?", color = TelosColors.onSurface)
            Button(onClick = { ProcessActions.kill(proc.pid); TelosSystemMonitor.confirmKill = null }) { Text("SIGKILL") }
            Button(onClick = { TelosSystemMonitor.confirmKill = null }) { Text("Cancel") }
        }
    }
}

@Composable
private fun OverviewPage(s: SystemSnapshot, engine: LinuxSystemTelemetryEngine, vault: PhantomVaultEngine) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeCard("CPU", s.cpu.totalPercent / 100.0, "${"%.0f".format(s.cpu.totalPercent)}%\n${s.cpu.freqsMhz.firstOrNull()?.div(1000.0)?.let { "%.2f GHz".format(it) } ?: ""}\n${s.cpu.tempC?.let { "%.0f°C".format(it) } ?: ""}")
            val memFrac = if (s.mem.totalKb == 0L) 0.0 else s.mem.usedKb.toDouble() / s.mem.totalKb
            val swapFrac = if (s.mem.swapTotalKb == 0L) 0.0 else (s.mem.swapTotalKb - s.mem.swapFreeKb).toDouble() / s.mem.swapTotalKb
            GaugeCard("Memory", memFrac, "${"%.1f".format(s.mem.usedKb / 1048576.0)} / ${"%.1f".format(s.mem.totalKb / 1048576.0)} GiB\nZRAM ${s.mem.zramUsedKb / 1024} MiB", inner = swapFrac)
            val gpu = s.gpus.firstOrNull()
            GaugeCard("GPU", (gpu?.loadPercent ?: 0.0) / 100.0, gpu?.let { "${it.name}\n${it.loadPercent?.toInt() ?: 0}%  ${it.tempC?.toInt() ?: 0}°C\n${it.powerW?.let { w -> "%.0f W".format(w) } ?: ""}" } ?: "No GPU")
        }
        val hist = engine.overviewHistory
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplineChart("Network", hist.map { it.nets.sumOf { n -> n.rxKBps } }, hist.map { it.nets.sumOf { n -> n.txKBps } }, TelosColors.secondary, TelosColors.primary, Modifier.weight(1f))
            SplineChart("Disk I/O", hist.map { it.disks.sumOf { d -> d.readMBps } }, hist.map { it.disks.sumOf { d -> d.writeMBps } }, Color(0xFF50FA7B), Color(0xFFF1FA8C), Modifier.weight(1f))
        }
        s.mounts.forEach { m ->
            val frac = if (m.totalBytes == 0L) 0f else m.usedBytes.toFloat() / m.totalBytes
            Column(Modifier.fillMaxWidth().background(TelosColors.card).padding(8.dp)) {
                Text("${m.path} (${m.fstype})  ${m.usedBytes / 1_000_000_000} / ${m.totalBytes / 1_000_000_000} GB", color = TelosColors.onSurface, fontSize = 12.sp)
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp), color = TelosColors.primary)
                if (m.fstype == "luks") Button(onClick = { vault.lock(syncCloud = true) }) { Text("Lock / Sync Vault") }
            }
        }
        Text("Top processes", color = TelosColors.secondary)
        s.apps.take(5).forEach {
            Text("${it.name}  ${"%.1f".format(it.cpuPercent)}%  ${it.rssKb / 1024} MiB", color = TelosColors.onSurface, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GaugeCard(title: String, frac: Double, subtitle: String, inner: Double = 0.0) {
    Column(Modifier.width(220.dp).background(TelosColors.card).padding(8.dp)) {
        Canvas(Modifier.size(120.dp)) {
            val f = frac.toFloat().coerceIn(0f, 1f)
            val sweep = 270f * f
            drawArc(TelosColors.surfaceContainer, 135f, 270f, false, style = Stroke(14f), size = Size(size.minDimension, size.minDimension))
            drawArc(
                Brush.sweepGradient(listOf(TelosColors.primary, TelosColors.secondary)),
                135f, sweep, false, style = Stroke(14f), size = Size(size.minDimension, size.minDimension),
            )
            if (inner > 0) {
                drawArc(TelosColors.primary.copy(0.4f), 135f, 270f * inner.toFloat(), false, style = Stroke(6f), topLeft = Offset(18f, 18f), size = Size(size.minDimension - 36f, size.minDimension - 36f))
            }
        }
        Text(title, color = TelosColors.secondary)
        Text(subtitle, color = TelosColors.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun SplineChart(title: String, a: List<Double>, b: List<Double>, ca: Color, cb: Color, modifier: Modifier = Modifier.fillMaxWidth().height(140.dp)) {
    Column(modifier.background(TelosColors.card).padding(8.dp)) {
        Text(title, color = TelosColors.secondary, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            fun path(values: List<Double>, color: Color) {
                if (values.size < 2) return
                val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val p = Path()
                values.forEachIndexed { i, v ->
                    val x = size.width * i / (values.size - 1).coerceAtLeast(1)
                    val y = size.height - (v / max * size.height).toFloat()
                    if (i == 0) p.moveTo(x, y) else {
                        val px = size.width * (i - 1) / (values.size - 1)
                        val mid = (px + x) / 2
                        p.cubicTo(mid, y, mid, y, x, y)
                    }
                }
                drawPath(p, color, style = Stroke(2.2f))
                val fill = Path().apply {
                    addPath(p)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(fill, color.copy(alpha = 0.18f))
            }
            path(a, ca)
            path(b, cb)
        }
    }
}

@Composable
private fun AppsPage(s: SystemSnapshot) {
    LazyColumn {
        items(s.apps, key = { it.key }) { g ->
            Row(Modifier.fillMaxWidth().padding(8.dp).background(TelosColors.card).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${g.name}  (${g.children} procs)", color = TelosColors.onSurface)
                Text("${"%.1f".format(g.cpuPercent)}%  ${g.rssKb / 1024} MiB", color = TelosColors.secondary)
                Text("Close", color = TelosColors.primary, modifier = Modifier.clickable { ProcessActions.termGroup(g.pids) }.padding(4.dp))
                Text("Kill", color = TelosColors.primary, modifier = Modifier.clickable { ProcessActions.killGroup(g.pids) }.padding(4.dp))
            }
        }
    }
}

@Composable
private fun ProcessesPage(s: SystemSnapshot) {
    var sortCpu by remember { mutableStateOf(true) }
    val myUid = s.processes.maxByOrNull { it.rssKb }?.uid ?: 1000
    val filtered = s.processes.filter { p ->
        val q = TelosSystemMonitor.search
        val match = q.isBlank() || p.name.contains(q, true) || p.pid.toString() == q || p.cmdline.contains(q, true)
        val scope = when (TelosSystemMonitor.filter) {
            "mine" -> p.uid == myUid
            "user" -> p.uid >= 1000
            "system" -> p.uid < 1000
            else -> true
        }
        match && scope
    }.let { if (sortCpu) it.sortedByDescending { p -> p.cpuPercent } else it.sortedBy { p -> p.name } }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(TelosSystemMonitor.search, { TelosSystemMonitor.search = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
            Text("All", modifier = Modifier.clickable { TelosSystemMonitor.filter = "all" }.padding(8.dp), color = TelosColors.onSurface)
            Text("Mine", modifier = Modifier.clickable { TelosSystemMonitor.filter = "mine" }.padding(8.dp), color = TelosColors.onSurface)
            Text("User", modifier = Modifier.clickable { TelosSystemMonitor.filter = "user" }.padding(8.dp), color = TelosColors.onSurface)
            Text("System", modifier = Modifier.clickable { TelosSystemMonitor.filter = "system" }.padding(8.dp), color = TelosColors.onSurface)
            Text(if (TelosSystemMonitor.tree) "Tree" else "Flat", modifier = Modifier.clickable { TelosSystemMonitor.tree = !TelosSystemMonitor.tree }.padding(8.dp), color = TelosColors.secondary)
        }
        LazyColumn {
            items(filtered.take(200), key = { it.pid }) { p ->
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name.take(18), color = TelosColors.onSurface, modifier = Modifier.width(120.dp), fontSize = 12.sp)
                    Text("${p.pid}", color = TelosColors.onSurface, modifier = Modifier.width(56.dp), fontSize = 11.sp)
                    Text(p.user, color = TelosColors.onSurface, modifier = Modifier.width(72.dp), fontSize = 11.sp)
                    Text("${"%.1f".format(p.cpuPercent)}%", color = TelosColors.secondary, modifier = Modifier.width(56.dp), fontSize = 11.sp)
                    Text("${p.rssKb / 1024}M", color = TelosColors.onSurface, modifier = Modifier.width(56.dp), fontSize = 11.sp)
                    Text("oom ${p.oomScoreAdj}", fontSize = 11.sp, color = TelosColors.onSurface)
                    Text("TERM", fontSize = 11.sp, color = TelosColors.primary, modifier = Modifier.clickable { ProcessActions.term(p.pid) })
                    Text("KILL", fontSize = 11.sp, color = TelosColors.primary, modifier = Modifier.clickable { TelosSystemMonitor.confirmKill = p })
                    Text("STOP", fontSize = 11.sp, modifier = Modifier.clickable { ProcessActions.stop(p.pid) }, color = TelosColors.onSurface)
                    Text("CONT", fontSize = 11.sp, modifier = Modifier.clickable { ProcessActions.cont(p.pid) }, color = TelosColors.onSurface)
                    Text("nice+", fontSize = 11.sp, modifier = Modifier.clickable { ProcessActions.renice(p.pid, 10) }, color = TelosColors.onSurface)
                    Text("prot", fontSize = 11.sp, modifier = Modifier.clickable {
                        if (!ProcessActions.setOomScoreAdj(p.pid, -500)) TelosPolkitAgent.prompt("Set OOM -500 for ${p.pid}") {
                            ProcessActions.setOomScoreAdj(p.pid, -500)
                        }
                    }, color = TelosColors.onSurface)
                    Text("copy", fontSize = 11.sp, modifier = Modifier.clickable {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(p.cmdline), null)
                    }, color = TelosColors.onSurface)
                    Text("open", fontSize = 11.sp, modifier = Modifier.clickable { ProcessActions.openLocation(p.cmdline) }, color = TelosColors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(s: SystemSnapshot, engine: LinuxSystemTelemetryEngine) {
    val hist = engine.overviewHistory
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Per-core CPU", color = TelosColors.secondary)
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.height(320.dp)) {
            items(s.cpu.perCore.indices.toList()) { i ->
                val series = hist.map { it.cpu.perCore.getOrNull(i) ?: 0.0 }
                Column(Modifier.padding(4.dp).background(TelosColors.card).padding(6.dp)) {
                    Text("Core $i ${"%.0f".format(s.cpu.perCore.getOrNull(i) ?: 0.0)}%", fontSize = 11.sp, color = TelosColors.onSurface)
                    SplineChart("", series, emptyList(), TelosColors.secondary, Color.Transparent)
                }
            }
        }
        SplineChart("RAM GiB", hist.map { it.mem.usedKb / 1048576.0 }, hist.map { it.mem.zramUsedKb / 1048576.0 }, TelosColors.primary, TelosColors.secondary)
        SplineChart("Network", hist.map { it.nets.sumOf { n -> n.rxKBps } }, hist.map { it.nets.sumOf { n -> n.txKBps } }, TelosColors.secondary, TelosColors.primary)
        SplineChart("Disk", hist.map { it.disks.sumOf { d -> d.readMBps } }, hist.map { it.disks.sumOf { d -> d.writeMBps } }, Color(0xFF50FA7B), Color(0xFFF1FA8C))
    }
}

@Composable
private fun ServicesPage() {
    var units by remember { mutableStateOf<List<SystemdUnit>>(emptyList()) }
    var user by remember { mutableStateOf(true) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(user) { units = SystemdManager.list(user) }
    Column {
        Row {
            Text(if (user) "User units" else "System units", modifier = Modifier.clickable { user = !user }.padding(8.dp), color = TelosColors.secondary)
            OutlinedTextField(q, { q = it }, label = { Text("Filter") })
        }
        LazyColumn {
            items(units.filter { it.name.contains(q, true) || it.description.contains(q, true) }.take(120), key = { it.name + it.user }) { u ->
                Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(u.name.take(40), color = TelosColors.onSurface, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Text("${u.active}/${u.sub}", color = if (u.active == "active") TelosColors.secondary else TelosColors.primary, fontSize = 11.sp)
                    Text("start", modifier = Modifier.clickable { SystemdManager.action(u, "start") }, color = TelosColors.onSurface, fontSize = 11.sp)
                    Text("stop", modifier = Modifier.clickable { SystemdManager.action(u, "stop") }, color = TelosColors.onSurface, fontSize = 11.sp)
                    Text("restart", modifier = Modifier.clickable { SystemdManager.action(u, "restart") }, color = TelosColors.onSurface, fontSize = 11.sp)
                }
            }
        }
        Text("${Sys.run("uname", "-r").stdout.trim()}  ${sOs()}", color = TelosColors.onSurface.copy(0.6f), fontSize = 11.sp)
    }
}

private fun sOs(): String = java.io.File("/etc/os-release").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"') ?: ""
